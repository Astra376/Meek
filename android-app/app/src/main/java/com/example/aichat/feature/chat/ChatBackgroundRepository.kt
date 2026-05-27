package com.example.aichat.feature.chat

import androidx.room.withTransaction
import com.example.aichat.core.db.AppDatabase
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationSceneDao
import com.example.aichat.core.db.ConversationSceneEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.network.GenerateChatBackgroundRequestDto
import com.example.aichat.core.network.ImageApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ChatBackgroundRepository @Inject constructor(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val sceneDao: ConversationSceneDao,
    private val imageApi: ImageApi
) {
    suspend fun ensureInitialBackground(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val character = database.withTransaction {
                val conversation = conversationDao.getById(conversationId) ?: return@withTransaction null
                database.characterDao().getById(conversation.characterId)
            } ?: return@runCatching
            if (character.initialSceneUrl != null && character.initialSceneKey != null) return@runCatching

            val scene = ChatScenePromptBuilder.initialScene(character)
            val imageUrl = imageApi.generateChatBackground(
                GenerateChatBackgroundRequestDto(scene.prompt)
            ).imageUrl
            database.characterDao().upsert(
                character.copy(
                    initialSceneUrl = imageUrl,
                    initialSceneKey = scene.key,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun refreshIfSceneChanged(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val detail = database.withTransaction {
                val conversation = conversationDao.getById(conversationId) ?: return@withTransaction null
                val character = database.characterDao().getById(conversation.characterId) ?: return@withTransaction null
                val regenerations = database.assistantRegenerationDao()
                    .getConversationRegenerations(conversationId)
                    .groupBy { it.messageId }
                val messages = database.messageDao().getMessages(conversationId).map { entity ->
                    entity.toModel(regenerations[entity.id].orEmpty())
                }
                Triple(character.toModel(), messages, sceneDao.getByConversation(conversationId))
            } ?: return@runCatching

            val (character, messages, existingScene) = detail
            val scene = ChatScenePromptBuilder.changedScene(character, messages) ?: return@runCatching
            if (scene.key == existingScene?.sceneKey || scene.key == character.initialSceneKey) return@runCatching

            val imageUrl = imageApi.generateChatBackground(
                GenerateChatBackgroundRequestDto(scene.prompt)
            ).imageUrl
            sceneDao.upsert(
                ConversationSceneEntity(
                    conversationId = conversationId,
                    sceneKey = scene.key,
                    imageUrl = imageUrl,
                    prompt = scene.prompt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
