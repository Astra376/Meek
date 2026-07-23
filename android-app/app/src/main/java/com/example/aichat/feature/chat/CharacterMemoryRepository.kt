package com.example.aichat.feature.chat

import com.example.aichat.core.db.ConversationMemoryDao
import com.example.aichat.core.db.ConversationMemoryEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterMemory
import com.example.aichat.core.network.CharacterMemoryDto
import com.example.aichat.core.network.ConversationApi
import com.example.aichat.core.network.UpdateCharacterMemoryRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class CharacterMemoryRepository @Inject constructor(
    private val memoryDao: ConversationMemoryDao,
    private val conversationApi: ConversationApi
) {
    fun observeMemory(conversationId: String): Flow<CharacterMemory?> {
        return memoryDao.observeByConversation(conversationId).map { it?.toModel() }
    }

    suspend fun refresh(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            memoryDao.upsert(conversationApi.getCharacterMemory(conversationId).toEntity())
        }
    }

    suspend fun save(
        conversationId: String,
        shortTerm: String,
        longTerm: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(shortTerm.length <= SHORT_TERM_MEMORY_MAX_LENGTH) {
                "Short-term memory is too long."
            }
            require(longTerm.length <= LONG_TERM_MEMORY_MAX_LENGTH) {
                "Long-term memory is too long."
            }
            val saved = conversationApi.updateCharacterMemory(
                conversationId,
                UpdateCharacterMemoryRequestDto(
                    shortTerm = shortTerm.trim(),
                    longTerm = longTerm.trim()
                )
            )
            memoryDao.upsert(saved.toEntity())
        }
    }

    private fun CharacterMemoryDto.toEntity() = ConversationMemoryEntity(
        conversationId = conversationId,
        shortTerm = shortTerm,
        longTerm = longTerm,
        updatedAt = updatedAt
    )
}

const val SHORT_TERM_MEMORY_MAX_LENGTH = 8_000
const val LONG_TERM_MEMORY_MAX_LENGTH = 64_000
