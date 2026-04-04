package com.example.aichat.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class RoomUpsertBehaviorTest {
    private lateinit var database: AppDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao
    private lateinit var regenerationDao: AssistantRegenerationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationDao = database.conversationDao()
        messageDao = database.messageDao()
        regenerationDao = database.assistantRegenerationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertingConversation_doesNotDeleteMessages() = runBlocking {
        val conversation = ConversationEntity(
            id = "conversation-1",
            ownerUserId = "user-1",
            characterId = "character-1",
            version = 1,
            updatedAt = 100L,
            startedAt = 100L,
            lastMessageAt = null,
            previewText = ""
        )
        conversationDao.upsert(conversation)
        messageDao.insert(
            MessageEntity(
                id = "message-1",
                conversationId = conversation.id,
                position = 0,
                role = "USER",
                content = "hello",
                edited = false,
                createdAt = 101L,
                updatedAt = 101L,
                selectedRegenerationId = null,
                sendState = "SENT"
            )
        )

        conversationDao.upsert(
            conversation.copy(
                version = 2,
                updatedAt = 200L,
                lastMessageAt = 200L,
                previewText = "hello"
            )
        )

        assertThat(messageDao.getMessages(conversation.id)).hasSize(1)
    }

    @Test
    fun upsertingMessage_doesNotDeleteRegenerations() = runBlocking {
        val conversation = ConversationEntity(
            id = "conversation-1",
            ownerUserId = "user-1",
            characterId = "character-1",
            version = 1,
            updatedAt = 100L,
            startedAt = 100L,
            lastMessageAt = null,
            previewText = ""
        )
        conversationDao.upsert(conversation)
        val message = MessageEntity(
            id = "message-1",
            conversationId = conversation.id,
            position = 1,
            role = "ASSISTANT",
            content = "hello",
            edited = false,
            createdAt = 101L,
            updatedAt = 101L,
            selectedRegenerationId = "regen-1",
            sendState = "SENT"
        )
        messageDao.insert(message)
        regenerationDao.insert(
            AssistantRegenerationEntity(
                id = "regen-1",
                messageId = message.id,
                content = "variant",
                createdAt = 102L
            )
        )

        messageDao.insert(message.copy(content = "hello again", updatedAt = 200L))

        assertThat(regenerationDao.getByMessage(message.id)).hasSize(1)
    }

    @Test
    fun getMessages_ordersCommittedTranscriptBeforeLocalOnlyRows() = runBlocking {
        val conversation = ConversationEntity(
            id = "conversation-1",
            ownerUserId = "user-1",
            characterId = "character-1",
            version = 1,
            updatedAt = 100L,
            startedAt = 100L,
            lastMessageAt = 100L,
            previewText = "hello"
        )
        conversationDao.upsert(conversation)

        messageDao.insertAll(
            listOf(
                MessageEntity(
                    id = "local-pending",
                    conversationId = conversation.id,
                    position = -2,
                    role = "USER",
                    content = "pending",
                    edited = false,
                    createdAt = 400L,
                    updatedAt = 400L,
                    selectedRegenerationId = null,
                    sendState = "PENDING"
                ),
                MessageEntity(
                    id = "assistant-1",
                    conversationId = conversation.id,
                    position = 2,
                    role = "ASSISTANT",
                    content = "hi there",
                    edited = false,
                    createdAt = 200L,
                    updatedAt = 200L,
                    selectedRegenerationId = null,
                    sendState = "SENT"
                ),
                MessageEntity(
                    id = "user-1",
                    conversationId = conversation.id,
                    position = 1,
                    role = "USER",
                    content = "hello",
                    edited = false,
                    createdAt = 100L,
                    updatedAt = 100L,
                    selectedRegenerationId = null,
                    sendState = "SENT"
                ),
                MessageEntity(
                    id = "local-failed",
                    conversationId = conversation.id,
                    position = -1,
                    role = "USER",
                    content = "failed",
                    edited = false,
                    createdAt = 500L,
                    updatedAt = 500L,
                    selectedRegenerationId = null,
                    sendState = "FAILED"
                )
            )
        )

        val orderedIds = messageDao.getMessages(conversation.id).map { it.id }

        assertThat(orderedIds).containsExactly(
            "user-1",
            "assistant-1",
            "local-pending",
            "local-failed"
        ).inOrder()
    }
}
