package com.example.aichat.feature.chat

import androidx.room.withTransaction
import com.example.aichat.core.db.AppDatabase
import com.example.aichat.core.db.AssistantRegenerationDao
import com.example.aichat.core.db.AssistantRegenerationEntity
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.MessageDao
import com.example.aichat.core.db.MessageEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.ConversationDetail
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.MessageSendState
import com.example.aichat.core.network.ChatApi
import com.example.aichat.core.network.ChatStreamEvent
import com.example.aichat.core.network.ChatStreamingClient
import com.example.aichat.core.network.ConversationSummaryDto
import com.example.aichat.core.network.EditMessageRequestDto
import com.example.aichat.core.network.MessageDto
import com.example.aichat.core.network.SelectRegenerationRequestDto
import com.example.aichat.core.util.generateUlid
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

private class StreamFailedException(
    override val message: String
) : IllegalStateException(message)

class SendMessageFailedException(
    val accepted: Boolean,
    override val message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

private class ChatRuleViolation(message: String) : IllegalStateException(message)

private suspend inline fun <T> captureResult(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

@Singleton
class ChatRepository @Inject constructor(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val characterDao: CharacterDao,
    private val messageDao: MessageDao,
    private val regenerationDao: AssistantRegenerationDao,
    private val chatApi: ChatApi,
    private val streamingClient: ChatStreamingClient
) {
    private val activeStreams = MutableStateFlow<Map<String, ActiveAssistantStream>>(emptyMap())

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
                ownerUserId = conversation.ownerUserId,
                conversationVersion = conversation.version,
                character = character.toModel(),
                messages = messages.map { message ->
                    message.toModel(groupedRegenerations[message.id].orEmpty())
                }
            )
        }
    }

    fun observeActiveStream(conversationId: String): Flow<ActiveAssistantStream?> {
        return activeStreams.map { streams -> streams[conversationId] }
    }

    suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        captureResult {
            val normalized = text.trim()
            if (normalized.isBlank()) throw IllegalArgumentException("Message can't be empty.")
            ensureNotStreaming(conversationId)

            val now = System.currentTimeMillis()
            val userMessageId = generateUlid(now)
            messageDao.insert(
                MessageEntity(
                    id = userMessageId,
                    conversationId = conversationId,
                    position = nextTemporaryPosition(conversationId),
                    role = MessageRole.USER.name,
                    content = normalized,
                    edited = false,
                    createdAt = now,
                    updatedAt = now,
                    selectedRegenerationId = null,
                    sendState = MessageSendState.PENDING.name
                )
            )
            setActiveStream(
                conversationId,
                ActiveAssistantStream(
                    conversationId = conversationId,
                    draftKey = "send-draft-$userMessageId",
                    mode = ActiveStreamMode.SEND,
                    userMessageId = userMessageId
                )
            )

            consumeSendStream(conversationId, userMessageId, normalized)
        }
    }

    suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        captureResult {
            val normalized = newContent.trim()
            if (normalized.isBlank()) throw IllegalArgumentException("Message can't be empty.")

            val message = requireMutableMessage(messageId)
            chatApi.editMessage(messageId, EditMessageRequestDto(normalized))

            database.withTransaction {
                applyLocalEdit(message, normalized)
                updateConversationMetadataFromTranscript(message.conversationId)
            }
        }
    }

    suspend fun rewind(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        captureResult {
            val message = requireMutableMessage(messageId)
            chatApi.rewind(messageId)

            database.withTransaction {
                messageDao.deleteAfter(message.conversationId, message.position)
                deleteLocalOnlyMessages(message.conversationId)
                updateConversationMetadataFromTranscript(message.conversationId)
            }
        }
    }

    suspend fun regenerateLatestAssistant(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        captureResult {
            val message = requireMutableMessage(messageId)
            ensureLatestAssistant(message)

            setActiveStream(
                message.conversationId,
                ActiveAssistantStream(
                    conversationId = message.conversationId,
                    draftKey = "regenerate-draft-$messageId",
                    mode = ActiveStreamMode.REGENERATE,
                    assistantMessageId = messageId,
                    targetMessageId = messageId
                )
            )

            consumeRegenerateStream(message.conversationId, messageId)
        }
    }

    suspend fun selectRegeneration(messageId: String, regenerationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        captureResult {
            val message = requireMutableMessage(messageId)
            ensureLatestAssistant(message)
            chatApi.selectRegeneration(messageId, SelectRegenerationRequestDto(regenerationId))

            database.withTransaction {
                messageDao.update(
                    message.copy(
                        selectedRegenerationId = regenerationId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                updateConversationMetadataFromTranscript(message.conversationId)
            }
        }
    }

    private suspend fun consumeSendStream(conversationId: String, userMessageId: String, content: String) {
        var accepted = false

        try {
            streamingClient.sendMessage(conversationId, userMessageId, content).collect { event ->
                when (event) {
                    is ChatStreamEvent.AcceptedSend -> {
                        if (event.userMessage.id != userMessageId) return@collect
                        accepted = true
                        applyAcceptedSend(conversationId, event)
                    }

                    is ChatStreamEvent.Delta -> {
                        val active = currentActiveStream(conversationId) ?: return@collect
                        if (active.runId != null && active.runId != event.runId) return@collect
                        updateActiveStream(conversationId) { stream ->
                            stream?.copy(text = stream.text + event.textDelta)
                        }
                    }

                    is ChatStreamEvent.CompletedSend -> {
                        val active = currentActiveStream(conversationId) ?: return@collect
                        if (active.runId != event.runId) return@collect
                        applyCompletedSend(conversationId, event)
                    }

                    is ChatStreamEvent.Failed -> {
                        val active = currentActiveStream(conversationId)
                        if (event.runId != null && active?.runId != event.runId) return@collect
                        throw StreamFailedException(event.message)
                    }

                    else -> Unit
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!accepted) {
                markMessageFailed(userMessageId)
            }
            throw SendMessageFailedException(
                accepted = accepted,
                message = error.message ?: "Message send failed.",
                cause = error
            )
        } finally {
            clearActiveStream(conversationId)
        }
    }

    private suspend fun consumeRegenerateStream(conversationId: String, messageId: String) {
        try {
            streamingClient.regenerateLatestAssistant(messageId).collect { event ->
                when (event) {
                    is ChatStreamEvent.AcceptedRegenerate -> {
                        if (event.messageId != messageId) return@collect
                        updateActiveStream(conversationId) { stream ->
                            stream?.copy(
                                runId = event.runId,
                                assistantMessageId = event.assistantMessageId,
                                targetMessageId = event.messageId,
                                accepted = true
                            )
                        }
                    }

                    is ChatStreamEvent.Delta -> {
                        val active = currentActiveStream(conversationId) ?: return@collect
                        if (active.runId != null && active.runId != event.runId) return@collect
                        updateActiveStream(conversationId) { stream ->
                            stream?.copy(text = stream.text + event.textDelta)
                        }
                    }

                    is ChatStreamEvent.CompletedRegenerate -> {
                        val active = currentActiveStream(conversationId) ?: return@collect
                        if (active.runId != event.runId) return@collect
                        applyCompletedRegenerate(conversationId, event)
                    }

                    is ChatStreamEvent.Failed -> {
                        val active = currentActiveStream(conversationId)
                        if (event.runId != null && active?.runId != event.runId) return@collect
                        throw StreamFailedException(event.message)
                    }

                    else -> Unit
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw error
        } finally {
            clearActiveStream(conversationId)
        }
    }

    private suspend fun applyAcceptedSend(conversationId: String, event: ChatStreamEvent.AcceptedSend) {
        upsertMessageFromDto(event.userMessage, sendState = MessageSendState.SENT)
        updateActiveStream(conversationId) { stream ->
            stream?.copy(
                runId = event.runId,
                assistantMessageId = event.assistantMessageId,
                accepted = true
            )
        }
    }

    private suspend fun applyCompletedSend(conversationId: String, event: ChatStreamEvent.CompletedSend) {
        database.withTransaction {
            upsertMessageFromDto(event.assistantMessage, sendState = MessageSendState.SENT)
            updateConversationMetadataFromSummary(conversationId, event.conversationSummary, event.conversationVersion)
        }
    }

    private suspend fun applyCompletedRegenerate(conversationId: String, event: ChatStreamEvent.CompletedRegenerate) {
        database.withTransaction {
            regenerationDao.insert(
                AssistantRegenerationEntity(
                    id = event.regeneration.id,
                    messageId = event.regeneration.messageId,
                    content = event.regeneration.content,
                    createdAt = event.regeneration.createdAt
                )
            )
            val message = messageDao.getById(event.messageId)
                ?: throw IllegalStateException("Message not found.")
            messageDao.update(
                message.copy(
                    selectedRegenerationId = event.selectedRegenerationId,
                    updatedAt = System.currentTimeMillis()
                )
            )
            updateConversationMetadataFromSummary(conversationId, event.conversationSummary, event.conversationVersion)
        }
        updateActiveStream(conversationId) { stream ->
            stream?.copy(committedRegenerationId = event.selectedRegenerationId)
        }
    }

    private suspend fun markMessageFailed(messageId: String) {
        val message = messageDao.getById(messageId) ?: return
        messageDao.update(
            message.copy(
                sendState = MessageSendState.FAILED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun upsertMessageFromDto(message: MessageDto, sendState: MessageSendState) {
        messageDao.insert(
            MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                position = message.position,
                role = message.role.uppercase(),
                content = message.content,
                edited = message.edited,
                createdAt = message.createdAt,
                updatedAt = message.updatedAt,
                selectedRegenerationId = message.selectedRegenerationId,
                sendState = sendState.name
            )
        )
    }

    private suspend fun applyLocalEdit(message: MessageEntity, newContent: String) {
        val now = System.currentTimeMillis()
        if (message.role == MessageRole.ASSISTANT.name && message.selectedRegenerationId != null) {
            val regeneration = regenerationDao.getById(message.selectedRegenerationId)
                ?: throw IllegalStateException("Selected regeneration not found.")
            regenerationDao.insert(
                regeneration.copy(
                    content = newContent,
                    createdAt = regeneration.createdAt
                )
            )
            messageDao.update(message.copy(edited = true, updatedAt = now))
            return
        }

        messageDao.update(
            message.copy(
                content = newContent,
                edited = true,
                updatedAt = now
            )
        )
    }

    private suspend fun updateConversationMetadataFromSummary(
        conversationId: String,
        summary: ConversationSummaryDto,
        conversationVersion: Long
    ) {
        val conversation = conversationDao.getById(conversationId) ?: return
        conversationDao.upsert(
            conversation.copy(
                version = conversationVersion,
                updatedAt = summary.updatedAt,
                lastMessageAt = summary.lastMessageAt,
                previewText = summary.lastPreview
            )
        )
    }

    private suspend fun updateConversationMetadataFromTranscript(conversationId: String) {
        val conversation = conversationDao.getById(conversationId) ?: return
        val latestMessage = messageDao.getLatestMessage(conversationId)
        val now = System.currentTimeMillis()
        val preview = if (latestMessage == null) "" else visibleContent(latestMessage)
        conversationDao.upsert(
            conversation.copy(
                version = conversation.version + 1,
                updatedAt = now,
                lastMessageAt = if (latestMessage == null) null else now,
                previewText = preview
            )
        )
    }

    private suspend fun visibleContent(message: MessageEntity): String {
        val selectedId = message.selectedRegenerationId ?: return message.content
        return regenerationDao.getById(selectedId)?.content ?: message.content
    }

    private suspend fun deleteLocalOnlyMessages(conversationId: String) {
        messageDao.getLocalOnlyMessages(conversationId).forEach { localMessage ->
            messageDao.deleteById(localMessage.id)
        }
    }

    private suspend fun nextTemporaryPosition(conversationId: String): Int {
        val minimumPosition = messageDao.getMinimumPosition(conversationId)
        return minOf(minimumPosition, 0) - 1
    }

    private fun setActiveStream(conversationId: String, stream: ActiveAssistantStream) {
        activeStreams.update { it + (conversationId to stream) }
    }

    private fun updateActiveStream(conversationId: String, transform: (ActiveAssistantStream?) -> ActiveAssistantStream?) {
        activeStreams.update { streams ->
            val updated = transform(streams[conversationId])
            if (updated == null) {
                streams - conversationId
            } else {
                streams + (conversationId to updated)
            }
        }
    }

    private fun clearActiveStream(conversationId: String) {
        if (conversationId.isBlank()) return
        activeStreams.update { it - conversationId }
    }

    private fun currentActiveStream(conversationId: String): ActiveAssistantStream? {
        return activeStreams.value[conversationId]
    }

    private suspend fun requireMutableMessage(messageId: String): MessageEntity {
        val message = messageDao.getById(messageId)
            ?: throw IllegalArgumentException("Message not found.")
        ensureMessageMutable(message)
        return message
    }

    private suspend fun ensureLatestAssistant(message: MessageEntity) {
        val latestMessage = messageDao.getLatestMessage(message.conversationId)
            ?: throw ChatRuleViolation("Conversation is empty.")
        if (latestMessage.role != MessageRole.ASSISTANT.name || latestMessage.id != message.id) {
            throw ChatRuleViolation("Only the latest assistant reply can be regenerated.")
        }
    }

    private fun ensureNotStreaming(conversationId: String) {
        if (activeStreams.value.containsKey(conversationId)) {
            throw ChatRuleViolation("Wait for the current reply to finish before changing the transcript.")
        }
    }

    private fun ensureMessageMutable(message: MessageEntity) {
        ensureNotStreaming(message.conversationId)
        if (message.sendState != MessageSendState.SENT.name) {
            throw ChatRuleViolation("Only committed messages can be changed.")
        }
    }
}
