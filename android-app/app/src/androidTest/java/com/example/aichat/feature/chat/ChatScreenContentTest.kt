package com.example.aichat.feature.chat

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.foundation.layout.PaddingValues
import com.example.aichat.core.design.AppTheme
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.ConversationDetail
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.MessageSendState
import com.example.aichat.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test

class ChatScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smartFollow_stopsOnManualScroll_andResumesFromJumpToLatest() {
        val baseConversation = conversationDetail(
            messages = List(30) { index ->
                ChatMessage(
                    id = "message-$index",
                    conversationId = "conversation-1",
                    position = index + 1,
                    role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                    content = "Message $index",
                    edited = false,
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                    selectedRegenerationId = null,
                    sendState = MessageSendState.SENT
                )
            }
        )
        val chatState = mutableStateOf(
            ChatUiState(
                conversation = baseConversation,
                activeStream = null,
                composerText = ""
            )
        )

        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }

            AppTheme(themeMode = ThemeMode.LIGHT) {
                ChatScreenContent(
                    paddingValues = PaddingValues(),
                    onBack = {},
                    state = chatState.value,
                    snackbarHostState = snackbarHostState,
                    onComposerChanged = {},
                    onSend = {},
                    onMessageLongPress = {},
                    onSelectPreviousVariant = {},
                    onSelectNextVariant = {}
                )
            }
        }

        composeRule.onNodeWithText("Message 29").assertIsDisplayed()
        composeRule.onNodeWithTag("chat-transcript").performTouchInput { swipeDown() }
        composeRule.onNodeWithTag("chat-transcript").performTouchInput { swipeDown() }
        composeRule.runOnIdle {
            chatState.value = chatState.value.copy(
                activeStream = ActiveAssistantStream(
                    conversationId = "conversation-1",
                    draftKey = "send-draft-1",
                    mode = ActiveStreamMode.SEND,
                    userMessageId = "user-new",
                    text = "Streaming reply"
                )
            )
        }
        composeRule.onNodeWithTag("jump-to-latest").assertExists()
        composeRule.onNodeWithText("Streaming reply").assertDoesNotExist()

        composeRule.onNodeWithTag("jump-to-latest").performClick()
        composeRule.onNodeWithText("Streaming reply").assertIsDisplayed()
    }

    private fun conversationDetail(messages: List<ChatMessage>) = ConversationDetail(
        id = "conversation-1",
        ownerUserId = "user-1",
        conversationVersion = 1,
        character = CharacterSummary(
            id = "character-1",
            ownerUserId = "user-1",
            name = "Astra",
            tagline = "",
            description = "",
            systemPrompt = "Be helpful",
            visibility = CharacterVisibility.PUBLIC,
            avatarUrl = null,
            publicChatCount = 0,
            likeCount = 0,
            likedByMe = false,
            lastActiveAt = 0L,
            createdAt = 0L,
            updatedAt = 0L
        ),
        messages = messages
    )
}
