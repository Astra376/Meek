package com.example.aichat.feature.profile

import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.toEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CursorPage
import com.example.aichat.core.model.PublicProfile
import com.example.aichat.core.network.ProfileApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreatorProfileRepository @Inject constructor(
    private val profileApi: ProfileApi,
    private val characterDao: CharacterDao
) {
    suspend fun getProfile(userId: String): PublicProfile {
        val profile = profileApi.getPublicProfile(userId)
        return PublicProfile(
            userId = profile.userId,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            bio = profile.bio,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
            characterCount = profile.characterCount,
            interactionCount = profile.interactionCount,
            likeCount = profile.likeCount
        )
    }

    suspend fun getCharacters(
        userId: String,
        cursor: String? = null
    ): CursorPage<CharacterSummary> {
        val page = profileApi.getPublicProfileCharacters(userId = userId, cursor = cursor)
        val entities = page.items.map { it.toEntity() }
        characterDao.upsertAll(entities)
        return CursorPage(
            items = entities.map { it.toModel() },
            nextCursor = page.nextCursor
        )
    }
}
