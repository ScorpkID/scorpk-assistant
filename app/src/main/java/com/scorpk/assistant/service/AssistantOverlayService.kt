package com.scorpk.assistant.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.data.local.AssistantSettings
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.ActionType
import com.scorpk.assistant.domain.model.CommandSource
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.ui.overlay.AssistantOverlayCard
import com.scorpk.assistant.ui.overlay.EdgeGlow
import com.scorpk.assistant.ui.overlay.OverlayActions
import com.scorpk.assistant.ui.overlay.OverlayImage
import com.scorpk.assistant.ui.overlay.OverlayPhase
import com.scorpk.assistant.ui.overlay.OverlayState
import com.scorpk.assistant.ui.theme.ScorpkTheme
import com.scorpk.assistant.util.ImageEncoder
import com.scorpk.assistant.util.PermissionChecker
import com.scorpk.assistant.voice.VoiceCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Contenedor de la ventana que convierte la tecla Atrás en un cierre del asistente. */
private class OverlayHost(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Asistente flotante estilo Gemini sobre cualquier app, con dos ventanas:
 * - una luz de borde a pantalla completa que no recibe toques;
 * - una tarjeta inferior con voz, texto, y accesos a pantalla y cámara.
 * Procesa con CommandProcessor (Fireworks → JSON → ActionDispatcher) y se cierra sola.
 */
class AssistantOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var voice: VoiceCapture

    private var glowView: View? = null
    private var cardView: View? = null
    private var cardParams: WindowManager.LayoutParams? = null

    private var captureJob: Job? = null
    private var processJob: Job? = null
    private var dismissJob: Job? = null
    private var conversationId: Long? = null
    private var nextImageId = 0L
    private var windowFocusable = false

    private val state = MutableStateFlow(OverlayState())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WindowManager::class.java)
        voice = VoiceCapture(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> dismiss()
            else -> scope.launch {
                if (appContainer.updateManager.isUpdateRequired()) {
                    blockForRequiredUpdate()
                } else {
                    showOverlay()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        processJob?.cancel()
        dismissJob?.cancel()
        voice.cancel()
        removeViews()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        super.onDestroy()
    }

    /** Hay una actualización obligatoria pendiente: se avisa y se abre la app, donde está el diálogo. */
    private fun blockForRequiredUpdate() {
        appContainer.speechOutput.speak("Actualiza Scorpk para seguir usando el asistente.")
        try {
            startActivity(
                Intent(this, com.scorpk.assistant.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir la app para actualizar", e)
        }
        stopSelf()
    }

    // region Ventanas

    @Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE sigue siendo necesario por debajo de Android 11.
    private fun showOverlay() {
        if (cardView != null) return
        if (!PermissionChecker.isGranted(this, RequiredPermission.OVERLAY)) {
            Log.w(TAG, "Sin permiso de superposición; no se muestra el overlay")
            stopSelf()
            return
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val glow = OverlayHost(this, onBack = ::dismiss).apply {
            bindOwners()
            addView(newComposeView {
                ScorpkTheme {
                    val s by state.collectAsStateWithLifecycle()
                    val alpha by animateFloatAsState(if (s.visible) 1f else 0f, tween(450), label = "glowAlpha")
                    EdgeGlow(
                        level = s.level,
                        active = s.phase == OverlayPhase.LISTENING || s.phase == OverlayPhase.PROCESSING,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            })
        }
        val glowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val actions = OverlayActions(
            onInputChange = ::onInputChange,
            onSend = ::sendTyped,
            onMic = ::onMic,
            onCamera = ::onCamera,
            onScreen = ::onScreen,
            onRemoveImage = { id -> state.update { s -> s.copy(images = s.images.filterNot { it.id == id }) } },
            onClose = ::dismiss,
            onInputFocused = ::onInputFocused
        )
        val card = OverlayHost(this, onBack = ::dismiss).apply {
            bindOwners()
            addView(newComposeView {
                val settings by appContainer.settings.settings.collectAsStateWithLifecycle(AssistantSettings())
                ScorpkTheme(themeMode = settings.themeMode) {
                    val s by state.collectAsStateWithLifecycle()
                    AssistantOverlayCard(state = s, actions = actions)
                }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager.addView(glow, glowParams)
            windowManager.addView(card, params)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo añadir el overlay", e)
            runCatching { windowManager.removeView(glow) }
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            stopSelf()
            return
        }
        glowView = glow
        cardView = card
        cardParams = params
        windowFocusable = false
        _isShowing.value = true
        appContainer.speechOutput.stop()

        state.value = OverlayState(visible = true, phase = OverlayPhase.LISTENING)
        scope.launch {
            val prefs = appContainer.settings.settings.first()
            state.update { it.copy(screenEnabled = prefs.screenVisionEnabled, cameraEnabled = prefs.cameraVisionEnabled) }
        }
        startListening()
    }

    /** Los dueños del árbol de vistas se fijan en la raíz de la ventana; Compose los busca hacia arriba. */
    private fun View.bindOwners() {
        setViewTreeLifecycleOwner(this@AssistantOverlayService)
        setViewTreeSavedStateRegistryOwner(this@AssistantOverlayService)
        setViewTreeViewModelStoreOwner(this@AssistantOverlayService)
    }

    private fun newComposeView(content: @Composable () -> Unit): ComposeView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent(content)
    }

    /** Para escribir hace falta que la ventana sea enfocable (si no, no aparece el teclado). */
    private fun onInputFocused() {
        cancelAutoDismiss()
        if (state.value.phase == OverlayPhase.LISTENING) {
            captureJob?.cancel()
            voice.cancel()
            state.update { it.copy(phase = OverlayPhase.IDLE, level = 0f, input = "") }
        }
        val view = cardView ?: return
        val params = cardParams ?: return
        if (windowFocusable) return
        windowFocusable = true
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            windowManager.updateViewLayout(view, params)
            state.update { it.copy(focusTick = it.focusTick + 1) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo hacer enfocable el overlay", e)
        }
    }

    private fun setViewsVisible(visible: Boolean) {
        val mode = if (visible) View.VISIBLE else View.INVISIBLE
        glowView?.visibility = mode
        cardView?.visibility = mode
    }

    private fun removeViews() {
        listOf(glowView, cardView).forEach { view ->
            if (view != null) {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.w(TAG, "El overlay ya no estaba adjunto", e)
                }
            }
        }
        glowView = null
        cardView = null
        cardParams = null
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        _isShowing.value = false
    }

    // endregion

    // region Voz y texto

    private fun startListening() {
        cancelAutoDismiss()
        state.update { it.copy(phase = OverlayPhase.LISTENING, input = "", notice = null, level = 0f) }
        captureJob?.cancel()
        captureJob = scope.launch {
            voice.state.collect { voiceState ->
                when (voiceState) {
                    is VoiceCapture.State.Listening ->
                        state.update { it.copy(input = voiceState.partial, level = voiceState.level) }
                    is VoiceCapture.State.Final -> {
                        voice.reset()
                        process(voiceState.text)
                    }
                    is VoiceCapture.State.Error -> {
                        voice.reset()
                        state.update {
                            it.copy(phase = OverlayPhase.IDLE, level = 0f, input = "", notice = voiceState.message)
                        }
                        scheduleAutoDismiss(IDLE_TIMEOUT_MS)
                    }
                    VoiceCapture.State.Idle -> Unit
                }
            }
        }
        voice.start()
    }

    private fun onMic() {
        cancelAutoDismiss()
        if (state.value.phase == OverlayPhase.LISTENING) {
            val text = state.value.input
            if (text.isBlank()) voice.finish() else {
                voice.cancel()
                process(text)
            }
        } else {
            startListening()
        }
    }

    private fun onInputChange(value: String) {
        cancelAutoDismiss()
        state.update { it.copy(input = value, notice = null) }
    }

    private fun sendTyped() {
        val current = state.value
        if (current.phase == OverlayPhase.PROCESSING) return
        if (current.input.isBlank() && current.images.isEmpty()) return
        voice.cancel()
        captureJob?.cancel()
        process(current.input.ifBlank { "¿Qué ves en esta imagen?" })
    }

    // endregion

    // region Pantalla y cámara

    /** Captura la pantalla que hay debajo (oculta un instante el asistente para no salir en la foto). */
    private fun onScreen() {
        cancelAutoDismiss()
        val service = ScorpkAccessService.instance.value
        if (service == null) {
            state.update { it.copy(notice = "Activa el servicio de accesibilidad de Scorpk para ver la pantalla.") }
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            state.update { it.copy(notice = "Ver la pantalla requiere Android 11 o superior.") }
            return
        }
        if (state.value.capturing) return
        voice.cancel()
        state.update { it.copy(capturing = true, notice = null) }
        scope.launch {
            setViewsVisible(false)
            delay(CAPTURE_HIDE_DELAY_MS)
            val bitmap = service.captureScreen()
            setViewsVisible(true)
            if (bitmap == null) {
                state.update {
                    it.copy(
                        capturing = false,
                        notice = "No pude capturar esta pantalla (las apps protegidas, como las bancarias, no lo permiten)."
                    )
                }
                return@launch
            }
            addImage(bitmap, "Pantalla")
            state.update { it.copy(capturing = false) }
        }
    }

    private fun onCamera() {
        cancelAutoDismiss()
        voice.cancel()
        state.update { it.copy(notice = null) }
        // El resultado llega por el bus; se suscribe antes de abrir la cámara para no perderlo.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val uri = CameraCaptureBus.results.first()
            setViewsVisible(true)
            if (uri == null) {
                state.update { it.copy(notice = "No se tomó la foto.") }
                return@launch
            }
            val attachment = withContext(Dispatchers.IO) {
                runCatching { appContainer.attachmentReader.read(uri) }.getOrNull()
            }
            val dataUrl = attachment?.imageDataUrl
            if (attachment == null || dataUrl == null) {
                state.update { it.copy(notice = "No pude leer la foto.") }
            } else {
                state.update {
                    it.copy(images = it.images + OverlayImage(nextImageId++, "Foto", attachment.thumbnail, dataUrl))
                }
            }
        }
        setViewsVisible(false)
        CameraCaptureActivity.start(this)
    }

    private suspend fun addImage(bitmap: Bitmap, label: String) {
        val (thumbnail, dataUrl) = withContext(Dispatchers.Default) {
            val ratio = THUMBNAIL_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            small.asImageBitmap() to ImageEncoder.fromBitmap(bitmap)
        }
        state.update { it.copy(images = it.images + OverlayImage(nextImageId++, label, thumbnail, dataUrl)) }
    }

    // endregion

    // region Procesamiento

    private fun process(text: String) {
        if (processJob?.isActive == true) return
        captureJob?.cancel()
        cancelAutoDismiss()
        val images = state.value.images
        state.update {
            it.copy(phase = OverlayPhase.PROCESSING, submitted = text, input = "", notice = null, level = 0f)
        }
        processJob = scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                appContainer.commandProcessor.process(
                    conversationId = conversationId,
                    input = text,
                    source = CommandSource.WAKE_WORD,
                    images = images.map { it.dataUrl }
                )
            }
            conversationId = outcome.conversationId
            val result = outcome.result
            val message = outcome.displayText
            state.update {
                it.copy(
                    phase = OverlayPhase.RESULT,
                    response = message,
                    success = result is ActionResult.Success,
                    images = emptyList()
                )
            }
            val longRead = outcome.request?.type == ActionType.READ_SCREEN ||
                outcome.request?.type == ActionType.RESPOND_CHAT ||
                outcome.request?.type == ActionType.CALENDAR_QUERY
            val timeout = when {
                result !is ActionResult.Success -> ERROR_RESULT_MS
                longRead -> (message.length * READ_MS_PER_CHAR).coerceIn(LONG_RESULT_MS, MAX_RESULT_MS)
                else -> SHORT_RESULT_MS
            }
            scheduleAutoDismiss(timeout)
        }
    }

    // endregion

    // region Cierre

    private fun scheduleAutoDismiss(delayMs: Long) {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(delayMs)
            dismiss()
        }
    }

    private fun cancelAutoDismiss() {
        dismissJob?.cancel()
        dismissJob = null
    }

    private fun dismiss() {
        if (state.value.visible.not() && cardView == null) {
            stopSelf()
            return
        }
        captureJob?.cancel()
        dismissJob?.cancel()
        voice.cancel()
        state.update { it.copy(visible = false, level = 0f) }
        // Deja terminar las animaciones de salida antes de quitar las ventanas.
        scope.launch {
            delay(EXIT_ANIMATION_MS)
            removeViews()
            stopSelf()
        }
    }

    // endregion

    companion object {
        private const val TAG = "ScorpkAssistant"
        private const val ACTION_SHOW = "com.scorpk.assistant.action.SHOW_OVERLAY"
        private const val ACTION_DISMISS = "com.scorpk.assistant.action.DISMISS_OVERLAY"
        private const val SHORT_RESULT_MS = 2_200L
        private const val LONG_RESULT_MS = 6_000L
        private const val MAX_RESULT_MS = 20_000L
        private const val ERROR_RESULT_MS = 6_000L
        private const val IDLE_TIMEOUT_MS = 12_000L
        private const val READ_MS_PER_CHAR = 55L
        private const val CAPTURE_HIDE_DELAY_MS = 220L
        private const val EXIT_ANIMATION_MS = 420L
        private const val THUMBNAIL_PX = 200

        private val _isShowing = MutableStateFlow(false)
        val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

        fun show(context: Context) {
            try {
                context.startService(Intent(context, AssistantOverlayService::class.java).setAction(ACTION_SHOW))
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar el overlay", e)
            }
        }
    }
}
