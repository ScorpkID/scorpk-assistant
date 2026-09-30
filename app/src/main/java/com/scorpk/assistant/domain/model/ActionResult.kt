package com.scorpk.assistant.domain.model

enum class RequiredPermission {
    MICROPHONE,
    CAMERA,
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATIONS,
    CALENDAR,
    CONTACTS,
    /** Acceso a notificaciones: permite controlar sesiones de medios (Spotify) directamente. */
    NOTIFICATION_ACCESS
}

sealed interface ActionResult {
    val message: String

    data class Success(override val message: String) : ActionResult

    data class Failure(override val message: String) : ActionResult

    data class PermissionRequired(
        val permission: RequiredPermission,
        override val message: String
    ) : ActionResult
}
