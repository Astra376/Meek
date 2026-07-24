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
                ensureInitialBackgroundLocked(conversationId)
            }
        }
    }

    suspend fun repairFailedBackground(
        conversationId: String,
        failedImageUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            generationLock(conversationId).withLock {
                val failedUrl = failedImageUrl.trim()
                if (failedUrl.isEmpty()) return@withLock

                val detail = database.withTransaction {
                    val conversation = conversationDao.getById(conversationId)
                        ?: return@withTransaction null
                    val character = database.characterDao().getById(conversation.characterId)
                        ?: return@withTransaction null
                    val scene = sceneDao.getByConversation(conversationId)
                    Triple(conversation, character, scene)
                } ?: return@withLock
                val (conversation, character, scene) = detail
                val sceneFailed = scene?.imageUrl == failedUrl
                val initialFailed = character.initialSceneUrl == failedUrl
                if (!sceneFailed && !initialFailed) return@withLock

                val hasUsableInitial =
                    !character.initialSceneUrl.isNullOrBlank() &&
                        !character.initialSceneKey.isNullOrBlank() &&
                        !initialFailed
                if (sceneFailed && hasUsableInitial) {
                    sceneDao.deleteByConversation(conversationId)
                    ensureInitialBackgroundLocked(conversationId)
                    return@withLock
                }

                // Do not discard the old URL until its replacement exists. A
                // temporary network/provider failure must not make the chat
                // permanently lose its last persisted background.
                val replacementScene = ChatScenePromptBuilder.initialScene(character)
                val replacementUrl = imageApi.generateChatBackground(
                    GenerateChatBackgroundRequestDto(replacementScene.prompt)
                ).imageUrl.trim()
                require(replacementUrl.isNotEmpty()) { "Image generation returned an empty URL." }
                database.withTransaction {
                    val currentCharacter = database.characterDao().getById(character.id)
                        ?: return@withTransaction
                    val currentScene = sceneDao.getByConversation(conversationId)
                    if (
                        currentCharacter.initialSceneUrl != failedUrl
                        && currentScene?.imageUrl != failedUrl
                    ) {
                        return@withTransaction
                    }
                    database.characterDao().upsert(
                        currentCharacter.copy(
                            initialSceneUrl = replacementUrl,
                            initialSceneKey = replacementScene.key
                        )
                    )
                    sceneDao.upsert(
                        ConversationSceneEntity(
                            conversationId = conversation.id,
                            sceneKey = replacementScene.key,
                            imageUrl = replacementUrl,
                            prompt = replacementScene.prompt,
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

    private suspend fun ensureInitialBackgroundLocked(conversationId: String) {
        val detail = database.withTransaction {
            val conversation = conversationDao.getById(conversationId) ?: return@withTransaction null
            val character =
                database.characterDao().getById(conversation.characterId) ?: return@withTransaction null
            Pair(conversation, character)
        } ?: return
        val (conversation, character) = detail
        if (!character.initialSceneUrl.isNullOrBlank() && !character.initialSceneKey.isNullOrBlank()) {
            sceneDao.upsert(
                ConversationSceneEntity(
                    conversationId = conversation.id,
                    sceneKey = character.initialSceneKey,
                    imageUrl = character.initialSceneUrl,
                    prompt = "",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val scene = ChatScenePromptBuilder.initialScene(character)
        val imageUrl = imageApi.generateChatBackground(
            GenerateChatBackgroundRequestDto(scene.prompt)
        ).imageUrl.trim()
        require(imageUrl.isNotEmpty()) { "Image generation returned an empty URL." }
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
