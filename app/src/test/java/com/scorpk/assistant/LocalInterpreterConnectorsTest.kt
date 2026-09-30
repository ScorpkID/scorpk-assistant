package com.scorpk.assistant

import com.scorpk.assistant.actions.ActionParser
import com.scorpk.assistant.domain.interpreter.LocalCommandInterpreter
import com.scorpk.assistant.domain.model.ActionRequest
import com.scorpk.assistant.domain.model.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInterpreterConnectorsTest {

    private val parser = ActionParser()
    private val interpreter = LocalCommandInterpreter(parser)

    private fun run(input: String): ActionRequest =
        parser.parse(runBlocking { interpreter.interpret(input) }).getOrThrow()

    @Test
    fun `whatsapp con contacto y mensaje`() {
        val r = run("Escríbele a Ana por WhatsApp: ya voy en camino")
        assertEquals(ActionType.WHATSAPP_MESSAGE, r.type)
        assertEquals("Ana", r.parameters.target)
        assertEquals("Ya voy en camino", r.parameters.message)
    }

    @Test
    fun `whatsapp variante diciendo`() {
        val r = run("mándale a Juan Pérez un whatsapp diciendo llego tarde")
        assertEquals(ActionType.WHATSAPP_MESSAGE, r.type)
        assertEquals("Juan Pérez", r.parameters.target)
        assertEquals("Llego tarde", r.parameters.message)
    }

    @Test
    fun `youtube maps y llamada`() {
        run("busca recetas de arepas en YouTube").let {
            assertEquals(ActionType.YOUTUBE_SEARCH, it.type)
            assertEquals("recetas de arepas", it.parameters.target)
        }
        run("Llévame al aeropuerto").let {
            assertEquals(ActionType.NAVIGATE, it.type)
            assertEquals("aeropuerto", it.parameters.target)
        }
        run("llama a mamá").let {
            assertEquals(ActionType.CALL_CONTACT, it.type)
            assertEquals("mamá", it.parameters.target)
        }
    }

    @Test
    fun `drive gmail y github`() {
        run("busca el presupuesto en mi Drive").let {
            assertEquals(ActionType.DRIVE_SEARCH, it.type)
            assertEquals("presupuesto", it.parameters.target)
        }
        assertEquals(ActionType.GMAIL_INBOX, run("¿tengo correos nuevos?").type)
        run("¿cuáles son mis issues de GitHub?").let {
            assertEquals(ActionType.GITHUB_QUERY, it.type)
            assertEquals("issues", it.parameters.valueAsString)
        }
    }

    @Test
    fun `correo con destinatario y mensaje`() {
        val r = run("escribe un correo a Ana diciendo que llego tarde")
        assertEquals(ActionType.COMPOSE_EMAIL, r.type)
        assertEquals("Ana", r.parameters.target)
        assertNotNull(r.parameters.message)
    }

    @Test
    fun `calendario crea evento con fecha y hora`() {
        val r = run("agenda dentista el viernes a las 4 de la tarde")
        assertEquals(ActionType.CALENDAR_CREATE, r.type)
        assertEquals("Dentista", r.parameters.target)
        assertTrue(r.parameters.valueAsString!!.endsWith("T16:00"))
    }

    @Test
    fun `calendario consulta la agenda`() {
        assertEquals(ActionType.CALENDAR_QUERY, run("¿qué tengo mañana?").type)
    }

    @Test
    fun `las reglas viejas siguen funcionando`() {
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, run("prende la linterna").type)
        assertEquals(ActionType.SET_ALARM, run("pon una alarma a las 7:30").type)
        assertEquals(ActionType.OPEN_APP, run("abre WhatsApp").type)
        assertEquals(ActionType.SPOTIFY_CONTROL, run("pon Bad Bunny en Spotify").type)
        assertNull(run("abre WhatsApp").parameters.message)
    }
}
