package com.example.aichat.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String
)

@Serializable
data class SessionResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("expiresAt") val expiresAt: Long
)

@Serializable
data class GoogleAuthRequestDto(
    @SerialName("idToken") val idToken: String
)

@Serializable
data class RefreshSessionRequestDto(
    @SerialName("refreshToken") val refreshToken: String
)

@Serializable
data class ProfileDto(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class UpdateProfileRequestDto(
    val displayName: String
)

@Serializable
data class CharacterDto(
    val id: String,
    val ownerUserId: String,
    val name: String,
    val tagline: String,
    val description: String,
    val systemPrompt: String,
    val visibility: String,
    val avatarUrl: String? = null,
    val publicChatCount: Int,
    val likeCount: Int,
    val likedByMe: Boolean,
    val lastActiveAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CharacterWriteRequestDto(
    val name: String,
    val tagline: String,
    val description: String,
    val systemPrompt: String,
    val visibility: String,
    val avatarUrl: String? = null
)

@Serializable
data class ConversationSummaryDto(
    val id: String,
    val characterId: String,
    val characterName: String,
    val characterAvatarUrl: String? = null,
    val updatedAt: Long,
    val startedAt: Long,
    val lastMessageAt: Long? = null,
    val lastPreview: String
)

@Serializable
data class AssistantRegenerationDto(
    val id: String,
    val messageId: String,
    val content: String,
    val createdAt: Long
)

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val position: Int,
    val role: String,
    val content: String,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedRegenerationId: String? = null,
    val regenerations: List<AssistantRegenerationDto> = emptyList()
)

@Serializable
data class ConversationDetailDto(
    val id: String,
    val ownerUserId: String,
    val conversationVersion: Long,
    val character: CharacterDto,
    val messages: List<MessageDto>
)

@Serializable
data class SendMessageRequestDto(
    val userMessageId: String,
    val content: String
)

@Serializable
data class EditMessageRequestDto(
    val content: String
)

@Serializable
data class SelectRegenerationRequestDto(
    val regenerationId: String
)

@Serializable
data class GeneratePortraitRequestDto(
    val prompt: String
)

@Serializable
data class GeneratePortraitResponseDto(
    val avatarUrl: String
)

@Serializable
data class StreamEventDto(
    val type: String,
    val runId: String? = null,
    val conversationVersion: Long? = null,
    val userMessage: MessageDto? = null,
    val assistantMessageId: String? = null,
    val textDelta: String? = null,
    val assistantMessage: MessageDto? = null,
    val messageId: String? = null,
    val regeneration: AssistantRegenerationDto? = null,
    val selectedRegenerationId: String? = null,
    val conversationSummary: ConversationSummaryDto? = null,
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class CursorPageDto<T>(
    val items: List<T>,
    val nextCursor: String? = null
)
