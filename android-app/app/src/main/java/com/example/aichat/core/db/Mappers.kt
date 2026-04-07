package com.example.aichat.core.db

import com.example.aichat.core.model.AssistantRegeneration
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.ConversationSummary
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.MessageSendState
import com.example.aichat.core.model.UserProfile
import com.example.aichat.core.network.CharacterDto

fun ProfileEntity.toModel(): UserProfile = UserProfile(
    userId = userId,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun UserProfile.toEntity(): ProfileEntity = ProfileEntity(
    userId = userId,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CharacterEntity.toModel(): CharacterSummary = CharacterSummary(
    id = id,
    ownerUserId = ownerUserId,
    authorUsername = authorUsername,
    name = name,
    tagline = tagline,
    bio = bio,
    systemPrompt = systemPrompt,
    visibility = CharacterVisibility.valueOf(visibility),
    avatarUrl = avatarUrl,
    publicChatCount = publicChatCount,
    likeCount = likeCount,
    likedByMe = likedByMe,
    lastActiveAt = lastActiveAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CharacterSummary.toEntity(): CharacterEntity = CharacterEntity(
    id = id,
    ownerUserId = ownerUserId,
    authorUsername = authorUsername,
    name = name,
    tagline = tagline,
    bio = bio,
    systemPrompt = systemPrompt,
    visibility = visibility.name,
    avatarUrl = avatarUrl,
    publicChatCount = publicChatCount,
    likeCount = likeCount,
    likedByMe = likedByMe,
    lastActiveAt = lastActiveAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CharacterDto.toEntity(): CharacterEntity = CharacterEntity(
    id = id,
    ownerUserId = ownerUserId,
    authorUsername = authorUsername,
    name = name,
    tagline = tagline,
    bio = bio,
    systemPrompt = systemPrompt,
    visibility = visibility.uppercase(),
    avatarUrl = avatarUrl,
    publicChatCount = publicChatCount,
    likeCount = likeCount,
    likedByMe = likedByMe,
    lastActiveAt = lastActiveAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MessageEntity.toModel(regenerations: List<AssistantRegenerationEntity>): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    position = position,
    role = MessageRole.valueOf(role),
    content = content,
    edited = edited,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedRegenerationId = selectedRegenerationId,
    sendState = MessageSendState.valueOf(sendState),
    regenerations = regenerations.map { it.toModel() }
)

fun AssistantRegenerationEntity.toModel(): AssistantRegeneration = AssistantRegeneration(
    id = id,
    messageId = messageId,
    content = content,
    createdAt = createdAt
)

fun ConversationSummaryRow.toModel(): ConversationSummary = ConversationSummary(
    id = id,
    characterId = characterId,
    characterName = characterName,
    characterAvatarUrl = characterAvatarUrl,
    updatedAt = updatedAt,
    startedAt = startedAt,
    lastMessageAt = lastMessageAt,
    lastPreview = lastPreview,
    unreadCount = unreadCount,
    hasUnreadBadge = hasUnreadBadge
)
