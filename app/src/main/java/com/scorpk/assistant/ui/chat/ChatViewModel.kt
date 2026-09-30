package com.scorpk.assistant.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.data.local.ConversationEntity
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.AiModel
import com.scorpk.assistant.domain.model.Attachment
import com.scorpk.assistant.domain.model.ChatMessage
import com.scorpk.assistant.domain.model.CommandSource
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.voice.VoiceCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatEvent {
    data class PermissionNeeded(val permission: RequiredPermission, val message: String) : ChatEvent
    data class Message(val text: String) : ChatEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repository = container.chatRepository
    private val processor = container.commandProcessor

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Modelo del chat elegido en el selector de la barra superior (persistido en DataStore). */
    val chatModel: StateFlow<AiModel> = container.settings.settings
        .map { it.chatModel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiModel.CHAT_DEFAULT)

    private val _attachment = MutableStateFlow<Attachment?>(null)
    val attachment: StateFlow<Attachment?> = _attachment.asStateFlow()

    private val _loadingAttachment = MutableStateFlow(false)
    val loadingAttachment: StateFlow<Boolean> = _loadingAttachment.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = _currentConversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Captura de voz bajo demanda (solo al pulsar el micrófono, nunca en bucle). */
    private val voice = VoiceCapture(application)
    val voiceState: StateFlow<VoiceCapture.State> = voice.state

    init {
        viewModelScope.launch {
            voice.state.collect { state ->
                when (state) {
                    is VoiceCapture.State.Final -> {
                        voice.reset()
                        send(state.text, CommandSource.VOICE)
                    }
                    is VoiceCapture.State.Error -> {
                        voice.reset()
                        _events.emit(ChatEvent.Message(state.message))
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Micrófono: inicia la escucha o, si ya está escuchando, la termina y envía lo capturado. */
    fun toggleVoice() {
        if (voice.isListening) {
            voice.finish()
        } else if (!_isProcessing.value) {
            container.speechOutput.stop()
            voice.start()
        }
    }

    fun cancelVoice() {
        voice.cancel()
    }

    override fun onCleared() {
        voice.cancel()
        super.onCleared()
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun submitInput() {
        val text = _input.value
        if (send(text, CommandSource.TEXT)) _input.value = ""
    }

    fun selectChatModel(model: AiModel) {
        viewModelScope.launch { container.settings.setChatModel(model) }
    }

    fun attach(uri: Uri) {
        _loadingAttachment.value = true
        viewModelScope.launch {
            try {
                _attachment.value = container.attachmentReader.read(uri)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo leer el adjunto", e)
                _events.emit(ChatEvent.Message("No se pudo leer el archivo seleccionado."))
            } finally {
                _loadingAttachment.value = false
            }
        }
    }

    fun clearAttachment() {
        _attachment.value = null
    }

    /** Envía una orden del chat; si hay un adjunto pendiente, viaja con ella y se limpia. */
    fun send(text: String, source: CommandSource): Boolean {
        val attachment = _attachment.value
        if ((text.isBlank() && attachment == null) || _isProcessing.value) return false
        _isProcessing.value = true
        _attachment.value = null
        viewModelScope.launch {
            try {
                val outcome = processor.process(_currentConversationId.value, text, source, attachment) { id ->
                    _currentConversationId.value = id
                }
                val result = outcome.result
                if (result is ActionResult.PermissionRequired) {
                    _events.emit(ChatEvent.PermissionNeeded(result.permission, result.message))
                }
            } finally {
                _isProcessing.value = false
            }
        }
        return true
    }

    fun newConversation() {
        _currentConversationId.value = null
        _input.value = ""
        _attachment.value = null
        container.speechOutput.stop()
    }

    fun selectConversation(id: Long) {
        _currentConversationId.value = id
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) _currentConversationId.value = null
        }
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
