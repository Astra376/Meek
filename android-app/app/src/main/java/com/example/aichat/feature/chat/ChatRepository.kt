package com.example.aichat.feature.chat

import com.example.aichat.core.db.AssistantRegenerationDao
import com.example.aichat.core.db.AssistantRegenerationEntity
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationEntity
import com.example.aichat.core.db.MessageDao
import com.example.aichat.core.db.MessageEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.ConversationDetail
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.StreamingDraft
import com.example.aichat.core.util.generateId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val characterDao: CharacterDao,
    private val messageDao: MessageDao,
    private val regenerationDao: AssistantRegenerationDao,
    private val responder: MockAssistantResponder
) {
    private val streamingDrafts = MutableStateFlow<Map<String, StreamingDraft>>(emptyMap())

    fun observeConversation(conversationId: String): Flow<ConversationDetail?> {
        return combine(
            conversationDao.observeById(conversationId),
            messageDao.observeMessages(conversationId),
            regenerationDao.observeConversationRegenerations(conversationId)
        ) { conversation, messages, regenerations ->
            if (conversation == null) return@combine null
            val character = characterDao.getById(conversation.characterId) ?: return@combine null
            val groupedRegenerations = regenerations.groupBy { it.messageId }
            ConversationDetail(
                id = conversation.id,
                character = character.toModel(),
                messages = messages.map { message ->
                    message.toModel(groupedRegenerations[message.id].orEmpty())
                }
            )
        }
    }

    fun observeStreamingDraft(conversationId: String): Flow<StreamingDraft?> {
        return streamingDrafts.map { drafts -> drafts[conversationId] }
    }

    suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (text.isBlank()) throw IllegalArgumentException("Message can't be empty.")
            val conversation = conversationDao.getById(conversationId)
                ?: throw IllegalArgumentException("Conversation not found.")
            ensureNotStreaming(conversationId)

            val character = characterDao.getById(conversation.characterId)?.toModel()
                ?: throw IllegalStateException("Character not found.")
            val existingMessages = snapshot(conversationId)
            val nextPosition = (existingMessages.maxByOrNull { it.position }?.position ?: -1) + 1
            val now = System.currentTimeMillis()
            val userMessage = MessageEntity(
                id = generateId("message"),
                conversationId = conversationId,
                position = nextPosition,
                role = MessageRole.USER.name,
                content = text.trim(),
                edited = false,
                createdAt = now,
                updatedAt = now,
                selectedRegenerationId = null
            )
            messageDao.insert(userMessage)
            updateConversationMetadata(conversation, preview = text.trim(), now = now)

            val transcript = snapshot(conversationId)
            streamIntoDraft(
                conversationId = conversationId,
                anchorMessageId = userMessage.id,
                source = responder.streamReply(character, transcript, ReplyMode.REPLY),
                onCompleted = { fullText ->
                    val assistantNow = System.currentTimeMillis()
                    messageDao.insert(
                        MessageEntity(
                            id = generateId("message"),
                            conversationId = conversationId,
                            position = nextPosition + 1,
                            role = MessageRole.ASSISTANT.name,
                            content = fullText,
                            edited = false,
                            createdAt = assistantNow,
                            updatedAt = assistantNow,
                            selectedRegenerationId = null
                        )
                    )
                    characterDao.upsert(
                        characterDao.getById(character.id)!!.copy(
                            publicChatCount = character.publicChatCount + 1,
                            lastActiveAt = assistantNow,
                            updatedAt = assistantNow
                        )
                    )
                    updateConversationMetadata(conversation, preview = fullText, now = assistantNow)
                }
            ).getOrThrow()
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw error
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureNotStreaming(message.conversationId)
            val messages = snapshot(message.conversationId)
            val updated = ChatTranscriptRules.edit(messages, messageId, newContent, System.currentTimeMillis())
            val updatedMessage = updated.first { it.id == messageId }

            if (message.role == MessageRole.ASSISTANT.name && updatedMessage.selectedRegenerationId != null) {
                val regeneration = updatedMessage.regenerations.first { it.id == updatedMessage.selectedRegenerationId }
                regenerationDao.insert(
                    AssistantRegenerationEntity(
                        id = regeneration.id,
                        messageId = regeneration.messageId,
                        content = regeneration.content,
                        createdAt = regeneration.createdAt
                    )
                )
                messageDao.update(message.copy(edited = true, updatedAt = updatedMessage.updatedAt))
            } else {
                messageDao.update(
                    message.copy(
                        content = updatedMessage.content,
                        edited = true,
                        updatedAt = updatedMessage.updatedAt
                    )
                )
            }
            refreshConversationPreview(message.conversationId)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun rewind(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureNotStreaming(message.conversationId)
            val messages = snapshot(message.conversationId)
            val remaining = ChatTranscriptRules.rewind(messages, messageId)
            val lastRemaining = remaining.lastOrNull()
            messageDao.deleteAfter(message.conversationId, remaining.maxByOrNull { it.position }?.position ?: -1)
            val conversation = conversationDao.getById(message.conversationId)!!
            updateConversationMetadata(
                conversation = conversation,
                preview = lastRemaining?.visibleContent ?: "Conversation rewound",
                now = System.currentTimeMillis()
            )
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun regenerateLatestAssistant(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureNotStreaming(message.conversationId)
            val conversation = conversationDao.getById(message.conversationId)
                ?: throw IllegalArgumentException("Conversation not found.")
            val messages = snapshot(message.conversationId)
            val target = ChatTranscriptRules.regenerateTarget(messages, messageId)
            val character = characterDao.getById(conversation.characterId)?.toModel()
                ?: throw IllegalStateException("Character not found.")
            val context = messages.filter { it.position < target.position }

            streamIntoDraft(
                conversationId = message.conversationId,
                anchorMessageId = messageId,
                source = responder.streamReply(character, context, ReplyMode.REGENERATE),
                onCompleted = { fullText ->
                    val regenerationId = generateId("regen")
                    regenerationDao.insert(
                        AssistantRegenerationEntity(
                            id = regenerationId,
                            messageId = target.id,
                            content = fullText,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    messageDao.update(message.copy(selectedRegenerationId = regenerationId, updatedAt = System.currentTimeMillis()))
                    updateConversationMetadata(conversation, preview = fullText, now = System.currentTimeMillis())
                }
            ).getOrThrow()
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw error
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun selectRegeneration(messageId: String, regenerationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureNotStreaming(message.conversationId)
            val messages = snapshot(message.conversationId)
            ChatTranscriptRules.selectRegeneration(messages, messageId, regenerationId)
            messageDao.update(message.copy(selectedRegenerationId = regenerationId, updatedAt = System.currentTimeMillis()))
            refreshConversationPreview(message.conversationId)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun streamIntoDraft(
        conversationId: String,
        anchorMessageId: String?,
        source: Flow<String>,
        onCompleted: suspend (String) -> Unit
    ): Result<Unit> {
        var buffer = ""
        setDraft(conversationId, StreamingDraft(conversationId, anchorMessageId, ""))
        return try {
            source.collect { chunk ->
                buffer += chunk
                setDraft(conversationId, StreamingDraft(conversationId, anchorMessageId, buffer))
            }
            onCompleted(buffer.trim())
            Result.success(Unit)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            clearDraft(conversationId)
        }
    }

    private suspend fun snapshot(conversationId: String): List<ChatMessage> {
        val messages = messageDao.getMessages(conversationId)
        return messages.map { message ->
            message.toModel(regenerationDao.getByMessage(message.id))
        }
    }

    private fun setDraft(conversationId: String, draft: StreamingDraft) {
        streamingDrafts.update { it + (conversationId to draft) }
    }

    private fun clearDraft(conversationId: String) {
        streamingDrafts.update { it - conversationId }
    }

    private fun ensureNotStreaming(conversationId: String) {
        if (streamingDrafts.value.containsKey(conversationId)) {
            throw ChatRuleViolation("Wait for the current reply to finish before changing the transcript.")
        }
    }

    private suspend fun refreshConversationPreview(conversationId: String) {
        val conversation = conversationDao.getById(conversationId) ?: return
        val latest = snapshot(conversationId).lastOrNull()
        updateConversationMetadata(
            conversation = conversation,
            preview = latest?.visibleContent ?: "Conversation updated",
            now = System.currentTimeMillis()
        )
    }

    private suspend fun updateConversationMetadata(conversation: ConversationEntity, preview: String, now: Long) {
        conversationDao.upsert(
            conversation.copy(
                updatedAt = now,
                lastMessageAt = now,
                previewText = preview
            )
        )
    }
}
