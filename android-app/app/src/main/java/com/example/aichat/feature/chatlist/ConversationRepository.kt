package com.example.aichat.feature.chatlist

import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.model.ConversationSummary
import com.example.aichat.core.util.generateId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val characterDao: CharacterDao
) {
    fun observeConversations(ownerUserId: String): Flow<List<ConversationSummary>> {
        return conversationDao.observeConversationSummaries(ownerUserId).map { rows ->
            rows.map { it.toModel() }
        }
    }

    suspend fun ensureConversation(ownerUserId: String, characterId: String): Result<String> {
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
}
