package com.scorpk.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.scorpk.assistant.MainActivity
import com.scorpk.assistant.R
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.util.PermissionChecker
import com.scorpk.assistant.voice.MicCoordinator
import com.scorpk.assistant.voice.WakeWordMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Servicio en primer plano que detecta el wake-word con Vosk (reconocimiento on-device,
 * silencioso, sin enviar audio fuera del dispositivo). No usa SpeechRecognizer: al detectar
 * "Oye Scorpk" libera el micrófono y abre [AssistantOverlayService], que captura la orden.
 * Mientras otro componente usa el micrófono ([MicCoordinator]) o el overlay está visible,
 * el detector queda en pausa. Si Android lo detiene, se reinicia gracias a START_STICKY.
 */
class WakeWordForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var observerJob: Job? = null
    private var restartJob: Job? = null
    private var lastTriggerAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { applicationContext.appContainer.settings.setWakeWordEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!PermissionChecker.isGranted(this, RequiredPermission.MICROPHONE)) {
            Log.w(TAG, "Sin permiso de micrófono; el servicio de wake-word no se inicia")
            stopSelf()
            return START_NOT_STICKY
        }
        val modelManager = appContainer.wakeWordModelManager
        if (!modelManager.isReady) {
            Log.w(TAG, "Modelo de wake-word no descargado")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar en primer plano", e)
            stopSelf()
            return START_NOT_STICKY
        }
        _isRunning.value = true
        if (observerJob == null) {
            observerJob = scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    try {
                        LibVosk.setLogLevel(LogLevel.WARNINGS)
                        Model(modelManager.modelDir.absolutePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "No se pudo cargar el modelo de Vosk", e)
                        null
                    }
                }
                if (loaded == null) {
                    stopSelf()
                    return@launch
                }
                model = loaded
                combine(MicCoordinator.busy, AssistantOverlayService.isShowing) { micBusy, overlay ->
                    micBusy || overlay
                }.distinctUntilChanged().collect { paused ->
                    if (paused) stopDetector() else startDetector()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopDetector()
        scope.cancel()
        model?.close()
        model = null
        _isRunning.value = false
        super.onDestroy()
    }

    // region Detector

    private fun startDetector() {
        if (speechService != null) return
        val loadedModel = model ?: return
        restartJob?.cancel()
        try {
            val rec = Recognizer(loadedModel, SAMPLE_RATE)
            val service = SpeechService(rec, SAMPLE_RATE)
            recognizer = rec
            speechService = service
            service.startListening(listener)
            Log.d(TAG, "Detector de wake-word activo")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir el micrófono para el wake-word", e)
            stopDetector()
            scheduleRestart(ERROR_RESTART_DELAY_MS)
        }
    }

    /** Libera el AudioRecord de inmediato para que otro componente pueda usar el micrófono. */
    private fun stopDetector() {
        restartJob?.cancel()
        speechService?.run {
            stop()
            shutdown()
        }
        speechService = null
        recognizer?.close()
        recognizer = null
    }

    private fun scheduleRestart(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (!MicCoordinator.busy.value && !AssistantOverlayService.isShowing.value) startDetector()
        }
    }

    private fun onTranscript(text: String) {
        if (text.isBlank()) return
        val match = WakeWordMatcher.match(text) ?: return
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < TRIGGER_COOLDOWN_MS) return
        lastTriggerAt = now
        Log.i(TAG, "Wake-word detectado: \"${match.heard}\" ≈ ${match.alias} (${"%.2f".format(match.similarity)})")

        stopDetector()
        if (PermissionChecker.isGranted(this, RequiredPermission.OVERLAY)) {
            AssistantOverlayService.show(this)
            // Si el overlay no llega a mostrarse, se reanuda la escucha.
            scheduleRestart(OVERLAY_START_TIMEOUT_MS)
        } else {
            appContainer.speechOutput.speak("Necesito el permiso para mostrarme sobre otras apps.")
            scheduleRestart(ERROR_RESTART_DELAY_MS)
        }
    }

    private fun extract(hypothesis: String?, field: String): String = try {
        hypothesis?.let { json.parseToJsonElement(it).jsonObject[field]?.jsonPrimitive?.content }.orEmpty()
    } catch (e: Exception) {
        ""
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = onTranscript(extract(hypothesis, "partial"))

        override fun onResult(hypothesis: String?) = onTranscript(extract(hypothesis, "text"))

        override fun onFinalResult(hypothesis: String?) = onTranscript(extract(hypothesis, "text"))

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Error del detector de wake-word", exception)
            stopDetector()
            scheduleRestart(ERROR_RESTART_DELAY_MS)
        }

        override fun onTimeout() {
            stopDetector()
            scheduleRestart(ERROR_RESTART_DELAY_MS)
        }
    }

    // endregion

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.wake_word_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = getString(R.string.wake_word_channel_description) }
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.wake_word_notification_title))
            .setContentText(getString(R.string.wake_word_notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.wake_word_notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "ScorpkAssistant"
        private const val CHANNEL_ID = "scorpk_wake_word"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.scorpk.assistant.action.STOP_WAKE_WORD"
        private const val SAMPLE_RATE = 16000f
        private const val TRIGGER_COOLDOWN_MS = 2_000L
        private const val ERROR_RESTART_DELAY_MS = 3_000L
        private const val OVERLAY_START_TIMEOUT_MS = 2_500L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** @return false si Android no permitió iniciar el servicio (p. ej. app en segundo plano). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordForegroundService::class.java)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el servicio de wake-word", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordForegroundService::class.java))
        }
    }
}
