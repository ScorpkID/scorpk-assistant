package com.scorpk.assistant.actions

import com.scorpk.assistant.domain.model.ActionRequest
import com.scorpk.assistant.domain.model.ActionType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Convierte la salida textual del intérprete (LLM o local) en un [ActionRequest].
 * Tolera bloques de código Markdown y texto alrededor del objeto JSON.
 */
class ActionParser(
    private val json: Json = DefaultJson
) {

    fun parse(raw: String): Result<ActionRequest> {
        val payload = extractJsonObject(raw)
            ?: return Result.failure(IllegalArgumentException("La respuesta no contiene un objeto JSON"))
        return try {
            val request = json.decodeFromString(ActionRequest.serializer(), payload)
            if (request.type == ActionType.UNKNOWN) {
                Result.failure(IllegalArgumentException("Acción desconocida: ${request.action}"))
            } else {
                Result.success(request)
            }
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    fun encode(request: ActionRequest): String = json.encodeToString(ActionRequest.serializer(), request)

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}
