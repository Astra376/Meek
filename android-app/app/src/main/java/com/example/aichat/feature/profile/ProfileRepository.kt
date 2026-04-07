package com.example.aichat.feature.profile

import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.ConversationDao
import com.example.aichat.core.db.ProfileDao
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class ProfileStats(
    val ownedCount: Int,
    val likedCount: Int,
    val conversationCount: Int
)

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val characterDao: CharacterDao,
    private val conversationDao: ConversationDao
) {
    val profile: Flow<UserProfile?> = profileDao.observeProfile().map { it?.toModel() }

    fun stats(userId: String): Flow<ProfileStats> {
        return combine(
            characterDao.observeOwnedCharacters(userId),
            characterDao.observeLikedCharacters(),
            conversationDao.observeConversationSummaries(userId)
        ) { owned, liked, conversations ->
            ProfileStats(
                ownedCount = owned.size,
                likedCount = liked.size,
                conversationCount = conversations.size
            )
        }
    }

    suspend fun updateProfile(name: String, bio: String): Result<Unit> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Display name can't be empty."))
        val current = profileDao.getProfile() ?: return Result.failure(IllegalStateException("No active profile."))
        profileDao.upsert(current.copy(displayName = name.trim(), bio = bio.trim(), updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }
}

