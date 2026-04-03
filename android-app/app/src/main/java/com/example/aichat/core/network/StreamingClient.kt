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

interface ChatStreamingClient {
    fun sendMessage(conversationId: String, content: String): Flow<String>
    fun regenerateLatestAssistant(messageId: String): Flow<String>
}

@Singleton
class WorkerStreamingClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : ChatStreamingClient {
    private val jsonMediaType = "application/json".toMediaType()

    override fun sendMessage(conversationId: String, content: String): Flow<String> {
        val url = "${BuildConfig.API_BASE_URL}v1/conversations/$conversationId/messages/stream"
        val body = json.encodeToString(SendMessageRequestDto(content)).toRequestBody(jsonMediaType)
        return stream(Request.Builder().url(url).post(body).build())
    }

    override fun regenerateLatestAssistant(messageId: String): Flow<String> {
        val url = "${BuildConfig.API_BASE_URL}v1/messages/$messageId/regenerate/stream"
        return stream(Request.Builder().url(url).post("{}".toRequestBody(jsonMediaType)).build())
    }

    private fun stream(request: Request): Flow<String> = callbackFlow {
        val call = okHttpClient.newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            response.close()
            close(IllegalStateException("Streaming request failed with HTTP ${response.code}."))
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
                    val event = json.decodeFromString<StreamEventDto>(payload)
                    when (event.type) {
                        "chunk" -> {
                            val text = event.text ?: ""
                            if (text.isNotEmpty()) trySend(text)
                        }
                        "done" -> break
                    }
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
}

