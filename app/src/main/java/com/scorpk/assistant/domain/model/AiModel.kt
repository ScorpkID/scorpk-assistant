package com.scorpk.assistant.domain.model

/**
 * Modelos serverless de Fireworks AI disponibles en Scorpk.
 * Verificados contra GET /inference/v1/models y probados con el prompt de acciones
 * (JSON válido en todas las pruebas).
 */
enum class AiModel(
    val id: String,
    val label: String,
    val description: String,
    /** Acepta imágenes (capturas de pantalla, fotos de la cámara, adjuntos). */
    val supportsVision: Boolean = false
) {
    GPT_OSS_120B(
        id = "accounts/fireworks/models/gpt-oss-120b",
        label = "GPT-OSS 120B",
        description = "Rápido y preciso · ~1 s"
    ),
    DEEPSEEK_V4_1_FLASH(
        id = "accounts/fireworks/models/deepseek-v4p1-flash",
        label = "DeepSeek V4.1 Flash",
        description = "Ágil y versátil · ve imágenes",
        supportsVision = true
    ),
    GLM_5_3_FLASH(
        id = "accounts/fireworks/models/glm-5p3-flash",
        label = "GLM 5.3 Flash",
        description = "Visión rápida · pantalla y cámara",
        supportsVision = true
    ),
    GLM_5_3(
        id = "accounts/fireworks/models/glm-5p3",
        label = "GLM 5.3",
        description = "Razonamiento avanzado y código"
    );

    companion object {
        /** Modelo fijo del asistente de voz / wake-word / overlay (no modificable desde la UI). */
        val VOICE: AiModel = GPT_OSS_120B

        /** Modelo usado cuando la petición incluye imágenes y el modelo elegido no tiene visión. */
        val VISION: AiModel = GLM_5_3_FLASH

        /** Modelos que el usuario puede elegir para el chat en pantalla. */
        val CHAT_OPTIONS: List<AiModel> = entries

        val CHAT_DEFAULT: AiModel = GPT_OSS_120B

        /** Ids desconocidos (p. ej. modelos retirados guardados en DataStore) vuelven al predeterminado. */
        fun fromId(id: String?): AiModel = entries.firstOrNull { it.id == id } ?: CHAT_DEFAULT
    }
}
