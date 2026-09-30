package com.scorpk.assistant.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tabla `profiles` (id = auth.users.id). */
@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("custom_wake_word")
    val customWakeWord: String? = DEFAULT_WAKE_WORD,
    @SerialName("created_at")
    val createdAt: String? = null
) {
    companion object {
        const val DEFAULT_WAKE_WORD = "scorpk"
    }
}

/** Tabla `tasks` (id/user_id uuid, due_date timestamptz, priority text). */
@Serializable
data class TaskItem(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val description: String? = null,
    @SerialName("due_date")
    val dueDate: String? = null,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    val priority: String? = "medium",
    @SerialName("is_reminder")
    val isReminder: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Tabla `user_connectors` (id/user_id uuid, scopes text[]). Una fila = servicio conectado;
 * hay una única fila por (user_id, provider).
 *
 * [accessToken] es obligatorio en el esquema, pero la app guarda "" a propósito: los tokens de
 * Google/GitHub permanecen cifrados en el dispositivo y nunca se suben al servidor.
 */
@Serializable
data class UserConnector(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val provider: String,
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    val scopes: List<String> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

/** Tabla `subscriptions` (la escribe solo el webhook de Stripe de la web; la app solo lee). */
@Serializable
data class Subscription(
    @SerialName("user_id")
    val userId: String? = null,
    val plan: String = "free",
    val status: String? = null,
    @SerialName("current_period_end")
    val currentPeriodEnd: String? = null
) {
    /** Igual que la web: Pro solo cuando el plan es pro y la suscripción está activa. */
    val isPro: Boolean get() = plan == "pro" && (status == "active" || status == "trialing")
}
