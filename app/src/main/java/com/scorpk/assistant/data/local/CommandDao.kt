package com.scorpk.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM commands WHERE conversation_id = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeCommands(conversationId: Long): Flow<List<CommandEntity>>

    /** Últimos [limit] mensajes de la conversación, del más reciente al más antiguo. */
    @Query("SELECT * FROM commands WHERE conversation_id = :conversationId ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun recentCommands(conversationId: Long, limit: Int): List<CommandEntity>

    @Query("SELECT COUNT(*) FROM commands WHERE role = :role")
    fun observeCountByRole(role: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeConversationCount(): Flow<Int>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertCommand(command: CommandEntity): Long

    @Query("UPDATE conversations SET updated_at = :timestamp WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: Long, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Transaction
    suspend fun insertCommandAndTouch(command: CommandEntity): Long {
        val id = insertCommand(command)
        touchConversation(command.conversationId, command.timestamp)
        return id
    }
}
