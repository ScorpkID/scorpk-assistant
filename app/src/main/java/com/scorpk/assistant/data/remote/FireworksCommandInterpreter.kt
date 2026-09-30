package com.scorpk.assistant.data.remote

import com.scorpk.assistant.BuildConfig
import com.scorpk.assistant.domain.interpreter.ChatTurn
import com.scorpk.assistant.domain.interpreter.CommandInterpreter
import com.scorpk.assistant.domain.interpreter.InterpreterException
import com.scorpk.assistant.domain.interpreter.PromptMode
import com.scorpk.assistant.domain.interpreter.SystemPrompt
import com.scorpk.assistant.domain.model.AiModel
import com.scorpk.assistant.domain.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Intérprete remoto: envía la orden a Fireworks AI (API compatible con OpenAI) con
 * response_format json_object y devuelve el JSON crudo de la acción para ActionParser.
 *
 * Dos rutas según cómo se compiló la app:
 * - Desarrollo: si [BuildConfig.FIREWORKS_API] (del .env) tiene valor, llama directo a Fireworks.
 * - Versiones publicadas: el APK no lleva ninguna clave; la petición viaja a [PROXY_ENDPOINT] en
 *   scorpk.tech con el token de sesión del usuario, y el servidor guarda la clave real.
 *
 * Hay dos instancias: la de voz, con modelo fijo, y la del chat, cuyo modelo elige el usuario.
 */
class FireworksCommandInterpreter(
    private val client: OkHttpClient,
    private val mode: PromptMode,
    private val modelProvider: suspend () -> AiModel,
    /** Token de acceso de la sesión de Supabase (null si no hay sesión). Solo se usa con el proxy. */
    private val sessionToken: suspend () -> String? = { null },
    private val apiKey: String = BuildConfig.FIREWORKS_API,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : CommandInterpreter {

    private val useProxy: Boolean get() = apiKey.isBlank()

    override suspend fun interpret(
        input: String,
        history: List<ChatTurn>,
        images: List<String>
    ): String = withContext(Dispatchers.IO) {
        val bearer = if (useProxy) {
            sessionToken()?.takeIf { it.isNotBlank() } ?: throw InterpreterException.NotSignedIn()
        } else {
            apiKey
        }

        val messages = buildList {
            add(ChatMessages.text("system", SystemPrompt.build(mode)))
            history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
                add(
                    ChatMessages.text(
                        role = if (turn.role == MessageRole.USER) "user" else "assistant",
                        content = turn.text.take(MAX_TURN_CHARS)
                    )
                )
            }
            add(if (images.isEmpty()) ChatMessages.text("user", input) else ChatMessages.withImages(input, images))
        }
        // Con imágenes se usa el modelo elegido si tiene visión; si no, el modelo de visión.
        val selected = modelProvider()
        val modelId = if (images.isNotEmpty() && !selected.supportsVision) AiModel.VISION.id else selected.id
        val payload = ChatCompletionRequest(
            model = modelId,
            messages = messages,
            maxTokens = if (mode == PromptMode.CHAT) CHAT_MAX_TOKENS else VOICE_MAX_TOKENS
        )
        val request = Request.Builder()
            .url(if (useProxy) PROXY_ENDPOINT else ENDPOINT)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            .post(json.encodeToString(ChatCompletionRequest.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: InterruptedIOException) {
            throw InterpreterException.Timeout(e)
        } catch (e: IOException) {
            throw InterpreterException.Network(e)
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw httpError(it.code, body, modelId)
            val content = try {
                json.decodeFromString(ChatCompletionResponse.serializer(), body)
                    .choices.firstOrNull()?.message?.content
            } catch (e: SerializationException) {
                throw InterpreterException.InvalidResponse("formato inesperado")
            }
            content?.takeIf { c -> c.isNotBlank() }
                ?: throw InterpreterException.InvalidResponse("contenido vacío")
        }
    }

    private fun httpError(code: Int, body: String, modelId: String): InterpreterException {
        val detail = try {
            json.decodeFromString(FireworksErrorResponse.serializer(), body).error?.message.orEmpty()
        } catch (e: SerializationException) {
            ""
        } catch (e: IllegalArgumentException) {
            ""
        }
        return when (code) {
            // Con el proxy, 401 significa sesión vencida; con la clave directa, clave inválida.
            401 -> if (useProxy) InterpreterException.NotSignedIn() else InterpreterException.InvalidApiKey()
            402 -> InterpreterException.ProRequired()
            403 -> InterpreterException.InvalidApiKey()
            404 -> InterpreterException.ModelUnavailable(modelId)
            429 -> InterpreterException.RateLimited()
            else -> InterpreterException.Http(code, detail.take(120))
        }
    }

    /** Ejecuta la llamada de forma asíncrona y la cancela si se cancela la corrutina. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }

    companion object {
        const val ENDPOINT = "https://api.fireworks.ai/inference/v1/chat/completions"
        const val PROXY_ENDPOINT = "https://scorpk.tech/api/ai/chat"
        private const val MAX_HISTORY_TURNS = 10
        private const val MAX_TURN_CHARS = 2_000
        private const val VOICE_MAX_TOKENS = 600
        private const val CHAT_MAX_TOKENS = 2_500
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
