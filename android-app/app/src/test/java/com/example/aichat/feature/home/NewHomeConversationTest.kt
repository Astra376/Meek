package com.example.aichat.feature.home

import com.example.aichat.core.model.ConversationSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NewHomeConversationTest {
    @Test
    fun mostRecentChatPerCharacter_removesOlderDuplicateChats() {
        val chats = listOf(
            conversation(id = "old-a", characterId = "a", updatedAt = 100),
            conversation(id = "b", characterId = "b", updatedAt = 200),
            conversation(id = "new-a", characterId = "a", updatedAt = 300)
        )

        val result = mostRecentChatPerCharacter(chats)

        assertThat(result.map { it.id }).containsExactly("new-a", "b")
    }

    @Test
    fun mostRecentChatPerCharacter_breaksTimestampTiesDeterministically() {
        val chats = listOf(
            conversation(id = "a-1", characterId = "a", updatedAt = 100, startedAt = 10),
            conversation(id = "a-2", characterId = "a", updatedAt = 100, startedAt = 20)
        )

        assertThat(mostRecentChatPerCharacter(chats).single().id).isEqualTo("a-2")
    }

    private fun conversation(
        id: String,
        characterId: String,
        updatedAt: Long,
        startedAt: Long = updatedAt
    ) = ConversationSummary(
        id = id,
        characterId = characterId,
        characterName = characterId,
        characterAvatarUrl = null,
        updatedAt = updatedAt,
        startedAt = startedAt,
        lastMessageAt = updatedAt,
        lastPreview = "",
        unreadCount = 0,
        hasUnreadBadge = false
    )
}
