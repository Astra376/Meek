package com.example.aichat.core.model

enum class CharacterVisibility {
    PUBLIC,
    PRIVATE
}

data class CharacterSummary(
    val id: String,
    val ownerUserId: String,
    val authorUsername: String = "",
    val name: String,
    val tagline: String,
    val description: String,
    val systemPrompt: String,
    val visibility: CharacterVisibility,
    val avatarUrl: String?,
    val publicChatCount: Int,
    val likeCount: Int,
    val likedByMe: Boolean,
    val lastActiveAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

data class CharacterDraft(
    val id: String? = null,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val visibility: CharacterVisibility = CharacterVisibility.PUBLIC,
    val avatarUrl: String? = null
)

data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?
)
