package com.scorpk.assistant.voice

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Detección fonética flexible del wake-word. Acepta "Hey/Oye <nombre>" o el nombre solo,
 * comparando cada palabra transcrita (y cada par de palabras unidas, p. ej. "es corp")
 * contra una lista de alias mediante similitud de Levenshtein o coincidencia de prefijo.
 */
object WakeWordMatcher {

    val ALIASES = listOf(
        "scorpk", "scorp", "skorp", "escorp", "escor", "score",
        "escorpion", "escorpio", "scor", "scort", "skor"
    )

    const val THRESHOLD = 0.75

    private const val MIN_TOKEN_LENGTH = 3
    private const val MIN_PREFIX_LENGTH = 4
    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9\s]""")
    private val WHITESPACE = Regex("""\s+""")

    data class Match(
        val alias: String,
        val heard: String,
        val similarity: Double,
        /** Texto normalizado que sigue al wake-word (posible orden encadenada). */
        val remainder: String
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()

    fun match(text: String, threshold: Double = THRESHOLD): Match? {
        val tokens = normalize(text).split(' ').filter { it.isNotEmpty() }
        for (i in tokens.indices) {
            val single = bestAlias(tokens[i])
            val joined = if (i + 1 < tokens.size) bestAlias(tokens[i] + tokens[i + 1]) else null

            val candidate = listOfNotNull(
                single?.let { Triple(it.first, it.second, i + 1) },
                joined?.let { Triple(it.first, it.second, i + 2) }
            ).maxByOrNull { it.second } ?: continue

            if (candidate.second >= threshold) {
                val consumed = candidate.third
                return Match(
                    alias = candidate.first,
                    heard = tokens.subList(i, consumed).joinToString(" "),
                    similarity = candidate.second,
                    remainder = tokens.drop(consumed).joinToString(" ")
                )
            }
        }
        return null
    }

    /** Alias más parecido a [token] y su puntuación (0..1), o null si el token es demasiado corto. */
    private fun bestAlias(token: String): Pair<String, Double>? {
        if (token.length < MIN_TOKEN_LENGTH) return null
        return ALIASES.map { alias -> alias to score(token, alias) }.maxByOrNull { it.second }
    }

    fun score(token: String, alias: String): Double {
        if (token == alias) return 1.0
        val prefixMatch =
            (alias.length >= MIN_PREFIX_LENGTH && token.startsWith(alias)) ||
                (token.length >= MIN_PREFIX_LENGTH && alias.startsWith(token))
        if (prefixMatch) return 1.0
        return similarity(token, alias)
    }

    /** 1 - distancia de Levenshtein normalizada por la longitud mayor. */
    fun similarity(a: String, b: String): Double {
        val longest = max(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
