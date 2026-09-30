package com.scorpk.assistant.domain

/**
 * Formato del texto de las respuestas guardadas. Una nota del sistema (p. ej. «usé el modo local»)
 * viaja al final del texto, separada del cuerpo, para que la interfaz la muestre aparte y para
 * que nunca se envíe a la IA como parte de la conversación.
 */
object AssistantText {

    private const val NOTE_MARKER = "\n\n[nota] "

    fun withNote(body: String, note: String?): String =
        if (note.isNullOrBlank()) body else body + NOTE_MARKER + note.trim()

    /** Devuelve (cuerpo, nota). La nota es null si no hay. */
    fun split(text: String): Pair<String, String?> {
        val index = text.lastIndexOf(NOTE_MARKER)
        if (index < 0) return text to null
        return text.substring(0, index) to text.substring(index + NOTE_MARKER.length).trim().ifBlank { null }
    }

    fun body(text: String): String = split(text).first
}
