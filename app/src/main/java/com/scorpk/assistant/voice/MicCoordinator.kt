package com.scorpk.assistant.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Árbitro del micrófono: mientras alguien captura voz con SpeechRecognizer (overlay o chat),
 * el detector de wake-word libera el AudioRecord para no competir por el audio.
 */
object MicCoordinator {

    private val holders = MutableStateFlow(0)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun acquire() {
        holders.update { it + 1 }
        _busy.value = holders.value > 0
    }

    fun release() {
        holders.update { (it - 1).coerceAtLeast(0) }
        _busy.value = holders.value > 0
    }
}
