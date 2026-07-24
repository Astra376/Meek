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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ChatBackgroundRepository @Inject constructor(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val sceneDao: ConversationSceneDao,
    private val imageApi: ImageApi
) {
    private val generationLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun ensureInitialBackground(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            generationLock(conversationId).withLock {
                val detail = database.withTransaction {
                    val conversation = conversationDao.getById(conversationId) ?: return@withTransaction null
                    val character =
                        database.characterDao().getById(conversation.characterId) ?: return@withTransaction null
                    Pair(conversation, character)
                } ?: return@withLock
                val (conversation, character) = detail
                if (character.initialSceneUrl != null && character.initialSceneKey != null) {
                    sceneDao.upsert(
                        ConversationSceneEntity(
                            conversationId = conversation.id,
                            sceneKey = character.initialSceneKey,
                            imageUrl = character.initialSceneUrl,
                            prompt = "",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    return@withLock
                }

                val scene = ChatScenePromptBuilder.initialScene(character)
                val imageUrl = imageApi.generateChatBackground(
                    GenerateChatBackgroundRequestDto(scene.prompt)
                ).imageUrl
                database.withTransaction {
                    database.characterDao().upsert(
                        character.copy(
                            initialSceneUrl = imageUrl,
                            initialSceneKey = scene.key
                        )
                    )
                    sceneDao.upsert(
                        ConversationSceneEntity(
                            conversationId = conversation.id,
                            sceneKey = scene.key,
                            imageUrl = imageUrl,
                            prompt = scene.prompt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    suspend fun refreshIfSceneChanged(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            generationLock(conversationId).withLock {
                val detail = database.withTransaction {
                    val conversation = conversationDao.getById(conversationId) ?: return@withTransaction null
                    val character =
                        database.characterDao().getById(conversation.characterId) ?: return@withTransaction null
                    val regenerations = database.assistantRegenerationDao()
                        .getConversationRegenerations(conversationId)
                        .groupBy { it.messageId }
                    val messages = database.messageDao().getMessages(conversationId).map { entity ->
                        entity.toModel(regenerations[entity.id].orEmpty())
                    }
                    Triple(character.toModel(), messages, sceneDao.getByConversation(conversationId))
                } ?: return@withLock

                val (character, messages, existingScene) = detail
                val scene = ChatScenePromptBuilder.changedScene(character, messages) ?: return@withLock
                if (scene.key == existingScene?.sceneKey || scene.key == character.initialSceneKey) return@withLock

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

    private fun generationLock(conversationId: String): Mutex =
        generationLocks.computeIfAbsent(conversationId) { Mutex() }
}
