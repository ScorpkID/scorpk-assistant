package com.scorpk.assistant.domain.interpreter

import com.scorpk.assistant.domain.model.MessageRole

/** Turno previo de la conversación, usado como contexto por los intérpretes conversacionales. */
data class ChatTurn(val role: MessageRole, val text: String)

/**
 * Traduce una orden en lenguaje natural a la salida JSON del contrato de function calling.
 * La implementación puede ser un LLM remoto o el intérprete local basado en reglas; en ambos
 * casos la salida se procesa con ActionParser y se ejecuta con ActionDispatcher.
 */
interface CommandInterpreter {
    /**
     * @param input orden actual (puede incluir el contenido de un archivo adjunto).
     * @param history turnos previos de la conversación, del más antiguo al más reciente.
     * @param images imágenes como data URL (JPEG base64) para modelos con visión.
     */
    suspend fun interpret(
        input: String,
        history: List<ChatTurn> = emptyList(),
        images: List<String> = emptyList()
    ): String
}
