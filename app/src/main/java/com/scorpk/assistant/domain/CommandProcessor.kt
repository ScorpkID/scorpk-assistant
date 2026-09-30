package com.scorpk.assistant.domain

import android.util.Log
import com.scorpk.assistant.actions.ActionDispatcher
import com.scorpk.assistant.actions.ActionParser
import com.scorpk.assistant.data.local.SettingsDataStore
import com.scorpk.assistant.data.repository.ChatRepository
import com.scorpk.assistant.domain.interpreter.ChatTurn
import com.scorpk.assistant.domain.interpreter.CommandInterpreter
import com.scorpk.assistant.domain.interpreter.InterpreterException
import com.scorpk.assistant.domain.model.ActionRequest
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.ActionType
import com.scorpk.assistant.domain.model.Attachment
import com.scorpk.assistant.domain.model.AttachmentKind
import com.scorpk.assistant.domain.model.CommandSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

data class CommandOutcome(
    val conversationId: Long,
    val request: ActionRequest?,
    val result: ActionResult,
    /** Aviso cuando la orden se resolvió con el intérprete local en vez del LLM. */
    val notice: String?,
    /** Texto de la respuesta tal como se muestra y se lee en voz alta (sin la nota del sistema). */
    val displayText: String
)

/**
 * Pipeline completo de una orden: persistir → interpretar (LLM → JSON) → parsear → despachar →
 * persistir respuesta → confirmar por voz. Lo comparten la UI, el overlay y el wake-word.
 *
 * - Órdenes del wake-word/overlay ([CommandSource.WAKE_WORD]) → [voiceInterpreter] (modelo fijo y rápido).
 * - Órdenes del chat en pantalla (texto o micrófono) → [chatInterpreter] (modelo elegido por el usuario,
 *   con historial de la conversación y archivos adjuntos como contexto).
 *
 * Si el LLM falla por red, timeout, falta de API key o JSON inválido, se reintenta con
 * [fallbackInterpreter] para que las acciones locales sigan funcionando.
 */
class CommandProcessor(
    private val repository: ChatRepository,
    private val voiceInterpreter: CommandInterpreter,
    private val chatInterpreter: CommandInterpreter,
    private val fallbackInterpreter: CommandInterpreter?,
    private val parser: ActionParser,
    private val dispatcher: ActionDispatcher,
    private val speech: SpeechOutput,
    private val settings: SettingsDataStore
) {

    private data class Interpretation(val raw: String?, val request: ActionRequest?, val notice: String?, val error: String?)

    /**
     * @param onConversationReady se invoca en cuanto el comando del usuario queda guardado,
     * antes de ejecutar la acción, para que la UI pueda mostrar la conversación de inmediato.
     */
    suspend fun process(
        conversationId: Long?,
        input: String,
        source: CommandSource,
        attachment: Attachment? = null,
        images: List<String> = emptyList(),
        onConversationReady: (Long) -> Unit = {}
    ): CommandOutcome {
        val text = input.trim().ifBlank { attachment?.let { "Analiza el archivo ${it.name}" }.orEmpty() }
        val convId = conversationId ?: repository.createConversation(titleFor(text))
        val userMessageId = repository.addUserCommand(convId, text, source, attachment)
        onConversationReady(convId)

        val isChat = source != CommandSource.WAKE_WORD
        val history = if (conversationId != null) {
            repository.recentTurns(convId, HISTORY_LIMIT, excludeId = userMessageId)
        } else {
            emptyList()
        }
        val allImages = images + listOfNotNull(attachment?.imageDataUrl)
        val interpretation = interpret(
            interpreter = if (isChat) chatInterpreter else voiceInterpreter,
            prompt = withAttachment(text, attachment),
            plainText = text,
            history = history,
            images = allImages
        )
        val request = interpretation.request

        val result = if (request == null) {
            ActionResult.Failure(interpretation.error ?: "No pude interpretar la orden.")
        } else {
            try {
                dispatcher.dispatch(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al ejecutar ${request.action}", e)
                ActionResult.Failure("Ocurrió un error al ejecutar la acción.")
            }
        }

        val reply = displayText(request, result)
        val stored = AssistantText.withNote(reply, interpretation.notice)
        repository.addAssistantResponse(
            conversationId = convId,
            text = stored,
            action = request?.action,
            actionJson = interpretation.raw,
            success = result is ActionResult.Success,
            source = source
        )

        if (settings.settings.first().voiceFeedbackEnabled) {
            // En el chat las respuestas largas se leen en pantalla; por voz solo la confirmación breve.
            val spoken = if (isChat && reply.length > SPOKEN_MAX_CHARS) request?.feedbackSpeech.orEmpty() else reply
            speech.speak(spoken)
        }
        Log.d(TAG, "[$source] \"$text\" -> ${request?.action} -> ${result::class.simpleName}")
        return CommandOutcome(convId, request, result, interpretation.notice, reply)
    }

    /**
     * Texto de la respuesta. En acciones de confirmación (abrir una app, encender la linterna…) que
     * salieron bien se prefiere la frase natural del modelo («Listo, ya prendí la linterna»); cuando la
     * respuesta contiene datos reales (batería, agenda, correos…) se conserva el resultado tal cual.
     */
    private fun displayText(request: ActionRequest?, result: ActionResult): String {
        val natural = request?.feedbackSpeech?.trim().orEmpty()
        if (result is ActionResult.Success && request != null && request.type in CONFIRMATION_ACTIONS && natural.isNotBlank()) {
            return natural
        }
        return result.message.ifBlank { natural }
    }

    private suspend fun interpret(
        interpreter: CommandInterpreter,
        prompt: String,
        plainText: String,
        history: List<ChatTurn>,
        images: List<String>
    ): Interpretation {
        val primaryError: InterpreterException = try {
            val raw = interpreter.interpret(prompt, history, images)
            val parsed = parser.parse(raw)
            parsed.getOrNull()?.let { return Interpretation(raw, it, null, null) }
            InterpreterException.InvalidResponse(parsed.exceptionOrNull()?.message ?: "JSON inválido")
        } catch (e: InterpreterException) {
            e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fallo inesperado del intérprete", e)
            InterpreterException.InvalidResponse(e.message ?: e::class.simpleName.orEmpty())
        }

        Log.w(TAG, "Intérprete principal falló: ${primaryError.userMessage}")
        val fallback = fallbackInterpreter
        if (fallback == null || !primaryError.allowsFallback) {
            return Interpretation(null, null, null, primaryError.userMessage)
        }
        return try {
            val raw = fallback.interpret(plainText)
            val request = parser.parse(raw).getOrNull()
            Interpretation(raw, request, "${primaryError.userMessage} Usé el modo local.", primaryError.userMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Interpretation(null, null, null, primaryError.userMessage)
        }
    }

    /** Añade al prompt el contenido (o la descripción) del archivo adjunto. */
    private fun withAttachment(text: String, attachment: Attachment?): String {
        if (attachment == null) return text
        val header = "[Archivo adjunto: ${attachment.name} (${attachment.mimeType})]"
        val body = when {
            attachment.kind == AttachmentKind.TEXT && attachment.textContent != null -> buildString {
                append("Contenido del archivo")
                if (attachment.truncated) append(" (truncado)")
                append(":\n\"\"\"\n")
                append(attachment.textContent)
                append("\n\"\"\"")
            }
            attachment.kind == AttachmentKind.IMAGE && attachment.imageDataUrl != null ->
                "La imagen va adjunta a este mensaje; analízala."
            attachment.kind == AttachmentKind.IMAGE ->
                "No se pudo procesar la imagen adjunta."
            else ->
                "El contenido de este tipo de documento no se puede extraer como texto."
        }
        return "$text\n\n$header\n$body"
    }

    private fun titleFor(text: String): String =
        text.replaceFirstChar { it.uppercase() }.let { if (it.length > TITLE_MAX) it.take(TITLE_MAX).trimEnd() + "…" else it }

    private companion object {
        /** Acciones cuya respuesta es solo una confirmación de lo hecho. */
        val CONFIRMATION_ACTIONS = setOf(
            ActionType.OPEN_APP, ActionType.TOGGLE_FLASHLIGHT, ActionType.SET_ALARM, ActionType.SET_TIMER,
            ActionType.MEDIA_CONTROL, ActionType.GLOBAL_ACTION, ActionType.CLICK_NODE, ActionType.GESTURE,
            ActionType.SEND_MESSAGE, ActionType.CALL_CONTACT, ActionType.WHATSAPP_MESSAGE, ActionType.NAVIGATE,
            ActionType.YOUTUBE_SEARCH, ActionType.COMPOSE_EMAIL, ActionType.CALENDAR_CREATE
        )
        const val TAG = "ScorpkAssistant"
        const val TITLE_MAX = 40
        const val HISTORY_LIMIT = 10
        const val SPOKEN_MAX_CHARS = 220
    }
}
