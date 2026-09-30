package com.scorpk.assistant

import com.scorpk.assistant.domain.SpeechTextSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeechTextSanitizerTest {

    private fun clean(text: String) = SpeechTextSanitizer.sanitize(text)

    @Test
    fun `quita negritas cursivas y encabezados`() {
        assertEquals("Resumen. La capital es Canberra.", clean("## Resumen\nLa capital es **Canberra**."))
        assertEquals("Esto es importante", clean("Esto es *importante*"))
    }

    @Test
    fun `convierte listas en frases sin viñetas`() {
        assertEquals("Leche. Pan. Huevos.", clean("- Leche\n- Pan\n* Huevos"))
        assertEquals("Primero. Segundo.", clean("1. Primero\n2) Segundo"))
    }

    @Test
    fun `elimina bloques de codigo y urls`() {
        val out = clean("Usa esto:\n```kotlin\nval x = 1\n```\nMás info en https://ejemplo.com/docs ahora.")
        assertFalse(out.contains("val x"))
        assertFalse(out.contains("http"))
        assertEquals("Usa esto: Más info en ahora.", out)
    }

    @Test
    fun `conserva el texto de enlaces y codigo en linea`() {
        assertEquals("Abre la guía y ejecuta gradlew.", clean("Abre [la guía](https://x.y) y ejecuta `gradlew`."))
    }

    @Test
    fun `no rompe texto plano`() {
        assertEquals("Linterna encendida", clean("Linterna encendida"))
        assertEquals("Batería al 80%, cargando", clean("Batería al 80%, cargando"))
    }
}
