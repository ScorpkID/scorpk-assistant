package com.scorpk.assistant.connectors.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DriveFile(val name: String, val type: String, val modified: String?, val link: String?)

data class MailSummary(val from: String, val subject: String)

data class InboxOverview(val unread: Int, val latest: List<MailSummary>)

/** Drive v3 y Gmail v1 con el token OAuth de la cuenta de Google. */
class GoogleServices(private val api: ApiClient) {

    /** @param query texto a buscar en el nombre; null o vacío = archivos más recientes. */
    suspend fun searchDrive(token: String, query: String?): List<DriveFile> {
        val clean = query?.trim()?.replace("\\", "\\\\")?.replace("'", "\\'").orEmpty()
        val q = buildString {
            append("trashed = false")
            if (clean.isNotEmpty()) append(" and name contains '").append(clean).append("'")
        }
        val response = api.get(
            url = "https://www.googleapis.com/drive/v3/files",
            token = token,
            query = mapOf(
                "q" to q,
                "pageSize" to "8",
                "orderBy" to "modifiedTime desc",
                "fields" to "files(id,name,mimeType,webViewLink,modifiedTime)"
            )
        ).jsonObject
        return response["files"]?.jsonArray.orEmpty().map { item ->
            val file = item.jsonObject
            DriveFile(
                name = file.string("name") ?: "(sin nombre)",
                type = friendlyType(file.string("mimeType")),
                modified = file.string("modifiedTime")?.take(10),
                link = file.string("webViewLink")
            )
        }
    }

    /** Correos sin leer de la bandeja de entrada y remitente/asunto de los más recientes. */
    suspend fun inbox(token: String, search: String?): InboxOverview {
        val label = api.get(
            "https://gmail.googleapis.com/gmail/v1/users/me/labels/INBOX",
            token
        ).jsonObject
        val unread = (label["messagesUnread"] as? JsonPrimitive)?.intOrNull ?: 0

        val query = if (search.isNullOrBlank()) "in:inbox is:unread" else "in:inbox ${search.trim()}"
        val list = api.get(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages",
            token,
            query = mapOf("q" to query, "maxResults" to "5")
        ).jsonObject
        val ids = list["messages"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.string("id") }

        val latest = ids.map { id ->
            val message = api.get(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id",
                token,
                query = mapOf("format" to "metadata", "metadataHeaders" to "From")
            ).jsonObject
            val from = header(message, "From")
            MailSummary(
                from = from.substringBefore('<').trim().trim('"').ifBlank { from },
                subject = header(message, "Subject").ifBlank { "(sin asunto)" }
            )
        }
        return InboxOverview(unread, latest)
    }

    private fun header(message: JsonObject, name: String): String =
        message["payload"]?.jsonObject?.get("headers")?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .firstOrNull { it.string("name").equals(name, ignoreCase = true) }
            ?.string("value").orEmpty()

    private fun friendlyType(mime: String?): String = when {
        mime == null -> "Archivo"
        mime.endsWith("folder") -> "Carpeta"
        mime.endsWith("document") -> "Documento"
        mime.endsWith("spreadsheet") -> "Hoja de cálculo"
        mime.endsWith("presentation") -> "Presentación"
        mime.startsWith("image/") -> "Imagen"
        mime.startsWith("video/") -> "Video"
        mime == "application/pdf" -> "PDF"
        else -> "Archivo"
    }
}

internal fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

