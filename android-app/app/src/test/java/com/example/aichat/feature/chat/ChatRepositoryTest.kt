package com.example.aichat.feature.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aichat.core.db.AppDatabase
import com.example.aichat.core.db.AssistantRegenerationEntity
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationEntity
import com.example.aichat.core.db.MessageDao
import com.example.aichat.core.db.MessageEntity
import com.example.aichat.core.model.MessageSendState
import com.example.aichat.core.network.AssistantRegenerationDto
import com.example.aichat.core.network.ChatApi
import com.example.aichat.core.network.ChatStreamEvent
import com.example.aichat.core.network.ChatStreamingClient
import com.example.aichat.core.network.ConversationSummaryDto
import com.example.aichat.core.network.EditMessageRequestDto
import com.example.aichat.core.network.MessageDto
import com.example.aichat.core.network.SelectRegenerationRequestDto
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ChatRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var characterDao: CharacterDao
    private lateinit var messageDao: MessageDao
    private lateinit var streamingClient: FakeStreamingClient
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationDao = database.conversationDao()
        characterDao = database.characterDao()
        messageDao = database.messageDao()
        streamingClient = FakeStreamingClient()
        repository = ChatRepository(
            database = database,
            conversationDao = conversationDao,
            characterDao = characterDao,
            messageDao = messageDao,
            regenerationDao = database.assistantRegenerationDao(),
            chatApi = FakeChatApi(),
            streamingClient = streamingClient
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sendMessage_acceptAndComplete_persistsSingleAssistantReply() = runTest {
        seedConversation(version = 1)
        streamingClient.sendHandler = { conversationId, userMessageId, _ ->
            flow {
                emit(
                    ChatStreamEvent.AcceptedSend(
                        runId = "run-1",
                        conversationVersion = 1,
                        userMessage = remoteMessage(
                            id = userMessageId,
                            conversationId = conversationId,
                            position = 1,
                            role = "user",
                            content = "hello",
                            createdAt = 100L,
                            updatedAt = 100L
                        ),
                        assistantMessageId = "assistant-1"
                    )
                )
                emit(ChatStreamEvent.Delta(runId = "run-1", textDelta = "hello"))
                emit(
                    ChatStreamEvent.CompletedSend(
                        runId = "run-1",
                        conversationVersion = 2,
                        assistantMessage = remoteMessage(
                            id = "assistant-1",
                            position = 2,
                            role = "assistant",
                            content = "hello there",
                            createdAt = 200L,
                            updatedAt = 200L
                        ),
                        conversationSummary = conversationSummary(version = 2, preview = "hello there")
                    )
                )
            }
        }

        repository.sendMessage(CONVERSATION_ID, "hello").getOrThrow()

        val messages = messageDao.getMessages(CONVERSATION_ID)
        val activeStream = repository.observeActiveStream(CONVERSATION_ID).first()

        assertThat(messages).hasSize(2)
        assertThat(messages.count { it.role == "ASSISTANT" }).isEqualTo(1)
        assertThat(messages[0].role).isEqualTo("USER")
        assertThat(messages[1].id).isEqualTo("assistant-1")
        assertThat(activeStream?.status).isEqualTo(ActiveStreamStatus.COMPLETED)

        repository.dismissSettledStream(CONVERSATION_ID, activeStream?.draftKey)

        assertThat(repository.observeActiveStream(CONVERSATION_ID).first()).isNull()
    }

    @Test
    fun pauseRequested_afterAcceptedSend_keepsPartialDraftVisible() = runTest {
        seedConversation(version = 1)
        val sendEvents = MutableSharedFlow<ChatStreamEvent>(extraBufferCapacity = 8)
        streamingClient.sendHandler = { _, _, _ -> sendEvents }

        val sendJob = backgroundScope.launch {
            repository.sendMessage(CONVERSATION_ID, "hello")
        }

        repository.observeActiveStream(CONVERSATION_ID).first { it != null }
        val pendingMessage = messageDao.getLocalOnlyMessages(CONVERSATION_ID).single()
        sendEvents.emit(
            ChatStreamEvent.AcceptedSend(
                runId = "run-1",
                conversationVersion = 1,
                userMessage = remoteMessage(
                    id = pendingMessage.id,
                    position = 1,
                    role = "user",
                    content = "hello",
                    createdAt = 100L,
                    updatedAt = 100L
                ),
                assistantMessageId = "assistant-1"
            )
        )
        sendEvents.emit(ChatStreamEvent.Delta(runId = "run-1", textDelta = "partial reply"))

        repository.observeActiveStream(CONVERSATION_ID).first { it?.text == "partial reply" }
        repository.requestPause(CONVERSATION_ID)
        sendJob.cancel()
        sendJob.join()

        val activeStream = repository.observeActiveStream(CONVERSATION_ID).first()

        assertThat(activeStream?.status).isEqualTo(ActiveStreamStatus.PAUSED)
        assertThat(activeStream?.text).isEqualTo("partial reply")
        assertThat(messageDao.getById(pendingMessage.id)?.sendState).isEqualTo(MessageSendState.SENT.name)
    }

    @Test
    fun regenerateLatestAssistant_persistsSelectedVariant() = runTest {
        seedConversation(version = 1)
        messageDao.insertAll(
            listOf(
                sentMessage(
                    id = "user-1",
                    position = 1,
                    role = "USER",
                    content = "hello",
                    createdAt = 100L,
                    updatedAt = 100L
                ),
                sentMessage(
                    id = "assistant-1",
                    position = 2,
                    role = "ASSISTANT",
                    content = "old answer",
                    createdAt = 200L,
                    updatedAt = 200L
                )
            )
        )
        streamingClient.regenerateHandler = { _ ->
            flow {
                emit(
                    ChatStreamEvent.AcceptedRegenerate(
                        runId = "run-2",
                        conversationVersion = 1,
                        messageId = "assistant-1",
                        assistantMessageId = "assistant-1"
                    )
                )
                emit(ChatStreamEvent.Delta(runId = "run-2", textDelta = "better"))
                emit(
                    ChatStreamEvent.CompletedRegenerate(
                        runId = "run-2",
                        conversationVersion = 2,
                        messageId = "assistant-1",
                        regeneration = AssistantRegenerationDto(
                            id = "regen-1",
                            messageId = "assistant-1",
                            content = "better answer",
                            createdAt = 300L
                        ),
                        selectedRegenerationId = "regen-1",
                        conversationSummary = conversationSummary(version = 2, preview = "better answer")
                    )
                )
            }
        }

        repository.regenerateLatestAssistant("assistant-1").getOrThrow()

        val message = messageDao.getById("assistant-1")
        val regenerations = database.assistantRegenerationDao().getByMessage("assistant-1")
        val activeStream = repository.observeActiveStream(CONVERSATION_ID).first()

        assertThat(message?.selectedRegenerationId).isEqualTo("regen-1")
        assertThat(regenerations.map(AssistantRegenerationEntity::id)).containsExactly("regen-1")
        assertThat(activeStream?.status).isEqualTo(ActiveStreamStatus.COMPLETED)
    }

    private suspend fun seedConversation(version: Long) {
        conversationDao.upsert(
            ConversationEntity(
                id = CONVERSATION_ID,
                ownerUserId = USER_ID,
                characterId = CHARACTER_ID,
                version = version,
                updatedAt = 100L,
                startedAt = 100L,
                lastMessageAt = null,
                previewText = ""
            )
        )
        characterDao.upsert(
            com.example.aichat.core.db.CharacterEntity(
                id = CHARACTER_ID,
                ownerUserId = USER_ID,
                name = "Astra",
                tagline = "",
                description = "",
                systemPrompt = "Be helpful",
                visibility = "PUBLIC",
                avatarUrl = null,
                publicChatCount = 0,
                likeCount = 0,
                likedByMe = false,
                lastActiveAt = 100L,
                createdAt = 100L,
                updatedAt = 100L
            )
        )
    }

    private fun sentMessage(
        id: String,
        position: Int,
        role: String,
        content: String,
        createdAt: Long,
        updatedAt: Long
    ) = MessageEntity(
        id = id,
        conversationId = CONVERSATION_ID,
        position = position,
        role = role,
        content = content,
        edited = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
        selectedRegenerationId = null,
        sendState = MessageSendState.SENT.name
    )

    private fun remoteMessage(
        id: String,
        position: Int,
        role: String,
        content: String,
        createdAt: Long,
        updatedAt: Long,
        conversationId: String = CONVERSATION_ID
    ) = MessageDto(
        id = id,
        conversationId = conversationId,
        position = position,
        role = role,
        content = content,
        edited = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
        selectedRegenerationId = null,
        regenerations = emptyList()
    )

    private fun conversationSummary(version: Long, preview: String) = ConversationSummaryDto(
        id = CONVERSATION_ID,
        characterId = CHARACTER_ID,
        characterName = "Astra",
        characterAvatarUrl = null,
        updatedAt = 100L + version,
        startedAt = 100L,
        lastMessageAt = 100L + version,
        lastPreview = preview
    )

    private class FakeChatApi : ChatApi {
        override suspend fun editMessage(messageId: String, body: EditMessageRequestDto) = Unit

        override suspend fun rewind(messageId: String) = Unit

        override suspend fun selectRegeneration(messageId: String, body: SelectRegenerationRequestDto) = Unit
    }

    private class FakeStreamingClient : ChatStreamingClient {
        var sendHandler: (String, String, String) -> Flow<ChatStreamEvent> = { _, _, _ -> emptyFlow() }
        var regenerateHandler: (String) -> Flow<ChatStreamEvent> = { emptyFlow() }

        override fun sendMessage(conversationId: String, userMessageId: String, content: String): Flow<ChatStreamEvent> {
            return sendHandler(conversationId, userMessageId, content)
        }

        override fun regenerateLatestAssistant(messageId: String): Flow<ChatStreamEvent> {
            return regenerateHandler(messageId)
        }
    }

    private companion object {
        const val USER_ID = "user-1"
        const val CHARACTER_ID = "character-1"
        const val CONVERSATION_ID = "conversation-1"
    }
}
