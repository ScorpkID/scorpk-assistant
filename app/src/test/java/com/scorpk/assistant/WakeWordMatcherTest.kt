package com.scorpk.assistant

import com.scorpk.assistant.voice.WakeWordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatcherTest {

    @Test
    fun `detecta el nombre con y sin prefijo`() {
        assertNotNull(WakeWordMatcher.match("hey scorpk"))
        assertNotNull(WakeWordMatcher.match("Oye, Escorpk"))
        assertNotNull(WakeWordMatcher.match("scorp"))
    }

    @Test
    fun `tolera transcripciones foneticas aproximadas`() {
        listOf("oye escorpión", "hey escorpio", "ey escort", "oye skorpk", "hey score", "es corp").forEach {
            assertNotNull("Debería activar: $it", WakeWordMatcher.match(it))
        }
    }

    @Test
    fun `normaliza tildes y signos`() {
        assertEquals("oye escorpion abre spotify", WakeWordMatcher.normalize("¡Oye, Escorpión! Abre Spotify."))
    }

    @Test
    fun `devuelve la orden encadenada tras el wake word`() {
        val match = WakeWordMatcher.match("oye escorpio prende la linterna")
        assertNotNull(match)
        assertEquals("prende la linterna", match!!.remainder)
    }

    @Test
    fun `ignora frases comunes sin el nombre`() {
        listOf(
            "hola como estas",
            "pasame la escoba",
            "vamos a la escuela",
            "abre la puerta",
            "el sector norte",
            "que hora es"
        ).forEach {
            assertNull("No debería activar: $it", WakeWordMatcher.match(it))
        }
    }

    @Test
    fun `similitud respeta el umbral del 75 por ciento`() {
        assertTrue(WakeWordMatcher.similarity("escort", "scort") >= WakeWordMatcher.THRESHOLD)
        assertTrue(WakeWordMatcher.similarity("escoba", "escor") < WakeWordMatcher.THRESHOLD)
        assertEquals(1, WakeWordMatcher.levenshtein("scorpk", "skorpk"))
    }
}
