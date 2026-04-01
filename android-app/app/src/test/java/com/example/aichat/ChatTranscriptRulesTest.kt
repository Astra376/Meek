package com.example.aichat

import com.example.aichat.core.model.AssistantRegeneration
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.MessageRole
import com.example.aichat.feature.chat.ChatRuleViolation
import com.example.aichat.feature.chat.ChatTranscriptRules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatTranscriptRulesTest {
    @Test
    fun edit_updates_selected_regeneration_when_assistant_variant_is_visible() {
        val messages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                position = 0,
                role = MessageRole.USER,
                content = "hello",
                edited = false,
                createdAt = 1L,
                updatedAt = 1L,
                selectedRegenerationId = null
            ),
            ChatMessage(
                id = "m2",
                conversationId = "c1",
                position = 1,
                role = MessageRole.ASSISTANT,
                content = "base",
                edited = false,
                createdAt = 2L,
                updatedAt = 2L,
                selectedRegenerationId = "r2",
                regenerations = listOf(
                    AssistantRegeneration("r1", "m2", "first", 3L),
                    AssistantRegeneration("r2", "m2", "second", 4L)
                )
            )
        )

        val updated = ChatTranscriptRules.edit(messages, "m2", "edited visible content", now = 9L)
        val editedMessage = updated.last()

        assertThat(editedMessage.edited).isTrue()
        assertThat(editedMessage.visibleContent).isEqualTo("edited visible content")
        assertThat(editedMessage.content).isEqualTo("base")
    }

    @Test
    fun rewind_keeps_messages_up_to_selected_position() {
        val messages = buildTranscript()

        val rewound = ChatTranscriptRules.rewind(messages, "m2")

        assertThat(rewound.map { it.id }).containsExactly("m1", "m2").inOrder()
    }

    @Test(expected = ChatRuleViolation::class)
    fun regenerateTarget_rejects_non_latest_assistant() {
        val messages = buildTranscript()

        ChatTranscriptRules.regenerateTarget(messages, "m2")
    }

    @Test
    fun selectRegeneration_updates_selected_id() {
        val target = ChatMessage(
            id = "m3",
            conversationId = "c1",
            position = 2,
            role = MessageRole.ASSISTANT,
            content = "base",
            edited = false,
            createdAt = 2L,
            updatedAt = 2L,
            selectedRegenerationId = "r1",
            regenerations = listOf(
                AssistantRegeneration("r1", "m3", "first", 3L),
                AssistantRegeneration("r2", "m3", "second", 4L)
            )
        )

        val updated = ChatTranscriptRules.selectRegeneration(listOf(target), "m3", "r2")

        assertThat(updated.single().selectedRegenerationId).isEqualTo("r2")
        assertThat(updated.single().visibleContent).isEqualTo("second")
    }

    private fun buildTranscript(): List<ChatMessage> = listOf(
        ChatMessage("m1", "c1", 0, MessageRole.USER, "one", false, 1L, 1L, null),
        ChatMessage("m2", "c1", 1, MessageRole.ASSISTANT, "two", false, 2L, 2L, null),
        ChatMessage("m3", "c1", 2, MessageRole.USER, "three", false, 3L, 3L, null)
    )
}

