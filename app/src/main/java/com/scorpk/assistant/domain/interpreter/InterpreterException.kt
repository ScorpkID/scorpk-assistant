package com.scorpk.assistant.domain.interpreter

/**
 * Errores de interpretación con un mensaje apto para mostrar al usuario.
 * [allowsFallback] indica si es razonable reintentar con el intérprete local.
 */
sealed class InterpreterException(
    val userMessage: String,
    val allowsFallback: Boolean,
    cause: Throwable? = null
) : Exception(userMessage, cause) {

    class NotSignedIn : InterpreterException(
        "Inicia sesión en Cuenta para usar la IA de Scorpk.",
        allowsFallback = true
    )

    class ProRequired : InterpreterException(
        "La IA de Scorpk requiere el plan Pro. Actívalo en scorpk.tech/pricing.",
        allowsFallback = true
    )

    class InvalidApiKey : InterpreterException(
        "La API key de Fireworks no es válida.",
        allowsFallback = true
    )

    class Timeout(cause: Throwable) : InterpreterException(
        "Fireworks AI tardó demasiado en responder.",
        allowsFallback = true,
        cause = cause
    )

    class Network(cause: Throwable) : InterpreterException(
        "Sin conexión con Fireworks AI.",
        allowsFallback = true,
        cause = cause
    )

    class RateLimited : InterpreterException(
        "Fireworks AI alcanzó el límite de peticiones.",
        allowsFallback = true
    )

    class ModelUnavailable(model: String) : InterpreterException(
        "El modelo ${model.substringAfterLast('/')} no está disponible en Fireworks AI.",
        allowsFallback = true
    )

    class Http(code: Int, detail: String) : InterpreterException(
        "Fireworks AI respondió con error $code${if (detail.isNotBlank()) ": $detail" else ""}.",
        allowsFallback = true
    )

    class InvalidResponse(detail: String) : InterpreterException(
        "Respuesta inválida del modelo: $detail",
        allowsFallback = true
    )
}
