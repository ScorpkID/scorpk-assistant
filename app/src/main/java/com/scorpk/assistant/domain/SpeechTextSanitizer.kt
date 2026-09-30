package com.scorpk.assistant.domain

/**
 * Limpia el texto antes de enviarlo al motor TTS para que no se lean literalmente
 * símbolos de Markdown, bloques de código, URLs ni viñetas.
 */
object SpeechTextSanitizer {

    private val CODE_BLOCK = Regex("""```[\s\S]*?(```|$)""")
    private val INLINE_CODE = Regex("""`([^`]*)`""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val URL = Regex("""(https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("""(?m)^\s{0,3}#{1,6}\s*""")
    private val BLOCKQUOTE = Regex("""(?m)^\s*>\s?""")
    private val BULLET = Regex("""(?m)^\s*([-*+•·]|\d+[.)])\s+""")
    private val HORIZONTAL_RULE = Regex("""(?m)^\s*([-*_]\s*){3,}$""")
    private val BOLD_ITALIC = Regex("""(\*{1,3}|_{1,3})(\S(?:.*?\S)?)\1""")
    private val STRAY_MARKS = Regex("""[*#`~|>]+""")
    private val TABLE_SEPARATOR = Regex("""(?m)^\s*\|?\s*:?-{2,}.*$""")
    private val SPACES = Regex("""[ \t]+""")
    private val SPACE_BEFORE_PUNCT = Regex("""\s+([,.;:!?])""")
    private val REPEATED_PUNCT = Regex("""([.;:])(\s*[.;:])+""")
    private val ENDS_WITH_PUNCT = Regex("""[.!?¡¿:;,]$""")

    fun sanitize(text: String): String {
        var out = text
            .replace(CODE_BLOCK, " ")
            .replace(IMAGE, "$1")
            .replace(LINK, "$1")
            .replace(URL, "")
            .replace(INLINE_CODE, "$1")
            .replace(TABLE_SEPARATOR, "")
            .replace(HORIZONTAL_RULE, "")
            .replace(HEADING, "")
            .replace(BLOCKQUOTE, "")
            .replace(BULLET, "")
        // Negrita/cursiva anidadas: se aplica varias veces hasta que no queden marcas.
        repeat(3) { out = out.replace(BOLD_ITALIC, "$2") }
        out = out.replace(STRAY_MARKS, " ")

        // Cada línea no vacía se convierte en una frase para que la voz haga pausas naturales.
        val lines = out.lines()
            .map { it.replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
        out = if (lines.size <= 1) {
            lines.firstOrNull().orEmpty()
        } else {
            lines.joinToString(" ") { line -> if (ENDS_WITH_PUNCT.containsMatchIn(line)) line else "$line." }
        }

        return out
            .replace(SPACE_BEFORE_PUNCT, "$1")
            .replace(REPEATED_PUNCT, "$1")
            .replace(SPACES, " ")
            .trim()
    }
}
