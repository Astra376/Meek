package com.example.aichat.feature.chat

internal object RemoteConversationSyncRules {
    fun shouldSkipRefresh(
        forceWhileStreaming: Boolean,
        hasStreamingDraft: Boolean,
        isSyncSuppressed: Boolean
    ): Boolean {
        if (forceWhileStreaming) return false
        return hasStreamingDraft || isSyncSuppressed
    }
}
