package com.example.aichat.feature.home

import com.example.aichat.BuildConfig
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.toEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CursorPage
import com.example.aichat.core.network.HomeApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val homeApi: HomeApi
) {
    suspend fun loadFeed(cursor: String?, pageSize: Int = 12): CursorPage<CharacterSummary> {
        if (!BuildConfig.USE_MOCK_SERVICES) {
            val page = homeApi.getFeed(cursor = cursor)
            characterDao.upsertAll(page.items.map { it.toEntity() })
            return CursorPage(
                items = page.items.map { it.toEntity().toModel() },
                nextCursor = page.nextCursor
            )
        }

        val ranked = characterDao.getPublicCharacters()
            .sortedWith(
                compareByDescending<com.example.aichat.core.db.CharacterEntity> { it.lastActiveAt }
                    .thenByDescending { it.publicChatCount }
                    .thenByDescending { it.createdAt }
            )
            .map { it.toModel() }
        return ranked.page(cursor = cursor, pageSize = pageSize)
    }

    suspend fun search(query: String, cursor: String?, pageSize: Int = 12): CursorPage<CharacterSummary> {
        if (query.isBlank()) return CursorPage(emptyList(), null)
        if (!BuildConfig.USE_MOCK_SERVICES) {
            val page = homeApi.search(query = query.trim(), cursor = cursor)
            characterDao.upsertAll(page.items.map { it.toEntity() })
            return CursorPage(
                items = page.items.map { it.toEntity().toModel() },
                nextCursor = page.nextCursor
            )
        }

        val ranked = characterDao.searchPublicCharacters(query.trim())
            .sortedWith(
                compareByDescending<com.example.aichat.core.db.CharacterEntity> { it.lastActiveAt }
                    .thenByDescending { it.publicChatCount }
                    .thenByDescending { it.createdAt }
            )
            .map { it.toModel() }
        return ranked.page(cursor = cursor, pageSize = pageSize)
    }

    private fun List<CharacterSummary>.page(cursor: String?, pageSize: Int): CursorPage<CharacterSummary> {
        val startIndex = cursor?.toIntOrNull() ?: 0
        val items = drop(startIndex).take(pageSize)
        val nextCursor = (startIndex + items.size).takeIf { it < size }?.toString()
        return CursorPage(items = items, nextCursor = nextCursor)
    }
}

