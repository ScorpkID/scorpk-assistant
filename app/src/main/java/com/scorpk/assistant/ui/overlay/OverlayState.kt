package com.scorpk.assistant.ui.overlay

import androidx.compose.ui.graphics.ImageBitmap

enum class OverlayPhase { IDLE, LISTENING, PROCESSING, RESULT }

/** Imagen lista para enviar con la consulta (captura de pantalla o foto de la cámara). */
data class OverlayImage(
    val id: Long,
    val label: String,
    val thumbnail: ImageBitmap?,
    val dataUrl: String
)

data class OverlayState(
    val visible: Boolean = false,
    val phase: OverlayPhase = OverlayPhase.LISTENING,
    /** Texto del campo: transcripción en vivo mientras se escucha o lo que el usuario escribe. */
    val input: String = "",
    val level: Float = 0f,
    val images: List<OverlayImage> = emptyList(),
    val submitted: String = "",
    val response: String = "",
    val success: Boolean = true,
    /** Aviso breve dentro de la tarjeta (permisos, errores de captura, "no te escuché"). */
    val notice: String? = null,
    val screenEnabled: Boolean = false,
    val cameraEnabled: Boolean = false,
    val capturing: Boolean = false,
    /** Se incrementa para pedir el foco y mostrar el teclado tras hacer la ventana enfocable. */
    val focusTick: Int = 0
)

class OverlayActions(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onMic: () -> Unit,
    val onCamera: () -> Unit,
    val onScreen: () -> Unit,
    val onRemoveImage: (Long) -> Unit,
    val onClose: () -> Unit,
    val onInputFocused: () -> Unit
)
