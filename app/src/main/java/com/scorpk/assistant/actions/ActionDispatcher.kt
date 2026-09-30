package com.scorpk.assistant.actions

import com.scorpk.assistant.connectors.AccountConnectors
import com.scorpk.assistant.connectors.CalendarManager
import com.scorpk.assistant.connectors.ConnectorManager
import com.scorpk.assistant.connectors.ConnectorType
import com.scorpk.assistant.connectors.DeviceConnectors
import com.scorpk.assistant.connectors.SpotifyCommand
import com.scorpk.assistant.connectors.SpotifyController
import com.scorpk.assistant.domain.model.ActionRequest
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.ActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enrutador que mapea cada [ActionRequest] del contrato JSON con su ejecución concreta.
 */
class ActionDispatcher(
    private val systemActions: SystemActions,
    private val automator: AccessibilityAutomator,
    private val spotify: SpotifyController,
    private val calendar: CalendarManager,
    private val device: DeviceConnectors,
    private val account: AccountConnectors,
    private val connectors: ConnectorManager
) {

    suspend fun dispatch(request: ActionRequest): ActionResult {
        val params = request.parameters
        return when (request.type) {
            ActionType.OPEN_APP -> onMain { systemActions.openApp(params.target) }

            ActionType.MEDIA_CONTROL -> {
                val command = MediaCommand.from(params.valueAsString ?: params.target)
                    ?: return ActionResult.Failure("Comando multimedia no reconocido.")
                onDefault { systemActions.mediaControl(command) }
            }

            ActionType.VOLUME_CONTROL -> onDefault {
                systemActions.adjustVolume(params.valueAsString ?: params.target)
            }

            ActionType.TOGGLE_FLASHLIGHT -> onDefault { systemActions.setFlashlight(params.valueAsBoolean) }

            ActionType.SET_ALARM -> onMain {
                systemActions.setAlarm(params.valueAsString ?: params.target, params.message)
            }

            ActionType.SET_TIMER -> onMain { systemActions.setTimer(params.valueAsInt, params.message) }

            ActionType.BATTERY_STATUS -> onDefault { systemActions.batteryStatus() }

            ActionType.GLOBAL_ACTION -> {
                val command = GlobalCommand.from(params.target ?: params.valueAsString)
                    ?: return ActionResult.Failure("Acción global no reconocida.")
                onDefault { automator.performGlobal(command) }
            }

            ActionType.READ_SCREEN -> onDefault { automator.readScreen() }

            ActionType.CLICK_NODE -> onDefault { automator.clickNode(params.target ?: params.valueAsString) }

            ActionType.GESTURE -> dispatchGesture(request)

            ActionType.SEND_MESSAGE -> withContext(Dispatchers.Default) {
                automator.sendMessage(params.message ?: params.valueAsString)
            }

            ActionType.SPOTIFY_CONTROL -> gated(ConnectorType.SPOTIFY) {
                val query = params.target?.takeIf { it.isNotBlank() }
                val command = SpotifyCommand.from(params.valueAsString)
                    ?: if (query != null) SpotifyCommand.PLAY else SpotifyCommand.TOGGLE
                onMain { spotify.execute(command, query) }
            }

            ActionType.CALENDAR_QUERY -> gated(ConnectorType.GOOGLE_CALENDAR) {
                withContext(Dispatchers.IO) { calendar.describeAgenda(params.valueAsString ?: params.target) }
            }

            ActionType.CALENDAR_CREATE -> gated(ConnectorType.GOOGLE_CALENDAR) {
                withContext(Dispatchers.IO) { calendar.createEvent(params.target, params.valueAsString, params.message) }
            }

            ActionType.DRIVE_SEARCH -> gated(ConnectorType.GOOGLE_DRIVE) {
                account.driveSearch(params.target ?: params.valueAsString)
            }

            ActionType.GMAIL_INBOX -> gated(ConnectorType.GMAIL) {
                account.gmailInbox(params.target?.takeIf { it.isNotBlank() })
            }

            ActionType.GITHUB_QUERY -> gated(ConnectorType.GITHUB) {
                account.github(params.valueAsString ?: params.target)
            }

            ActionType.CALL_CONTACT -> gated(ConnectorType.CONTACTS) {
                onMain { device.call(params.target ?: params.valueAsString) }
            }

            ActionType.WHATSAPP_MESSAGE -> gated(ConnectorType.WHATSAPP) {
                onMain { device.whatsapp(params.target, params.message ?: params.valueAsString) }
            }

            ActionType.NAVIGATE -> gated(ConnectorType.MAPS) {
                onMain { device.navigate(params.target ?: params.valueAsString) }
            }

            ActionType.YOUTUBE_SEARCH -> gated(ConnectorType.YOUTUBE) {
                onMain { device.youtube(params.target ?: params.valueAsString) }
            }

            ActionType.COMPOSE_EMAIL -> gated(ConnectorType.EMAIL) {
                onMain { device.composeEmail(params.target, params.valueAsString, params.message) }
            }

            ActionType.RESPOND_CHAT -> ActionResult.Success(
                params.message ?: request.feedbackSpeech.ifBlank { "Entendido." }
            )

            ActionType.UNKNOWN -> ActionResult.Failure("No sé cómo ejecutar \"${request.action}\".")
        }
    }

    /** Ejecuta [block] solo si el conector está conectado y listo; si no, explica qué falta. */
    private suspend fun gated(type: ConnectorType, block: suspend () -> ActionResult): ActionResult =
        connectors.gate(type) ?: block()

    private suspend fun dispatchGesture(request: ActionRequest): ActionResult {
        val target = request.parameters.target?.lowercase()
        val value = request.parameters.valueAsString
        return withContext(Dispatchers.Default) {
            when {
                target == "tap" && value != null -> {
                    val coords = value.split(',').mapNotNull { it.trim().toFloatOrNull() }
                    if (coords.size == 2) {
                        automator.tapAt(coords[0], coords[1])
                    } else {
                        ActionResult.Failure("Coordenadas inválidas: $value")
                    }
                }
                target?.startsWith("swipe_") == true -> automator.swipe(target.removePrefix("swipe_"))
                target == "swipe" && value != null -> automator.swipe(value)
                else -> ActionResult.Failure("Gesto no reconocido.")
            }
        }
    }

    private suspend inline fun onMain(crossinline block: suspend () -> ActionResult): ActionResult =
        withContext(Dispatchers.Main.immediate) { block() }

    private suspend inline fun onDefault(crossinline block: suspend () -> ActionResult): ActionResult =
        withContext(Dispatchers.Default) { block() }
}
