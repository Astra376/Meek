package com.example.aichat.core.network

import com.example.aichat.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ChatStreamingClient {
    fun sendMessage(conversationId: String, content: String): Flow<String>
    fun regenerateLatestAssistant(messageId: String): Flow<String>
}

@Singleton
class WorkerStreamingClient @Inject constructor() : ChatStreamingClient {
    override fun sendMessage(conversationId: String, content: String): Flow<String> = flow {
        error(
            "Streaming client is not wired yet. Set AI_CHAT_USE_MOCK=false and point AI_CHAT_API_BASE_URL at a deployed Worker before using remote chat."
        )
    }

    override fun regenerateLatestAssistant(messageId: String): Flow<String> = flow {
        error(
            "Streaming client is not wired yet. Set AI_CHAT_USE_MOCK=false and point AI_CHAT_API_BASE_URL at a deployed Worker before using remote regeneration."
        )
    }
}

