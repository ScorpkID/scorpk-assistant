package com.scorpk.assistant.connectors

import com.scorpk.assistant.connectors.api.ApiException
import com.scorpk.assistant.connectors.api.GitHubService
import com.scorpk.assistant.connectors.api.GoogleServices
import com.scorpk.assistant.connectors.api.TokenExpiredException
import com.scorpk.assistant.domain.model.ActionResult

/** Acciones que consultan APIs de cuentas conectadas (Drive, Gmail, GitHub). */
class AccountConnectors(
    private val tokens: ConnectorTokenStore,
    private val google: GoogleServices,
    private val github: GitHubService
) {

    suspend fun driveSearch(query: String?): ActionResult = withToken(ConnectorType.GOOGLE_DRIVE) { token ->
        val files = google.searchDrive(token, query)
        val topic = query?.trim()?.takeIf { it.isNotEmpty() }
        if (files.isEmpty()) {
            ActionResult.Success(if (topic != null) "No encontré archivos que contengan \"$topic\" en tu Drive." else "Tu Drive no tiene archivos.")
        } else {
            val header = if (topic != null) "Esto encontré en tu Drive para \"$topic\":" else "Tus archivos más recientes de Drive:"
            ActionResult.Success(
                header + "\n" + files.joinToString("\n") { file ->
                    val link = file.link?.let { " — [abrir]($it)" }.orEmpty()
                    "• ${file.name} (${file.type}${file.modified?.let { ", $it" }.orEmpty()})$link"
                }
            )
        }
    }

    suspend fun gmailInbox(search: String?): ActionResult = withToken(ConnectorType.GMAIL) { token ->
        val inbox = google.inbox(token, search)
        val summary = when (inbox.unread) {
            0 -> "No tienes correos sin leer."
            1 -> "Tienes 1 correo sin leer."
            else -> "Tienes ${inbox.unread} correos sin leer."
        }
        if (inbox.latest.isEmpty()) {
            ActionResult.Success(if (search.isNullOrBlank()) summary else "No encontré correos sobre \"${search.trim()}\".")
        } else {
            ActionResult.Success(summary + "\n" + inbox.latest.joinToString("\n") { "• ${it.from}: ${it.subject}" })
        }
    }

    /** @param what "notifications" | "repos" | "issues" */
    suspend fun github(what: String?): ActionResult = withToken(ConnectorType.GITHUB) { token ->
        when (what?.lowercase()) {
            "repos", "repositories" -> {
                val repos = github.repos(token)
                if (repos.isEmpty()) {
                    ActionResult.Success("No encontré repositorios en tu cuenta.")
                } else {
                    ActionResult.Success("Tus repositorios más recientes:\n" + repos.joinToString("\n") {
                        "• ${it.name}" + (it.description?.let { d -> " — $d" }.orEmpty()) + " ★${it.stars}"
                    })
                }
            }
            "issues" -> {
                val issues = github.assignedIssues(token)
                if (issues.isEmpty()) {
                    ActionResult.Success("No tienes issues abiertos asignados.")
                } else {
                    ActionResult.Success("Issues abiertos que tienes asignados:\n" + issues.joinToString("\n") {
                        "• ${it.repo}#${it.number}: ${it.title}"
                    })
                }
            }
            else -> {
                val notifications = github.notifications(token)
                if (notifications.isEmpty()) {
                    ActionResult.Success("No tienes notificaciones nuevas en GitHub.")
                } else {
                    ActionResult.Success("Tus notificaciones de GitHub:\n" + notifications.joinToString("\n") {
                        "• ${it.repo}: ${it.title} (${it.reason})"
                    })
                }
            }
        }
    }

    private suspend fun withToken(type: ConnectorType, block: suspend (String) -> ActionResult): ActionResult {
        val account = type.account ?: return ActionResult.Failure("Este conector no usa una cuenta.")
        val stored = tokens.get(account)
        if (stored == null || !stored.isUsable(account)) {
            return ActionResult.Failure("La conexión con ${account.label} venció. Vuelve a conectar ${type.label} en Conectores.")
        }
        return try {
            block(stored.accessToken)
        } catch (e: TokenExpiredException) {
            tokens.markExpired(account)
            ActionResult.Failure("La autorización de ${account.label} venció. Vuelve a conectar ${type.label} en Conectores.")
        } catch (e: ApiException) {
            ActionResult.Failure(e.message ?: "No pude consultar ${type.label}.")
        }
    }
}
