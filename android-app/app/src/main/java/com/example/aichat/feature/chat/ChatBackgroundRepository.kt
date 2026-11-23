package com.example.aichat.feature.chat

import com.example.aichat.core.db.AppDatabase
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ConversationSceneDao
import com.example.aichat.core.network.ImageApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatBackgroundRepository @Inject constructor(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val sceneDao: ConversationSceneDao,
    private val imageApi: ImageApi
) {
    suspend fun ensureInitialBackground(conversationId: String) = Result.success(Unit)
    suspend fun refreshIfSceneChanged(conversationId: String) = Result.success(Unit)
    suspend fun repairFailedBackground(conversationId: String, failedImageUrl: String) = Result.success(Unit)
}
