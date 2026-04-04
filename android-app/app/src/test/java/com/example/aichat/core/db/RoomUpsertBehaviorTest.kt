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
}
