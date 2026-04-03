package com.example.aichat.core.model

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class MessageSendState {
    PENDING,
    SENT,
    FAILED
}

data class AssistantRegeneration(
    val id: String,
    val messageId: String,
    val content: String,
    val createdAt: Long
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val position: Int,
    val role: MessageRole,
    val content: String,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedRegenerationId: String?,
    val sendState: MessageSendState = MessageSendState.SENT,
    val regenerations: List<AssistantRegeneration> = emptyList()
) {
    val visibleContent: String
        get() = regenerations.firstOrNull { it.id == selectedRegenerationId }?.content ?: content
}

data class ConversationSummary(
    val id: String,
    val characterId: String,
    val characterName: String,
    val characterAvatarUrl: String?,
    val updatedAt: Long,
    val startedAt: Long,
    val lastMessageAt: Long?,
    val lastPreview: String
)

data class ConversationDetail(
    val id: String,
    val ownerUserId: String,
    val conversationVersion: Long,
    val character: CharacterSummary,
    val messages: List<ChatMessage>
)
