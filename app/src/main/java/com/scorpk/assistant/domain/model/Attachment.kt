package com.scorpk.assistant.domain.model

import androidx.compose.ui.graphics.ImageBitmap

enum class AttachmentKind { IMAGE, TEXT, DOCUMENT }

/**
 * Archivo elegido con el selector nativo. [textContent] solo existe para documentos de texto
 * legibles (txt, md, csv, json, código…), que se envían al modelo como contexto.
 * [thumbnail] es una miniatura en memoria para la previsualización (no se persiste).
 */
data class Attachment(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val kind: AttachmentKind,
    val textContent: String?,
    val truncated: Boolean,
    val thumbnail: ImageBitmap?,
    /** Imagen reducida (máx. 1280 px) en JPEG base64 para modelos con visión. */
    val imageDataUrl: String? = null
)
