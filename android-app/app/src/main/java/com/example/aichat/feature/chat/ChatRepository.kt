package com.example.aichat.feature.chat

import androidx.room.withTransaction
import com.example.aichat.BuildConfig
import com.example.aichat.core.db.AppDatabase
import com.example.aichat.core.db.AssistantRegenerationDao
import com.example.aichat.core.db.AssistantRegenerationEntity
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationEntity
import com.example.aichat.core.db.MessageDao
import com.example.aichat.core.db.MessageEntity
import com.example.aichat.core.db.toEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.ConversationDetail
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.MessageSendState
import com.example.aichat.core.network.AssistantRegenerationDto
import com.example.aichat.core.network.ChatApi
import com.example.aichat.core.network.ChatStreamEvent
import com.example.aichat.core.network.ChatStreamingClient
import com.example.aichat.core.network.ConversationApi
import com.example.aichat.core.network.ConversationDetailDto
import com.example.aichat.core.network.ConversationSummaryDto
import com.example.aichat.core.network.EditMessageRequestDto
import com.example.aichat.core.network.MessageDto
import com.example.aichat.core.network.SelectRegenerationRequestDto
import com.example.aichat.core.util.generateId
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
    override val message: String,
    val accepted: Boolean
) : IllegalStateException(message)

@Singleton
class ChatRepository @Inject constructor(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val characterDao: CharacterDao,
    private val messageDao: MessageDao,
    private val regenerationDao: AssistantRegenerationDao,
    private val conversationApi: ConversationApi,
    private val chatApi: ChatApi,
    private val streamingClient: ChatStreamingClient,
    private val responder: MockAssistantResponder
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

    suspend fun refreshConversation(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_MOCK_SERVICES) return@withContext Result.success(Unit)

        runCatching {
            recoverConversation(conversationId)
            val remote = conversationApi.getConversation(conversationId)
            syncConversation(remote)
        }
    }

    suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_MOCK_SERVICES) {
            return@withContext sendMockMessage(conversationId, text)
        }

        runCatching {
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
                    mode = ActiveStreamMode.SEND,
                    userMessageId = userMessageId
                )
            )

            consumeSendStream(conversationId, userMessageId, normalized)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_MOCK_SERVICES) {
            return@withContext editMockMessage(messageId, newContent)
        }

        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            val updated = ChatTranscriptRules.edit(
                committedSnapshot(message.conversationId),
                messageId,
                newContent,
                System.currentTimeMillis()
            )
            val updatedMessage = updated.first { it.id == messageId }

            chatApi.editMessage(messageId, EditMessageRequestDto(newContent.trim()))
            applyEditedMessage(message, updatedMessage)
            updateConversationMetadataFromMessages(message.conversationId, updated)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun rewind(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_MOCK_SERVICES) {
            return@withContext rewindMock(messageId)
        }

        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            chatApi.rewind(messageId)

            database.withTransaction {
                messageDao.deleteAfter(message.conversationId, message.position)
                messageDao.getLocalOnlyMessages(message.conversationId).forEach { localMessage ->
                    messageDao.deleteById(localMessage.id)
                }
                updateConversationMetadataFromMessages(
                    conversationId = message.conversationId,
                    messages = snapshot(message.conversationId)
                )
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun regenerateLatestAssistant(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_MOCK_SERVICES) {
            return@withContext regenerateMock(messageId)
        }

        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            setActiveStream(
                message.conversationId,
                ActiveAssistantStream(
                    conversationId = message.conversationId,
                    mode = ActiveStreamMode.REGENERATE,
                    assistantMessageId = messageId,
                    targetMessageId = messageId
                )
            )

            consumeRegenerateStream(message.conversationId, messageId)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun selectRegeneration(messageId: String, regenerationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_MOCK_SERVICES) {
            return@withContext selectMockRegeneration(messageId, regenerationId)
        }

        runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            chatApi.selectRegeneration(messageId, SelectRegenerationRequestDto(regenerationId))
            messageDao.update(message.copy(selectedRegenerationId = regenerationId, updatedAt = System.currentTimeMillis()))
            updateConversationMetadataFromMessages(message.conversationId, committedSnapshot(message.conversationId))
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun sendMockMessage(conversationId: String, text: String): Result<Unit> {
        return runCatching {
            val normalized = text.trim()
            if (normalized.isBlank()) throw IllegalArgumentException("Message can't be empty.")
            ensureNotStreaming(conversationId)

            val conversation = conversationDao.getById(conversationId)
                ?: throw IllegalArgumentException("Conversation not found.")
            val character = characterDao.getById(conversation.characterId)?.toModel()
                ?: throw IllegalStateException("Character not found.")
            val now = System.currentTimeMillis()
            val userMessageId = generateUlid(now)
            val committedMessages = snapshot(conversationId).filter { it.sendState == MessageSendState.SENT }
            val userPosition = (committedMessages.maxByOrNull { it.position }?.position ?: -1) + 1

            messageDao.insert(
                MessageEntity(
                    id = userMessageId,
                    conversationId = conversationId,
                    position = userPosition,
                    role = MessageRole.USER.name,
                    content = normalized,
                    edited = false,
                    createdAt = now,
                    updatedAt = now,
                    selectedRegenerationId = null,
                    sendState = MessageSendState.SENT.name
                )
            )
            setActiveStream(
                conversationId,
                ActiveAssistantStream(
                    conversationId = conversationId,
                    runId = generateId("run"),
                    mode = ActiveStreamMode.SEND,
                    assistantMessageId = generateId("message"),
                    userMessageId = userMessageId,
                    accepted = true
                )
            )

            val transcript = snapshot(conversationId).filter { it.sendState == MessageSendState.SENT }
            var buffer = ""
            responder.streamReply(character, transcript, ReplyMode.REPLY).collect { chunk ->
                buffer += chunk
                updateActiveStream(conversationId) { stream ->
                    stream?.copy(text = buffer)
                }
            }

            val assistantNow = System.currentTimeMillis()
            val assistantMessageId = currentActiveStream(conversationId)?.assistantMessageId ?: generateId("message")
            messageDao.insert(
                MessageEntity(
                    id = assistantMessageId,
                    conversationId = conversationId,
                    position = userPosition + 1,
                    role = MessageRole.ASSISTANT.name,
                    content = buffer.trim(),
                    edited = false,
                    createdAt = assistantNow,
                    updatedAt = assistantNow,
                    selectedRegenerationId = null,
                    sendState = MessageSendState.SENT.name
                )
            )
            updateConversationMetadataFromSummary(
                conversationId = conversationId,
                summary = ConversationSummaryDto(
                    id = conversationId,
                    characterId = conversation.characterId,
                    characterName = character.name,
                    characterAvatarUrl = character.avatarUrl,
                    updatedAt = assistantNow,
                    startedAt = conversation.startedAt,
                    lastMessageAt = assistantNow,
                    lastPreview = buffer.trim()
                ),
                conversationVersion = conversation.version + 1
            )
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw error
        }.fold(
            onSuccess = {
                clearActiveStream(conversationId)
                Result.success(Unit)
            },
            onFailure = {
                clearActiveStream(conversationId)
                Result.failure(it)
            }
        )
    }

    private suspend fun editMockMessage(messageId: String, newContent: String): Result<Unit> {
        return runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            val updated = ChatTranscriptRules.edit(
                committedSnapshot(message.conversationId),
                messageId,
                newContent,
                System.currentTimeMillis()
            )
            val updatedMessage = updated.first { it.id == messageId }
            applyEditedMessage(message, updatedMessage)
            updateConversationMetadataFromMessages(message.conversationId, updated)
        }
    }

    private suspend fun rewindMock(messageId: String): Result<Unit> {
        return runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            val committed = snapshot(message.conversationId).filter { it.sendState == MessageSendState.SENT }
            val target = committed.firstOrNull { it.id == messageId }
                ?: throw IllegalArgumentException("Message not found.")

            database.withTransaction {
                messageDao.deleteAfter(message.conversationId, target.position)
                messageDao.getLocalOnlyMessages(message.conversationId).forEach { localMessage ->
                    messageDao.deleteById(localMessage.id)
                }
                updateConversationMetadataFromMessages(message.conversationId, snapshot(message.conversationId))
            }
        }
    }

    private suspend fun regenerateMock(messageId: String): Result<Unit> {
        var activeConversationId = ""
        return runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            activeConversationId = message.conversationId
            val conversation = conversationDao.getById(message.conversationId)
                ?: throw IllegalArgumentException("Conversation not found.")
            val messages = snapshot(message.conversationId).filter { it.sendState == MessageSendState.SENT }
            val target = ChatTranscriptRules.regenerateTarget(messages, messageId)
            val character = characterDao.getById(conversation.characterId)?.toModel()
                ?: throw IllegalStateException("Character not found.")
            val context = messages.filter { it.position < target.position }

            setActiveStream(
                message.conversationId,
                ActiveAssistantStream(
                    conversationId = message.conversationId,
                    runId = generateId("run"),
                    mode = ActiveStreamMode.REGENERATE,
                    assistantMessageId = messageId,
                    targetMessageId = messageId,
                    accepted = true
                )
            )

            var buffer = ""
            responder.streamReply(character, context, ReplyMode.REGENERATE).collect { chunk ->
                buffer += chunk
                updateActiveStream(message.conversationId) { stream ->
                    stream?.copy(text = buffer)
                }
            }

            val regenerationId = generateId("regen")
            val regenerationNow = System.currentTimeMillis()
            regenerationDao.insert(
                AssistantRegenerationEntity(
                    id = regenerationId,
                    messageId = target.id,
                    content = buffer.trim(),
                    createdAt = regenerationNow
                )
            )
            messageDao.update(
                message.copy(
                    selectedRegenerationId = regenerationId,
                    updatedAt = regenerationNow
                )
            )
            updateConversationMetadataFromMessages(message.conversationId, snapshot(message.conversationId))
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw error
        }.fold(
            onSuccess = {
                clearActiveStream(activeConversationId)
                Result.success(Unit)
            },
            onFailure = {
                clearActiveStream(activeConversationId)
                Result.failure(it)
            }
        )
    }

    private suspend fun selectMockRegeneration(messageId: String, regenerationId: String): Result<Unit> {
        return runCatching {
            val message = messageDao.getById(messageId)
                ?: throw IllegalArgumentException("Message not found.")
            ensureMessageMutable(message)
            val messages = committedSnapshot(message.conversationId)
            ChatTranscriptRules.selectRegeneration(messages, messageId, regenerationId)
            messageDao.update(message.copy(selectedRegenerationId = regenerationId, updatedAt = System.currentTimeMillis()))
            updateConversationMetadataFromMessages(message.conversationId, messages)
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
                        throw StreamFailedException(event.message, accepted = active?.accepted == true || accepted)
                    }

                    else -> Unit
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!accepted) {
                markMessageFailed(userMessageId)
            }
            throw error
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
                        throw StreamFailedException(event.message, accepted = true)
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
        upsertMessageFromDto(event.assistantMessage, sendState = MessageSendState.SENT)
        updateConversationMetadataFromSummary(conversationId, event.conversationSummary, event.conversationVersion)
        clearActiveStream(conversationId)
    }

    private suspend fun applyCompletedRegenerate(conversationId: String, event: ChatStreamEvent.CompletedRegenerate) {
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
        clearActiveStream(conversationId)
    }

    private suspend fun applyEditedMessage(original: MessageEntity, updatedMessage: ChatMessage) {
        if (original.role == MessageRole.ASSISTANT.name && updatedMessage.selectedRegenerationId != null) {
            val regeneration = updatedMessage.regenerations.first { it.id == updatedMessage.selectedRegenerationId }
            regenerationDao.insert(
                AssistantRegenerationEntity(
                    id = regeneration.id,
                    messageId = regeneration.messageId,
                    content = regeneration.content,
                    createdAt = regeneration.createdAt
                )
            )
            messageDao.update(original.copy(edited = true, updatedAt = updatedMessage.updatedAt))
        } else {
            messageDao.update(
                original.copy(
                    content = updatedMessage.content,
                    edited = true,
                    updatedAt = updatedMessage.updatedAt
                )
            )
        }
    }

    private suspend fun syncConversation(detail: ConversationDetailDto) {
        database.withTransaction {
            markPendingMessagesFailed(detail.id)
            characterDao.upsert(detail.character.toEntity())

            val existingConversation = conversationDao.getById(detail.id)
            val latestMessage = detail.messages.maxByOrNull { it.position }
            val preview = latestMessage?.let { message ->
                message.regenerations.firstOrNull { it.id == message.selectedRegenerationId }?.content ?: message.content
            } ?: existingConversation?.previewText ?: ""
            val lastMessageAt = latestMessage?.updatedAt ?: latestMessage?.createdAt ?: existingConversation?.lastMessageAt
            val updatedAt = lastMessageAt ?: existingConversation?.updatedAt ?: System.currentTimeMillis()
            val startedAt = existingConversation?.startedAt
                ?: detail.messages.minByOrNull { it.position }?.createdAt
                ?: System.currentTimeMillis()

            conversationDao.upsert(
                ConversationEntity(
                    id = detail.id,
                    ownerUserId = detail.ownerUserId.ifBlank { existingConversation?.ownerUserId.orEmpty() },
                    characterId = detail.character.id,
                    version = detail.conversationVersion,
                    updatedAt = updatedAt,
                    startedAt = startedAt,
                    lastMessageAt = lastMessageAt,
                    previewText = preview
                )
            )

            messageDao.deleteCommittedByConversation(detail.id)
            if (detail.messages.isNotEmpty()) {
                messageDao.insertAll(
                    detail.messages.map { message ->
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
                            sendState = MessageSendState.SENT.name
                        )
                    }
                )
                regenerationDao.insertAll(
                    detail.messages.flatMap { message ->
                        message.regenerations.map { regeneration ->
                            AssistantRegenerationEntity(
                                id = regeneration.id,
                                messageId = regeneration.messageId,
                                content = regeneration.content,
                                createdAt = regeneration.createdAt
                            )
                        }
                    }
                )
            }
        }
    }

    private suspend fun recoverConversation(conversationId: String) {
        clearActiveStream(conversationId)
        markPendingMessagesFailed(conversationId)
    }

    private suspend fun markPendingMessagesFailed(conversationId: String) {
        val pendingMessages = messageDao.getLocalOnlyMessages(conversationId)
            .filter { it.sendState == MessageSendState.PENDING.name }
        if (pendingMessages.isEmpty()) return

        val now = System.currentTimeMillis()
        pendingMessages.forEach { message ->
            messageDao.update(
                message.copy(
                    sendState = MessageSendState.FAILED.name,
                    updatedAt = now
                )
            )
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

    private suspend fun updateConversationMetadataFromMessages(
        conversationId: String,
        messages: List<ChatMessage>
    ) {
        val conversation = conversationDao.getById(conversationId) ?: return
        val committedMessages = messages.filter { it.sendState == MessageSendState.SENT }
        val latest = committedMessages.lastOrNull()
        val now = System.currentTimeMillis()
        conversationDao.upsert(
            conversation.copy(
                version = conversation.version + 1,
                updatedAt = now,
                lastMessageAt = now,
                previewText = latest?.visibleContent ?: conversation.previewText
            )
        )
    }

    private suspend fun snapshot(conversationId: String): List<ChatMessage> {
        val messages = messageDao.getMessages(conversationId)
        return messages.map { message ->
            message.toModel(regenerationDao.getByMessage(message.id))
        }
    }

    private suspend fun committedSnapshot(conversationId: String): List<ChatMessage> {
        return snapshot(conversationId).filter { it.sendState == MessageSendState.SENT }
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
