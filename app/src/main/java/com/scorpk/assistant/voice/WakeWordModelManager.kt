package com.scorpk.assistant.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Descarga y prepara el modelo on-device de Vosk (español, ~40 MB) usado para detectar el
 * wake-word sin SpeechRecognizer: sin pitidos y sin enviar audio a ningún servidor.
 */
class WakeWordModelManager(context: Context, private val client: OkHttpClient) {

    sealed interface ModelState {
        data object NotDownloaded : ModelState
        data class Downloading(val progress: Float) : ModelState
        data object Ready : ModelState
        data class Failed(val message: String) : ModelState
    }

    private val filesDir = context.applicationContext.filesDir
    val modelDir: File = File(filesDir, MODEL_DIR_NAME)
    private val marker = File(modelDir, READY_MARKER)

    private val _state = MutableStateFlow(if (marker.exists()) ModelState.Ready else ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val isReady: Boolean get() = marker.exists()

    /** Descarga y descomprime el modelo. Devuelve true si queda listo. */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (isReady) {
            _state.value = ModelState.Ready
            return@withContext true
        }
        if (_state.value is ModelState.Downloading) return@withContext false
        _state.value = ModelState.Downloading(0f)
        val zipFile = File(filesDir, "$MODEL_DIR_NAME.zip.part")
        val tempDir = File(filesDir, "$MODEL_DIR_NAME.tmp")
        try {
            fetch(zipFile)
            tempDir.deleteRecursively()
            unzipStrippingRoot(zipFile, tempDir)
            modelDir.deleteRecursively()
            if (!tempDir.renameTo(modelDir)) throw IOException("No se pudo mover el modelo")
            marker.writeText(MODEL_URL)
            _state.value = ModelState.Ready
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al descargar el modelo de voz", e)
            tempDir.deleteRecursively()
            _state.value = ModelState.Failed(
                if (e is IOException) "No se pudo descargar el modelo. Revisa tu conexión." else "Descarga cancelada."
            )
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        } finally {
            zipFile.delete()
        }
    }

    fun delete() {
        modelDir.deleteRecursively()
        _state.value = ModelState.NotDownloaded
    }

    private suspend fun fetch(target: File) {
        val request = Request.Builder().url(MODEL_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Respuesta vacía")
            val total = body.contentLength().takeIf { it > 0 } ?: MODEL_SIZE_ESTIMATE
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReported = 0f
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                        if (progress - lastReported >= 0.01f) {
                            lastReported = progress
                            _state.value = ModelState.Downloading(progress)
                        }
                    }
                }
            }
        }
    }

    /** Extrae el zip omitiendo la carpeta raíz ("vosk-model-small-es-0.42/"). */
    private suspend fun unzipStrippingRoot(zip: File, destination: File) {
        val canonicalDest = destination.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = stream.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "")
                if (relative.isEmpty()) continue
                val out = File(destination, relative)
                if (!out.canonicalPath.startsWith(canonicalDest)) throw IOException("Entrada inválida: ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { stream.copyTo(it) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        const val MODEL_DIR_NAME = "vosk-model-es"
        const val READY_MARKER = ".ready"
        const val MODEL_SIZE_ESTIMATE = 39_817_833L
        const val BUFFER_SIZE = 64 * 1024
    }
}
