package com.example.aichat.feature.character

import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.toEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterDraft
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.network.CharacterApi
import com.example.aichat.core.network.CharacterWriteRequestDto
import com.example.aichat.core.network.GeneratePortraitRequestDto
import com.example.aichat.core.network.ImageApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val characterApi: CharacterApi,
    private val imageApi: ImageApi
) {
    fun observeOwnedCharacters(ownerUserId: String): Flow<List<CharacterSummary>> =
        characterDao.observeOwnedCharacters(ownerUserId).map { list -> list.map { it.toModel() } }

    fun observeLikedCharacters(): Flow<List<CharacterSummary>> =
        characterDao.observeLikedCharacters().map { list -> list.map { it.toModel() } }

    suspend fun getCharacter(characterId: String): CharacterSummary? = characterDao.getById(characterId)?.toModel()

    suspend fun refreshOwnedCharacters(): Result<Unit> {
        return runCatching {
            val page = characterApi.getOwnedCharacters()
            characterDao.upsertAll(page.items.map { it.toEntity() })
        }
    }

    suspend fun refreshLikedCharacters(): Result<Unit> {
        return runCatching {
            val page = characterApi.getLikedCharacters()
            characterDao.upsertAll(page.items.map { it.toEntity() })
        }
    }

    suspend fun saveCharacter(draft: CharacterDraft): Result<String> {
        if (draft.name.isBlank()) return Result.failure(IllegalArgumentException("Name is required."))
        if (draft.tagline.isBlank()) return Result.failure(IllegalArgumentException("Tagline is required."))
        if (draft.systemPrompt.isBlank()) return Result.failure(IllegalArgumentException("System prompt is required."))

        return runCatching {
            val payload = CharacterWriteRequestDto(
                name = draft.name.trim(),
                tagline = draft.tagline.trim(),
                description = draft.description.trim(),
                systemPrompt = draft.systemPrompt.trim(),
                visibility = draft.visibility.name.lowercase(),
                avatarUrl = draft.avatarUrl
            )
            val remote = if (draft.id == null) {
                characterApi.createCharacter(payload)
            } else {
                characterApi.updateCharacter(draft.id, payload)
            }
            characterDao.upsert(remote.toEntity())
            remote.id
        }
    }

    suspend fun toggleLike(characterId: String): Result<Unit> {
        val character = characterDao.getById(characterId) ?: return Result.failure(
            IllegalArgumentException("Character not found.")
        )
        if (character.visibility != CharacterVisibility.PUBLIC.name) {
            return Result.failure(IllegalStateException("Only public characters can be liked."))
        }

        return runCatching {
            if (character.likedByMe) {
                characterApi.unlikeCharacter(characterId)
            } else {
                characterApi.likeCharacter(characterId)
            }
            characterDao.upsert(
                character.copy(
                    likedByMe = !character.likedByMe,
                    likeCount = (character.likeCount + if (character.likedByMe) -1 else 1).coerceAtLeast(0),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun generatePortrait(seedSource: String): Result<String> {
        if (seedSource.isBlank()) return Result.failure(IllegalArgumentException("Add a name or prompt first."))
        return runCatching {
            imageApi.generatePortrait(GeneratePortraitRequestDto(seedSource.trim())).avatarUrl
        }
    }
}
