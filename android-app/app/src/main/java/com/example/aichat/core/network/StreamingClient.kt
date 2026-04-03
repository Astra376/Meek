package com.example.aichat.core.network

import com.example.aichat.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface ChatStreamEvent {
    data class AcceptedSend(
        val runId: String,
        val conversationVersion: Long,
        val userMessage: MessageDto,
        val assistantMessageId: String
    ) : ChatStreamEvent

    data class AcceptedRegenerate(
        val runId: String,
        val conversationVersion: Long,
        val messageId: String,
        val assistantMessageId: String
    ) : ChatStreamEvent

    data class Delta(
        val runId: String,
        val textDelta: String
    ) : ChatStreamEvent

    data class CompletedSend(
        val runId: String,
        val conversationVersion: Long,
        val assistantMessage: MessageDto,
        val conversationSummary: ConversationSummaryDto
    ) : ChatStreamEvent

    data class CompletedRegenerate(
        val runId: String,
        val conversationVersion: Long,
        val messageId: String,
        val regeneration: AssistantRegenerationDto,
        val selectedRegenerationId: String,
        val conversationSummary: ConversationSummaryDto
    ) : ChatStreamEvent

    data class Failed(
        val runId: String?,
        val code: String,
        val message: String
    ) : ChatStreamEvent
}

interface ChatStreamingClient {
    fun sendMessage(conversationId: String, userMessageId: String, content: String): Flow<ChatStreamEvent>
    fun regenerateLatestAssistant(messageId: String): Flow<ChatStreamEvent>
}

@Singleton
class WorkerStreamingClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : ChatStreamingClient {
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = if (BuildConfig.API_BASE_URL.endsWith("/")) {
        BuildConfig.API_BASE_URL
    } else {
        "${BuildConfig.API_BASE_URL}/"
    }

    override fun sendMessage(conversationId: String, userMessageId: String, content: String): Flow<ChatStreamEvent> {
        val url = "${baseUrl}v1/conversations/$conversationId/messages/stream"
        val body = json.encodeToString(
            SendMessageRequestDto.serializer(),
            SendMessageRequestDto(userMessageId = userMessageId, content = content)
        ).toRequestBody(jsonMediaType)
        return stream(Request.Builder().url(url).post(body).build())
    }

    override fun regenerateLatestAssistant(messageId: String): Flow<ChatStreamEvent> {
        val url = "${baseUrl}v1/messages/$messageId/regenerate/stream"
        return stream(Request.Builder().url(url).post("{}".toRequestBody(jsonMediaType)).build())
    }

    private fun stream(request: Request): Flow<ChatStreamEvent> = callbackFlow {
        val call = okHttpClient.newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            val errorMessage = response.body?.string()?.let { body ->
                runCatching {
                    json.decodeFromString(ErrorResponseDto.serializer(), body).message
                }.getOrNull()
            }
            response.close()
            close(IllegalStateException(errorMessage ?: "Streaming request failed with HTTP ${response.code}."))
            return@callbackFlow
        }

        val body = response.body ?: run {
            response.close()
            close(IllegalStateException("Streaming response was empty."))
            return@callbackFlow
        }

        try {
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank()) continue
                    val event = json.decodeFromString(StreamEventDto.serializer(), payload).toModel()
                    trySend(event)
                }
            }
            response.close()
            close()
        } catch (error: Throwable) {
            response.close()
            close(error)
        }

        awaitClose {
            call.cancel()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun StreamEventDto.toModel(): ChatStreamEvent = when (type) {
        "accepted_send" -> ChatStreamEvent.AcceptedSend(
            runId = requireNotNull(runId),
            conversationVersion = requireNotNull(conversationVersion),
            userMessage = requireNotNull(userMessage),
            assistantMessageId = requireNotNull(assistantMessageId)
        )

        "accepted_regenerate" -> ChatStreamEvent.AcceptedRegenerate(
            runId = requireNotNull(runId),
            conversationVersion = requireNotNull(conversationVersion),
            messageId = requireNotNull(messageId),
            assistantMessageId = requireNotNull(assistantMessageId)
        )

        "delta" -> ChatStreamEvent.Delta(
            runId = requireNotNull(runId),
            textDelta = textDelta.orEmpty()
        )

        "completed_send" -> ChatStreamEvent.CompletedSend(
            runId = requireNotNull(runId),
            conversationVersion = requireNotNull(conversationVersion),
            assistantMessage = requireNotNull(assistantMessage),
            conversationSummary = requireNotNull(conversationSummary)
        )

        "completed_regenerate" -> ChatStreamEvent.CompletedRegenerate(
            runId = requireNotNull(runId),
            conversationVersion = requireNotNull(conversationVersion),
            messageId = requireNotNull(messageId),
            regeneration = requireNotNull(regeneration),
            selectedRegenerationId = requireNotNull(selectedRegenerationId),
            conversationSummary = requireNotNull(conversationSummary)
        )

        "failed" -> ChatStreamEvent.Failed(
            runId = runId,
            code = code ?: "STREAM_FAILED",
            message = message ?: "Streaming failed."
        )

        else -> throw IllegalStateException("Unsupported stream event type: $type")
    }
}
