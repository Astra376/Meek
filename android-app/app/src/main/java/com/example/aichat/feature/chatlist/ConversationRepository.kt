package com.example.aichat.feature.chatlist

import com.example.aichat.BuildConfig
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.CharacterEntity
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.model.ConversationSummary
import com.example.aichat.core.network.ConversationApi
import com.example.aichat.core.network.ConversationSummaryDto
import com.example.aichat.core.util.generateId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val characterDao: CharacterDao,
    private val conversationApi: ConversationApi
) {
    fun observeConversations(ownerUserId: String): Flow<List<ConversationSummary>> {
        return conversationDao.observeConversationSummaries(ownerUserId).map { rows ->
            rows.map { it.toModel() }
        }
    }

    suspend fun refreshConversations(ownerUserId: String): Result<Unit> {
        if (BuildConfig.USE_MOCK_SERVICES) return Result.success(Unit)
        return runCatching {
            val page = conversationApi.getConversations()
            page.items.forEach { summary ->
                upsertConversationSummary(ownerUserId, summary)
            }
        }
    }

    suspend fun ensureConversation(ownerUserId: String, characterId: String): Result<String> {
        if (!BuildConfig.USE_MOCK_SERVICES) {
            return runCatching {
                val summary = conversationApi.createConversation(mapOf("characterId" to characterId))
                upsertConversationSummary(ownerUserId, summary)
                summary.id
            }
        }

        val character = characterDao.getById(characterId)
            ?: return Result.failure(IllegalArgumentException("Character not found."))
        if (character.visibility == CharacterVisibility.PRIVATE.name && character.ownerUserId != ownerUserId) {
            return Result.failure(IllegalStateException("Private characters are only available to their owner."))
        }
        val existing = conversationDao.findByOwnerAndCharacter(ownerUserId, characterId)
        if (existing != null) return Result.success(existing.id)

        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = generateId("conversation"),
            ownerUserId = ownerUserId,
            characterId = characterId,
            updatedAt = now,
            startedAt = now,
            lastMessageAt = null,
            previewText = "New conversation"
        )
        conversationDao.upsert(conversation)
        return Result.success(conversation.id)
    }

    private suspend fun upsertConversationSummary(ownerUserId: String, summary: ConversationSummaryDto) {
        val existingCharacter = characterDao.getById(summary.characterId)
        characterDao.upsert(
            CharacterEntity(
                id = summary.characterId,
                ownerUserId = existingCharacter?.ownerUserId ?: "",
                name = summary.characterName,
                tagline = existingCharacter?.tagline ?: "",
                description = existingCharacter?.description ?: "",
                systemPrompt = existingCharacter?.systemPrompt ?: "",
                visibility = existingCharacter?.visibility ?: CharacterVisibility.PUBLIC.name,
                avatarUrl = summary.characterAvatarUrl ?: existingCharacter?.avatarUrl,
                publicChatCount = existingCharacter?.publicChatCount ?: 0,
                likeCount = existingCharacter?.likeCount ?: 0,
                likedByMe = existingCharacter?.likedByMe ?: false,
                lastActiveAt = existingCharacter?.lastActiveAt ?: (summary.lastMessageAt ?: summary.updatedAt),
                createdAt = existingCharacter?.createdAt ?: summary.startedAt,
                updatedAt = maxOf(existingCharacter?.updatedAt ?: 0L, summary.updatedAt)
            )
        )
        conversationDao.upsert(
            ConversationEntity(
                id = summary.id,
                ownerUserId = ownerUserId,
                characterId = summary.characterId,
                updatedAt = summary.updatedAt,
                startedAt = summary.startedAt,
                lastMessageAt = summary.lastMessageAt,
                previewText = summary.lastPreview
            )
        )
    }
}
