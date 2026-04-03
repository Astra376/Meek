package com.example.aichat

import com.example.aichat.feature.chat.RemoteConversationSyncRules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteConversationSyncRulesTest {
    @Test
    fun skipRefresh_whenSyncIsSuppressedBeforeDraftStarts() {
        val shouldSkip = RemoteConversationSyncRules.shouldSkipRefresh(
            forceWhileStreaming = false,
            hasStreamingDraft = false,
            isSyncSuppressed = true
        )

        assertThat(shouldSkip).isTrue()
    }

    @Test
    fun allowForcedRefresh_evenWhileConversationIsProtected() {
        val shouldSkip = RemoteConversationSyncRules.shouldSkipRefresh(
            forceWhileStreaming = true,
            hasStreamingDraft = true,
            isSyncSuppressed = true
        )

        assertThat(shouldSkip).isFalse()
    }
}
