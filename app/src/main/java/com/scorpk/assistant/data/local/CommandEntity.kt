package com.scorpk.assistant.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un registro del historial: el comando del usuario (role = USER) o la respuesta de Scorpk
 * (role = ASSISTANT) junto con la acción ejecutada y su resultado.
 */
@Entity(
    tableName = "commands",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class CommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val role: String,
    val text: String,
    val action: String? = null,
    @ColumnInfo(name = "action_json")
    val actionJson: String? = null,
    val success: Boolean = true,
    val source: String,
    val timestamp: Long,
    @ColumnInfo(name = "attachment_name")
    val attachmentName: String? = null,
    @ColumnInfo(name = "attachment_mime")
    val attachmentMime: String? = null,
    @ColumnInfo(name = "attachment_uri")
    val attachmentUri: String? = null
) {
    companion object {
        const val ROLE_USER = "USER"
        const val ROLE_ASSISTANT = "ASSISTANT"
    }
}
