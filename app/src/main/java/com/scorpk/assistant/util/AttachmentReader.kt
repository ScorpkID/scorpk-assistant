package com.scorpk.assistant.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.asImageBitmap
import com.scorpk.assistant.domain.model.Attachment
import com.scorpk.assistant.domain.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee metadatos, texto y miniatura de un archivo elegido por el usuario (siempre fuera del hilo principal). */
class AttachmentReader(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    suspend fun read(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "archivo"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

        val kind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            isTextual(mime, name) -> AttachmentKind.TEXT
            else -> AttachmentKind.DOCUMENT
        }

        var text: String? = null
        var truncated = false
        if (kind == AttachmentKind.TEXT && (size ?: 0L) <= MAX_TEXT_BYTES) {
            resolver.openInputStream(uri)?.use { input ->
                val raw = input.bufferedReader(Charsets.UTF_8).readText()
                truncated = raw.length > MAX_TEXT_CHARS
                text = raw.take(MAX_TEXT_CHARS)
            }
        } else if (kind == AttachmentKind.TEXT) {
            truncated = true
            resolver.openInputStream(uri)?.use { input ->
                val buffer = CharArray(MAX_TEXT_CHARS)
                val read = input.bufferedReader(Charsets.UTF_8).read(buffer)
                if (read > 0) text = String(buffer, 0, read)
            }
        }

        Attachment(
            uri = uri.toString(),
            name = name,
            mimeType = mime,
            sizeBytes = size,
            kind = kind,
            textContent = text,
            truncated = truncated,
            thumbnail = if (kind == AttachmentKind.IMAGE) thumbnail(uri) else null,
            imageDataUrl = if (kind == AttachmentKind.IMAGE) ImageEncoder.fromUri(resolver, uri) else null
        )
    }

    private fun thumbnail(uri: Uri) = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMBNAIL_PX && bounds.outHeight / (sample * 2) >= THUMBNAIL_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    private fun isTextual(mime: String, name: String): Boolean =
        mime.startsWith("text/") ||
            mime in TEXT_MIME_TYPES ||
            name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

    private companion object {
        const val MAX_TEXT_BYTES = 512 * 1024L
        const val MAX_TEXT_CHARS = 12_000
        const val THUMBNAIL_PX = 128
        val TEXT_MIME_TYPES = setOf(
            "application/json", "application/xml", "application/javascript",
            "application/x-yaml", "application/csv", "application/sql"
        )
        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "csv", "json", "xml", "yaml", "yml", "kt", "kts", "java", "py", "js", "ts",
            "html", "css", "sql", "log", "ini", "properties", "gradle", "sh", "c", "cpp", "h", "swift"
        )
    }
}

/** Formatea un tamaño en bytes para mostrarlo en la UI. */
fun formatFileSize(bytes: Long?): String = when {
    bytes == null -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
