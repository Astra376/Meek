package com.example.aichat.feature.chat

import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.MessageRole

class ChatRuleViolation(message: String) : IllegalStateException(message)

sealed interface EditableTarget {
    data class MessageContent(val messageId: String) : EditableTarget
    data class RegenerationContent(val messageId: String, val regenerationId: String) : EditableTarget
}

object ChatTranscriptRules {
    fun edit(messages: List<ChatMessage>, messageId: String, newContent: String, now: Long): List<ChatMessage> {
        if (newContent.isBlank()) throw ChatRuleViolation("Message content can't be empty.")
        val target = messages.firstOrNull { it.id == messageId } ?: throw ChatRuleViolation("Message not found.")
        return when {
            target.role == MessageRole.ASSISTANT && target.selectedRegenerationId != null -> {
                messages.map { message ->
                    if (message.id != messageId) {
                        message
                    } else {
                        message.copy(
                            edited = true,
                            updatedAt = now,
                            regenerations = message.regenerations.map { regeneration ->
                                if (regeneration.id == message.selectedRegenerationId) {
                                    regeneration.copy(content = newContent.trim())
                                } else {
                                    regeneration
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(content = newContent.trim(), edited = true, updatedAt = now)
                    } else {
                        message
                    }
                }
            }
        }
    }

    fun rewind(messages: List<ChatMessage>, messageId: String): List<ChatMessage> {
        val target = messages.firstOrNull { it.id == messageId } ?: throw ChatRuleViolation("Message not found.")
        return messages.filter { it.position <= target.position }
    }

    fun regenerateTarget(messages: List<ChatMessage>, messageId: String): ChatMessage {
        val target = messages.firstOrNull { it.id == messageId } ?: throw ChatRuleViolation("Message not found.")
        val latest = messages.maxByOrNull { it.position } ?: throw ChatRuleViolation("Conversation is empty.")
        if (target.role != MessageRole.ASSISTANT || target.id != latest.id) {
            throw ChatRuleViolation("Only the latest assistant reply can be regenerated.")
        }
        return target
    }

    fun selectRegeneration(messages: List<ChatMessage>, messageId: String, regenerationId: String): List<ChatMessage> {
        val target = regenerateTarget(messages, messageId)
        if (target.regenerations.none { it.id == regenerationId }) {
            throw ChatRuleViolation("Selected regeneration does not exist.")
        }
        return messages.map { message ->
            if (message.id == target.id) message.copy(selectedRegenerationId = regenerationId) else message
        }
    }
}

