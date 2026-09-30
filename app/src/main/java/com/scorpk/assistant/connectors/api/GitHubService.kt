package com.scorpk.assistant.connectors.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class GitHubNotification(val repo: String, val title: String, val reason: String)

data class GitHubRepo(val name: String, val description: String?, val stars: Int)

data class GitHubIssue(val repo: String, val title: String, val number: Int)

/** API REST de GitHub con el token OAuth de la cuenta. */
class GitHubService(private val api: ApiClient) {

    private val headers = mapOf(
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28"
    )

    suspend fun notifications(token: String): List<GitHubNotification> =
        api.get("https://api.github.com/notifications", token, mapOf("per_page" to "8"), headers)
            .asArray().map { item ->
                val n = item.jsonObject
                GitHubNotification(
                    repo = n["repository"]?.jsonObject?.string("full_name").orEmpty(),
                    title = n["subject"]?.jsonObject?.string("title").orEmpty(),
                    reason = reasonLabel(n.string("reason"))
                )
            }

    suspend fun repos(token: String): List<GitHubRepo> =
        api.get(
            "https://api.github.com/user/repos",
            token,
            mapOf("sort" to "updated", "per_page" to "8", "affiliation" to "owner,collaborator"),
            headers
        ).asArray().map { item ->
            val r = item.jsonObject
            GitHubRepo(
                name = r.string("full_name").orEmpty(),
                description = r.string("description"),
                stars = (r["stargazers_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            )
        }

    suspend fun assignedIssues(token: String): List<GitHubIssue> =
        api.get(
            "https://api.github.com/issues",
            token,
            mapOf("filter" to "assigned", "state" to "open", "per_page" to "8"),
            headers
        ).asArray().map { item ->
            val i = item.jsonObject
            GitHubIssue(
                repo = i["repository"]?.jsonObject?.string("full_name").orEmpty(),
                title = i.string("title").orEmpty(),
                number = (i["number"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            )
        }

    private fun JsonElement.asArray(): JsonArray =
        (this as? JsonArray) ?: JsonArray(emptyList())

    private fun reasonLabel(reason: String?): String = when (reason) {
        "mention" -> "te mencionaron"
        "review_requested" -> "piden tu revisión"
        "assign" -> "te asignaron"
        "author" -> "actividad en lo tuyo"
        "comment" -> "nuevo comentario"
        "ci_activity" -> "resultado de CI"
        "subscribed" -> "suscrito"
        else -> reason.orEmpty()
    }

}
