package com.scorpk.assistant.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Petición de chat compatible con OpenAI. Los mensajes son JSON libre porque el contenido
 * puede ser texto o, en modelos de visión, una lista de partes (texto + imágenes).
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<JsonObject>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat = ResponseFormat(),
    val temperature: Double = 0.1,
    @SerialName("max_tokens")
    val maxTokens: Int = 300
)

object ChatMessages {
    fun text(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    /** Mensaje de usuario con imágenes (data URLs "data:image/jpeg;base64,..."). */
    fun withImages(text: String, imageDataUrls: List<String>): JsonObject = buildJsonObject {
        put("role", "user")
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", text)
            }
            imageDataUrls.forEach { url ->
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", url) }
                }
            }
        }
    }
}

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList()
)

@Serializable
data class ChatCompletionChoice(
    val message: ResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class FireworksErrorResponse(
    val error: FireworksError? = null
)

@Serializable
data class FireworksError(
    val message: String? = null
)
