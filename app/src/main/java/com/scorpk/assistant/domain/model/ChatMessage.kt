package com.scorpk.assistant.domain.model

enum class MessageRole { USER, ASSISTANT }

enum class CommandSource { TEXT, VOICE, WAKE_WORD }

data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val action: String?,
    val success: Boolean,
    val source: CommandSource,
    val timestamp: Long,
    val attachmentName: String? = null,
    val attachmentMime: String? = null
)
