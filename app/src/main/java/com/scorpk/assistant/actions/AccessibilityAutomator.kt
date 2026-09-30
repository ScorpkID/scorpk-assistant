package com.scorpk.assistant.actions

import android.accessibilityservice.AccessibilityService
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.service.ScorpkAccessService
import kotlinx.coroutines.delay

enum class GlobalCommand(val actionId: Int, val label: String) {
    BACK(AccessibilityService.GLOBAL_ACTION_BACK, "Atrás"),
    HOME(AccessibilityService.GLOBAL_ACTION_HOME, "Pantalla de inicio"),
    NOTIFICATIONS(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notificaciones"),
    RECENTS(AccessibilityService.GLOBAL_ACTION_RECENTS, "Apps recientes"),
    QUICK_SETTINGS(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Ajustes rápidos"),
    LOCK_SCREEN(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Pantalla bloqueada"),
    SCREENSHOT(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Captura de pantalla");

    companion object {
        fun from(value: String?): GlobalCommand? = when (value?.trim()?.lowercase()) {
            "back", "atras" -> BACK
            "home", "inicio" -> HOME
            "notifications", "notificaciones" -> NOTIFICATIONS
            "recents", "recientes" -> RECENTS
            "quick_settings", "ajustes_rapidos" -> QUICK_SETTINGS
            "lock", "lock_screen", "bloquear" -> LOCK_SCREEN
            "screenshot", "captura" -> SCREENSHOT
            else -> null
        }
    }
}

/**
 * Automatización de alto nivel sobre [ScorpkAccessService]: navegación global, lectura
 * de pantalla, clicks por texto y envío de mensajes en apps externas.
 */
class AccessibilityAutomator {

    private val service: ScorpkAccessService?
        get() = ScorpkAccessService.instance.value

    fun performGlobal(command: GlobalCommand): ActionResult = withService { svc ->
        if (svc.performGlobal(command.actionId)) {
            ActionResult.Success(command.label)
        } else {
            ActionResult.Failure("El sistema rechazó la acción \"${command.label}\".")
        }
    }

    fun readScreen(): ActionResult = withService { svc ->
        val texts = svc.readScreenText()
        if (texts.isEmpty()) {
            ActionResult.Success("No encontré texto legible en la pantalla.")
        } else {
            val app = svc.foregroundPackage.value?.let { " ($it)" }.orEmpty()
            ActionResult.Success(
                "En pantalla$app:\n" + texts.take(MAX_SCREEN_ITEMS).joinToString("\n") { "• $it" }
            )
        }
    }

    fun clickNode(target: String?): ActionResult {
        if (target.isNullOrBlank()) return ActionResult.Failure("No indicaste qué elemento pulsar.")
        return withService { svc ->
            val node = svc.findNode(target)
                ?: return@withService ActionResult.Failure("No encontré \"$target\" en la pantalla.")
            if (svc.clickNode(node)) {
                ActionResult.Success("Pulsé \"$target\"")
            } else {
                ActionResult.Failure("\"$target\" no se puede pulsar.")
            }
        }
    }

    suspend fun tapAt(x: Float, y: Float): ActionResult {
        val svc = service ?: return accessibilityRequired()
        return if (svc.tap(x, y)) ActionResult.Success("Toque ejecutado") else ActionResult.Failure("El gesto fue cancelado.")
    }

    suspend fun swipe(direction: String): ActionResult {
        val svc = service ?: return accessibilityRequired()
        val metrics = svc.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val ok = when (direction.lowercase()) {
            "up", "arriba" -> svc.swipe(w / 2, h * 0.75f, w / 2, h * 0.25f)
            "down", "abajo" -> svc.swipe(w / 2, h * 0.25f, w / 2, h * 0.75f)
            "left", "izquierda" -> svc.swipe(w * 0.85f, h / 2, w * 0.15f, h / 2)
            "right", "derecha" -> svc.swipe(w * 0.15f, h / 2, w * 0.85f, h / 2)
            else -> return ActionResult.Failure("Dirección de deslizamiento desconocida: $direction")
        }
        return if (ok) ActionResult.Success("Deslizamiento ejecutado") else ActionResult.Failure("El gesto fue cancelado.")
    }

    /**
     * Escribe [message] en el primer campo editable de la app activa y pulsa el botón de envío.
     * Pensado para apps de mensajería con un chat ya abierto (WhatsApp, Telegram, SMS).
     */
    suspend fun sendMessage(message: String?): ActionResult {
        if (message.isNullOrBlank()) return ActionResult.Failure("No indicaste el mensaje a enviar.")
        val svc = service ?: return accessibilityRequired()
        val input = svc.findFirstEditableNode()
            ?: return ActionResult.Failure("No encontré un campo de texto en la pantalla actual.")
        if (!svc.setText(input, message)) return ActionResult.Failure("No pude escribir en el campo de texto.")

        delay(SEND_BUTTON_DELAY_MS)
        val sendButton = SEND_BUTTON_QUERIES.firstNotNullOfOrNull { svc.findNode(it) }
        return if (sendButton != null && svc.clickNode(sendButton)) {
            ActionResult.Success("Mensaje enviado: \"$message\"")
        } else {
            ActionResult.Success("Escribí el mensaje, pero no encontré el botón de enviar.")
        }
    }

    private inline fun withService(block: (ScorpkAccessService) -> ActionResult): ActionResult {
        val svc = service ?: return accessibilityRequired()
        return block(svc)
    }

    private fun accessibilityRequired() = ActionResult.PermissionRequired(
        RequiredPermission.ACCESSIBILITY,
        "Activa el servicio de accesibilidad de Scorpk para esta acción."
    )

    private companion object {
        const val MAX_SCREEN_ITEMS = 40
        const val SEND_BUTTON_DELAY_MS = 250L
        val SEND_BUTTON_QUERIES = listOf("send", "enviar", "Enviar", "Send")
    }
}
