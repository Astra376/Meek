package com.example.aichat.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.network.ConversationApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.example.aichat.MainActivity
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.db.toEntity
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class OfflinePollingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val conversationApi: ConversationApi,
    private val conversationDao: ConversationDao,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val session = authRepository.sessionState.firstOrNull()
            if (session?.isSignedIn != true) return Result.success()

            val ownerUserId = session.profile?.userId ?: return Result.success()

            val page = conversationApi.getConversations(null)
            var newMessagesCount = 0

            for (dto in page.items) {
                val local = conversationDao.getById(dto.id)
                // If local unreadCount is less than the remote unreadCount, a new message arrived
                if (local != null && dto.unreadCount > local.unreadCount) {
                    newMessagesCount += (dto.unreadCount - local.unreadCount)
                }

                // Update local DB
                conversationDao.upsert(
                    com.example.aichat.core.db.ConversationEntity(
                        id = dto.id,
                        ownerUserId = ownerUserId,
                        characterId = dto.characterId,
                        version = local?.version ?: 0,
                        updatedAt = dto.updatedAt,
                        startedAt = dto.startedAt,
                        lastMessageAt = dto.lastMessageAt,
                        previewText = dto.lastPreview,
                        unreadCount = dto.unreadCount,
                        hasUnreadBadge = dto.hasUnreadBadge
                    )
                )
            }

            if (newMessagesCount > 0) {
                showNotification(newMessagesCount)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun showNotification(newMessagesCount: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "offline_messages_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new chat messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Messages")
            .setContentText("You have $newMessagesCount new unread message" + if (newMessagesCount > 1) "s" else "")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1, notification)
    }
}
