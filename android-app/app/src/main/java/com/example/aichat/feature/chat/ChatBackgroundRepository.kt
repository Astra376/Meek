package com.example.aichat.feature.chat

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("UNUSED_PARAMETER")
class ChatBackgroundRepository @Inject constructor() {
    suspend fun ensureInitialBackground(conversationId: String) = Result.success(Unit)
    suspend fun refreshIfSceneChanged(conversationId: String) = Result.success(Unit)
    suspend fun repairFailedBackground(conversationId: String, failedImageUrl: String) = Result.success(Unit)
}
