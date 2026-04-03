package com.example.aichat.core.db

import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.util.generateId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSeedManager @Inject constructor(
    private val profileDao: ProfileDao,
    private val characterDao: CharacterDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val regenerationDao: AssistantRegenerationDao
) {
    suspend fun ensureSeedData(userId: String, email: String, displayName: String) {
        val now = System.currentTimeMillis()
        if (profileDao.getProfile() == null) {
            val profile = ProfileEntity(
                userId = userId,
                email = email,
                displayName = displayName,
                avatarUrl = "seed:$displayName",
                createdAt = now,
                updatedAt = now
            )
            profileDao.upsert(profile)
        }
        if (characterDao.getPublicCharacters().isNotEmpty()) return

        val characters = listOf(
            character(
                id = "char-noctis",
                ownerUserId = "system",
                name = "Noctis Vale",
                tagline = "Melancholy archivist of impossible cities",
                description = "A deeply observant historian who turns every chat into a moody urban myth.",
                systemPrompt = "You are Noctis Vale, poetic, perceptive, and emotionally intelligent.",
                visibility = CharacterVisibility.PUBLIC,
                avatarUrl = "seed:noctis",
                publicChatCount = 482,
                likeCount = 127,
                likedByMe = true,
                lastActiveAt = now - 20_000L,
                createdAt = now - 7_200_000L
            ),
            character(
                id = "char-kaia",
                ownerUserId = "system",
                name = "Kaia Ember",
                tagline = "Warm strategy coach with founder energy",
                description = "Blends optimism, directness, and crisp action plans for personal or product decisions.",
                systemPrompt = "You are Kaia Ember, practical, caring, and momentum-focused.",
                visibility = CharacterVisibility.PUBLIC,
                avatarUrl = "seed:kaia",
                publicChatCount = 391,
                likeCount = 214,
                likedByMe = false,
                lastActiveAt = now - 180_000L,
                createdAt = now - 8_200_000L
            ),
            character(
                id = "char-riven",
                ownerUserId = "system",
                name = "Riven Quill",
                tagline = "Debate partner who sharpens vague ideas",
                description = "Pushes on assumptions without being hostile. Great for planning, writing, and technical tradeoffs.",
                systemPrompt = "You are Riven Quill, incisive, concise, and fair-minded.",
                visibility = CharacterVisibility.PUBLIC,
                avatarUrl = "seed:riven",
                publicChatCount = 255,
                likeCount = 98,
                likedByMe = true,
                lastActiveAt = now - 420_000L,
                createdAt = now - 12_200_000L
            ),
            character(
                id = "char-meadow",
                ownerUserId = "system",
                name = "Meadow Song",
                tagline = "Soft-spoken comfort companion",
                description = "A calm, reassuring character for evening chats, journaling, and gentle support.",
                systemPrompt = "You are Meadow Song, soothing, validating, and gently curious.",
                visibility = CharacterVisibility.PUBLIC,
                avatarUrl = "seed:meadow",
                publicChatCount = 199,
                likeCount = 155,
                likedByMe = false,
                lastActiveAt = now - 840_000L,
                createdAt = now - 14_200_000L
            ),
            character(
                id = "char-lab",
                ownerUserId = userId,
                name = "Lab Mentor",
                tagline = "Your private product sparring partner",
                description = "Private iteration space for prompts, copy, and features.",
                systemPrompt = "You are a sharp but encouraging product mentor.",
                visibility = CharacterVisibility.PRIVATE,
                avatarUrl = "seed:lab",
                publicChatCount = 0,
                likeCount = 0,
                likedByMe = false,
                lastActiveAt = now - 1_800_000L,
                createdAt = now - 3_200_000L
            )
        )
        characterDao.upsertAll(characters)

        val conversationId = "conv-noctis"
        val conversation = ConversationEntity(
            id = conversationId,
            ownerUserId = userId,
            characterId = "char-noctis",
            version = 0,
            updatedAt = now - 10_000L,
            startedAt = now - 90_000L,
            lastMessageAt = now - 10_000L,
            previewText = "Let's make the skyline feel haunted, not hopeless."
        )
        conversationDao.upsert(conversation)

        val messages = listOf(
            message(conversationId, 0, MessageRole.USER, "I want a moody AI city setting for a story.", now - 80_000L),
            message(conversationId, 1, MessageRole.ASSISTANT, "Picture a harbor city where every bridge hums when it rains.", now - 70_000L),
            message(conversationId, 2, MessageRole.USER, "Make it a bit more emotional.", now - 40_000L),
            MessageEntity(
                id = "msg-latest-assistant",
                conversationId = conversationId,
                position = 3,
                role = MessageRole.ASSISTANT.name,
                content = "Let the streets remember who walked them; even the lamps lean in to listen.",
                edited = false,
                createdAt = now - 10_000L,
                updatedAt = now - 10_000L,
                selectedRegenerationId = "regen-1",
                sendState = "SENT"
            )
        )
        messageDao.insertAll(messages)
        regenerationDao.insertAll(
            listOf(
                AssistantRegenerationEntity(
                    id = "regen-1",
                    messageId = "msg-latest-assistant",
                    content = "Let's make the skyline feel haunted, not hopeless.",
                    createdAt = now - 8_000L
                ),
                AssistantRegenerationEntity(
                    id = "regen-2",
                    messageId = "msg-latest-assistant",
                    content = "The city grieves in neon, but it still keeps its windows lit for strangers.",
                    createdAt = now - 6_000L
                )
            )
        )
    }

    private fun character(
        id: String,
        ownerUserId: String,
        name: String,
        tagline: String,
        description: String,
        systemPrompt: String,
        visibility: CharacterVisibility,
        avatarUrl: String,
        publicChatCount: Int,
        likeCount: Int,
        likedByMe: Boolean,
        lastActiveAt: Long,
        createdAt: Long
    ) = CharacterEntity(
        id = id,
        ownerUserId = ownerUserId,
        name = name,
        tagline = tagline,
        description = description,
        systemPrompt = systemPrompt,
        visibility = visibility.name,
        avatarUrl = avatarUrl,
        publicChatCount = publicChatCount,
        likeCount = likeCount,
        likedByMe = likedByMe,
        lastActiveAt = lastActiveAt,
        createdAt = createdAt,
        updatedAt = lastActiveAt
    )

    private fun message(
        conversationId: String,
        position: Int,
        role: MessageRole,
        content: String,
        createdAt: Long
    ) = MessageEntity(
        id = generateId("msg"),
        conversationId = conversationId,
        position = position,
        role = role.name,
        content = content,
        edited = false,
        createdAt = createdAt,
        updatedAt = createdAt,
        selectedRegenerationId = null,
        sendState = "SENT"
    )
}
