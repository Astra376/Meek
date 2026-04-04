package com.example.aichat.feature.chat

enum class ActiveStreamMode {
    SEND,
    REGENERATE
}

enum class ActiveStreamStatus {
    STREAMING,
    COMPLETED,
    PAUSED
}

data class ActiveAssistantStream(
    val conversationId: String,
    val draftKey: String,
    val runId: String? = null,
    val mode: ActiveStreamMode,
    val assistantMessageId: String? = null,
    val targetMessageId: String? = null,
    val userMessageId: String? = null,
    val text: String = "",
    val accepted: Boolean = false,
    val committedRegenerationId: String? = null,
    val status: ActiveStreamStatus = ActiveStreamStatus.STREAMING
) {
    val isLive: Boolean
        get() = status == ActiveStreamStatus.STREAMING
}
