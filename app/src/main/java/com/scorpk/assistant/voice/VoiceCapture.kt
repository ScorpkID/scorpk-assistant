package com.scorpk.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.MainThread
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.util.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Captura única de una orden con SpeechRecognizer (una sola sesión, sin bucle).
 * Expone la transcripción parcial en tiempo real y el nivel de audio para la onda visual.
 * Todos los métodos deben invocarse desde el hilo principal.
 */
class VoiceCapture(context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String, val level: Float) : State
        data class Final(val text: String) : State
        data class Error(val code: Int, val message: String) : State
    }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var holdingMic = false

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isListening: Boolean get() = _state.value is State.Listening

    @MainThread
    fun start(language: String = DEFAULT_LANGUAGE): Boolean {
        if (isListening) return true
        if (!PermissionChecker.isGranted(appContext, RequiredPermission.MICROPHONE)) {
            _state.value = State.Error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, "Necesito permiso de micrófono.")
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = State.Error(SpeechRecognizer.ERROR_CLIENT, "El reconocimiento de voz no está disponible.")
            return false
        }
        release()
        acquireMic()
        val sr = SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        sr.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        _state.value = State.Listening(partial = "", level = 0f)
        return try {
            sr.startListening(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar SpeechRecognizer", e)
            release()
            _state.value = State.Error(SpeechRecognizer.ERROR_CLIENT, "No se pudo iniciar el micrófono.")
            false
        }
    }

    /** Termina la escucha y fuerza el resultado final con lo capturado hasta ahora. */
    @MainThread
    fun finish() {
        recognizer?.stopListening()
    }

    /** Aborta la escucha sin producir resultado. */
    @MainThread
    fun cancel() {
        release()
        _state.value = State.Idle
    }

    /** Vuelve a Idle tras consumir un estado terminal (Final/Error). */
    fun reset() {
        if (_state.value is State.Final || _state.value is State.Error) _state.value = State.Idle
    }

    private fun release() {
        recognizer?.run {
            setRecognitionListener(null)
            cancel()
            destroy()
        }
        recognizer = null
        if (holdingMic) {
            holdingMic = false
            MicCoordinator.release()
        }
    }

    private fun acquireMic() {
        if (!holdingMic) {
            holdingMic = true
            MicCoordinator.acquire()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value as? State.Listening ?: return
            val level = ((rmsdB - RMS_MIN) / (RMS_MAX - RMS_MIN)).coerceIn(0f, 1f)
            _state.value = current.copy(level = level)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val current = _state.value as? State.Listening ?: return
            val partial = partialResults.firstResult() ?: return
            if (partial.isNotBlank()) _state.value = current.copy(partial = partial)
        }

        override fun onResults(results: Bundle?) {
            val text = results.firstResult()
                ?: (_state.value as? State.Listening)?.partial
            release()
            _state.value = if (text.isNullOrBlank()) {
                State.Error(SpeechRecognizer.ERROR_NO_MATCH, "No te escuché bien.")
            } else {
                State.Final(text.trim())
            }
        }

        override fun onError(error: Int) {
            val partial = (_state.value as? State.Listening)?.partial
            release()
            if (!partial.isNullOrBlank() && error in RECOVERABLE_WITH_PARTIAL) {
                _state.value = State.Final(partial.trim())
                return
            }
            _state.value = State.Error(error, messageFor(error))
        }
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché bien."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Necesito permiso de micrófono."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para reconocer la voz."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado."
        SpeechRecognizer.ERROR_AUDIO -> "Error de audio del micrófono."
        else -> "Error de reconocimiento de voz ($error)."
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val DEFAULT_LANGUAGE = "es-ES"
        const val RMS_MIN = -2f
        const val RMS_MAX = 10f
        val RECOVERABLE_WITH_PARTIAL = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        )
    }
}
