package com.scorpk.assistant.ui.connectors

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.connectors.ConnectResult
import com.scorpk.assistant.connectors.ConnectorStatus
import com.scorpk.assistant.connectors.ConnectorType
import com.scorpk.assistant.util.PermissionChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectorEvent {
    data class RequestPermissions(val type: ConnectorType, val permissions: Array<String>) : ConnectorEvent
    data class OpenInstall(val type: ConnectorType) : ConnectorEvent
    data class Message(val text: String) : ConnectorEvent
}

class ConnectorsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = application.appContainer.connectorManager

    val statuses: StateFlow<List<ConnectorStatus>> = manager.statuses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _working = MutableStateFlow<ConnectorType?>(null)
    val working: StateFlow<ConnectorType?> = _working.asStateFlow()

    private val _events = MutableSharedFlow<ConnectorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ConnectorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            manager.messages.collect { _events.emit(ConnectorEvent.Message(it)) }
        }
        viewModelScope.launch {
            application.appContainer.authRepository.callbackMessages.collect {
                _events.emit(ConnectorEvent.Message(it))
            }
        }
    }

    fun refresh() = manager.refresh()

    fun connect(type: ConnectorType) = run(type) {
        when (val result = manager.connect(type)) {
            is ConnectResult.Connected ->
                _events.emit(ConnectorEvent.Message(result.warning ?: "${type.label} conectado"))
            ConnectResult.NeedsInstall -> _events.emit(ConnectorEvent.OpenInstall(type))
            is ConnectResult.NeedsPermissions -> {
                val runtime = result.permissions.flatMap { PermissionChecker.runtimePermissionsFor(it)?.toList().orEmpty() }
                _events.emit(ConnectorEvent.RequestPermissions(type, runtime.toTypedArray()))
            }
            ConnectResult.OAuthStarted ->
                _events.emit(ConnectorEvent.Message("Abriendo la autorización de ${type.account?.label ?: type.label}…"))
            is ConnectResult.Failed -> _events.emit(ConnectorEvent.Message(result.message))
        }
    }

    fun disconnect(type: ConnectorType) = run(type) {
        _events.emit(ConnectorEvent.Message(manager.disconnect(type) ?: "${type.label} desconectado"))
    }

    fun installIntent(type: ConnectorType) = manager.installIntent(type)

    fun launchIntent(type: ConnectorType) = manager.launchIntent(type)

    private fun run(type: ConnectorType, block: suspend () -> Unit) {
        viewModelScope.launch {
            _working.value = type
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: VirtualMachineError) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Fallo en el conector ${type.provider}", e)
                _events.emit(ConnectorEvent.Message("No se pudo completar la acción con ${type.label}."))
            } finally {
                _working.value = null
                manager.refresh()
            }
        }
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
    }
}
