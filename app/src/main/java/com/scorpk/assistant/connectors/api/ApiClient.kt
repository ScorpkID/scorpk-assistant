package com.scorpk.assistant.connectors.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** El token de la cuenta venció o fue revocado: hay que volver a autorizar el conector. */
class TokenExpiredException : Exception("La autorización venció.")

/** Otra falla de la API (permiso insuficiente, límite, red…), con mensaje apto para el usuario. */
class ApiException(message: String) : Exception(message)

/** Cliente REST mínimo: GET autenticado con Bearer que devuelve JSON. */
class ApiClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun get(
        url: String,
        token: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): JsonElement = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(httpUrl)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .get()
            .build()

        val response = try {
            http.newCall(request).await()
        } catch (e: InterruptedIOException) {
            throw ApiException("El servicio tardó demasiado en responder.")
        } catch (e: IOException) {
            throw ApiException("Sin conexión con el servicio.")
        }
        response.use {
            val body = it.body?.string().orEmpty()
            when {
                it.code == 401 -> throw TokenExpiredException()
                it.code == 403 && body.contains("insufficient", ignoreCase = true) ->
                    throw ApiException("La cuenta no concedió todos los permisos. Reconecta el servicio y acepta todos los accesos.")
                it.code == 403 -> throw ApiException("El servicio rechazó la petición (403). Revisa los permisos del conector.")
                it.code == 429 -> throw ApiException("El servicio alcanzó su límite de peticiones. Intenta de nuevo en un momento.")
                !it.isSuccessful -> throw ApiException("El servicio respondió con error ${it.code}.")
                else -> json.parseToJsonElement(body.ifBlank { "{}" })
            }
        }
    }

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
}
