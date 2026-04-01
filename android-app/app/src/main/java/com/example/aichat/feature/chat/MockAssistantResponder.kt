package com.example.aichat.feature.chat

import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class MockAssistantResponder @Inject constructor() {
    fun streamReply(
        character: CharacterSummary,
        transcript: List<ChatMessage>,
        mode: ReplyMode
    ): Flow<String> = flow {
        val latestUserText = transcript.lastOrNull { it.role.name == "USER" }?.visibleContent.orEmpty()
        val fullResponse = when (mode) {
            ReplyMode.REPLY -> buildReply(character.name, latestUserText)
            ReplyMode.REGENERATE -> buildRegeneration(character.name, latestUserText)
        }
        fullResponse.chunked(16).forEach { chunk ->
            delay(55)
            emit(chunk)
        }
    }

    private fun buildReply(name: String, latestUserText: String): String {
        return when {
            latestUserText.contains("plan", ignoreCase = true) ->
                "$name leans in: let's turn that into a crisp plan with one clear next step and one risky assumption to test."
            latestUserText.contains("story", ignoreCase = true) ->
                "$name says the story should feel tactile and intimate, with details that carry emotion instead of just decoration."
            latestUserText.isBlank() ->
                "$name is ready whenever you are. Give me a direction and I'll keep the thread coherent."
            else ->
                "$name responds with steady focus: ${latestUserText.take(80)} can go further if we sharpen the emotional core and give it one surprising image."
        }
    }

    private fun buildRegeneration(name: String, latestUserText: String): String {
        return if (latestUserText.isBlank()) {
            "$name offers a quieter alternate take, softer and more reflective than the first reply."
        } else {
            "$name tries a new angle: instead of stating the answer plainly, let's frame ${latestUserText.take(50)} around tension, rhythm, and one memorable turn of phrase."
        }
    }
}

enum class ReplyMode {
    REPLY,
    REGENERATE
}

