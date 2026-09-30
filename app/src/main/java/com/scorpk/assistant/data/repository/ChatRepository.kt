package com.scorpk.assistant.data.repository

import com.scorpk.assistant.data.local.CommandDao
import com.scorpk.assistant.data.local.CommandEntity
import com.scorpk.assistant.data.local.ConversationEntity
import com.scorpk.assistant.domain.AssistantText
import com.scorpk.assistant.domain.interpreter.ChatTurn
import com.scorpk.assistant.domain.model.Attachment
import com.scorpk.assistant.domain.model.ChatMessage
import com.scorpk.assistant.domain.model.CommandSource
import com.scorpk.assistant.domain.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatRepository(private val dao: CommandDao) {

    val conversations: Flow<List<ConversationEntity>> =
        dao.observeConversations().flowOn(Dispatchers.IO)

    val conversationCount: Flow<Int> = dao.observeConversationCount().flowOn(Dispatchers.IO)

    val userCommandCount: Flow<Int> =
        dao.observeCountByRole(CommandEntity.ROLE_USER).flowOn(Dispatchers.IO)

    fun messages(conversationId: Long): Flow<List<ChatMessage>> =
        dao.observeCommands(conversationId)
            .map { list -> list.map { it.toChatMessage() } }
            .flowOn(Dispatchers.IO)

    suspend fun createConversation(title: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insertConversation(
            ConversationEntity(title = title, createdAt = now, updatedAt = now)
        )
    }

    suspend fun addUserCommand(
        conversationId: Long,
        text: String,
        source: CommandSource,
        attachment: Attachment? = null
    ): Long = withContext(Dispatchers.IO) {
        dao.insertCommandAndTouch(
            CommandEntity(
                conversationId = conversationId,
                role = CommandEntity.ROLE_USER,
                text = text,
                source = source.name,
                timestamp = System.currentTimeMillis(),
                attachmentName = attachment?.name,
                attachmentMime = attachment?.mimeType,
                attachmentUri = attachment?.uri
            )
        )
    }

    /** Turnos previos de la conversación (del más antiguo al más reciente) para dar contexto al LLM. */
    suspend fun recentTurns(conversationId: Long, limit: Int, excludeId: Long? = null): List<ChatTurn> =
        withContext(Dispatchers.IO) {
            dao.recentCommands(conversationId, limit + 1)
                .filter { it.id != excludeId }
                .take(limit)
                .reversed()
                .map { entity ->
                    val role = if (entity.role == CommandEntity.ROLE_USER) MessageRole.USER else MessageRole.ASSISTANT
                    val text = when {
                        // Datos personales de conectores: se muestran al usuario pero no viajan al LLM.
                        entity.role == CommandEntity.ROLE_ASSISTANT && entity.action in PRIVATE_RESULT_ACTIONS ->
                            "(Resultado de ${entity.action}: no se comparte con la IA.)"
                        entity.attachmentName != null -> "${entity.text}\n[Adjunto: ${entity.attachmentName}]"
                        else -> AssistantText.body(entity.text)
                    }
                    ChatTurn(role, text)
                }
        }

    suspend fun addAssistantResponse(
        conversationId: Long,
        text: String,
        action: String?,
        actionJson: String?,
        success: Boolean,
        source: CommandSource
    ): Long = withContext(Dispatchers.IO) {
        dao.insertCommandAndTouch(
            CommandEntity(
                conversationId = conversationId,
                role = CommandEntity.ROLE_ASSISTANT,
                text = text,
                action = action,
                actionJson = actionJson,
                success = success,
                source = source.name,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        dao.deleteConversation(conversationId)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.deleteAllConversations()
    }

    private companion object {
        /** Acciones cuyo resultado contiene datos personales de una cuenta o del dispositivo. */
        val PRIVATE_RESULT_ACTIONS = setOf("gmail_inbox", "drive_search", "github_query", "calendar_query")
    }

    private fun CommandEntity.toChatMessage() = ChatMessage(
        id = id,
        role = if (role == CommandEntity.ROLE_USER) MessageRole.USER else MessageRole.ASSISTANT,
        text = text,
        action = action,
        success = success,
        source = runCatching { CommandSource.valueOf(source) }.getOrDefault(CommandSource.TEXT),
        timestamp = timestamp,
        attachmentName = attachmentName,
        attachmentMime = attachmentMime
    )
}
