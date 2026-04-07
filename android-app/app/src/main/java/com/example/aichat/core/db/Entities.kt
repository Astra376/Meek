package com.example.aichat.core.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "characters",
    indices = [Index("ownerUserId"), Index("visibility"), Index("likedByMe")]
)
data class CharacterEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val authorUsername: String,
    val name: String,
    val tagline: String,
    val bio: String,
    val systemPrompt: String,
    val visibility: String,
    val avatarUrl: String?,
    val publicChatCount: Int,
    val likeCount: Int,
    val likedByMe: Boolean,
    val lastActiveAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "conversations",
    indices = [Index("ownerUserId"), Index("characterId"), Index("updatedAt")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val characterId: String,
    val version: Long,
    val updatedAt: Long,
    val startedAt: Long,
    val lastMessageAt: Long?,
    val previewText: String,
    val unreadCount: Int,
    val hasUnreadBadge: Boolean
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index(value = ["conversationId", "position"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val position: Int,
    val role: String,
    val content: String,
    val edited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedRegenerationId: String?,
    val sendState: String
)

@Entity(
    tableName = "assistant_regenerations",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class AssistantRegenerationEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val content: String,
    val createdAt: Long
)
