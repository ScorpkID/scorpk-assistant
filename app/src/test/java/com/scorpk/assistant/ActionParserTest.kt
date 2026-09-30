package com.scorpk.assistant

import com.scorpk.assistant.actions.ActionParser
import com.scorpk.assistant.domain.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    private val parser = ActionParser()

    @Test
    fun `parsea el contrato json del llm`() {
        val raw = """{"action":"set_alarm","parameters":{"target":null,"value":"07:30","message":"Gym"},"feedback_speech":"Listo"}"""
        val request = parser.parse(raw).getOrThrow()
        assertEquals(ActionType.SET_ALARM, request.type)
        assertEquals("07:30", request.parameters.valueAsString)
        assertEquals("Gym", request.parameters.message)
    }

    @Test
    fun `extrae el json aunque venga envuelto en markdown`() {
        val raw = "```json\n{\"action\":\"toggle_flashlight\",\"parameters\":{\"value\":true},\"feedback_speech\":\"Ok\"}\n```"
        val request = parser.parse(raw).getOrThrow()
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, request.type)
        assertEquals(true, request.parameters.valueAsBoolean)
    }

    @Test
    fun `rechaza acciones desconocidas`() {
        assertTrue(parser.parse("""{"action":"hack_nasa"}""").isFailure)
        assertTrue(parser.parse("sin json").isFailure)
    }
}
