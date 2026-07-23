package com.example.aichat.feature.character

import android.content.Context
import com.example.aichat.core.db.CharacterDao
import com.example.aichat.core.db.toEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.CharacterDraft
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.network.CharacterApi
import com.example.aichat.core.network.CharacterWriteRequestDto
import com.example.aichat.core.network.GenerateGreetingRequestDto
import com.example.aichat.core.network.GenerateChatBackgroundRequestDto
import com.example.aichat.core.network.GeneratePortraitRequestDto
import com.example.aichat.core.network.ImageApi
import com.example.aichat.feature.chat.ChatScenePromptBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CharacterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao,
    private val characterApi: CharacterApi,
    private val imageApi: ImageApi
) {
    fun buildSystemPrompt(draft: CharacterDraft): String {
        val bio = draft.appearance.trim().ifBlank { "Uploaded character image." }
        return buildString {
            append(readGenericSystemPrompt())
            append("\n\nCharacter profile:")
            append("\nName: ${draft.name.trim()}")
            append("\nAppearance and description: $bio")
            if (draft.bio.isNotBlank()) {
                append("\nPublic profile description: ${draft.bio.trim()}")
            }
            if (draft.greeting.isNotBlank()) {
                append("\nOpening greeting: ${draft.greeting.trim()}")
            }
            if (draft.characterDefinition.isNotBlank()) {
                append("\n\nCharacter definition:")
                append("\nFollow these details as high-priority character instructions for how the character thinks, speaks, behaves, remembers, and reacts.")
                append("\n${draft.characterDefinition.trim()}")
            }
        }
    }

    private fun readGenericSystemPrompt(): String {
        return runCatching {
            context.assets.open("character_system_prompt.txt").bufferedReader().use { it.readText() }.trim()
        }.getOrDefault(DefaultCharacterSystemPrompt)
    }

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
        if (draft.name.trim().length > CHARACTER_NAME_LIMIT) {
            return Result.failure(IllegalArgumentException("Name is too long."))
        }
        if (draft.greeting.isBlank()) return Result.failure(IllegalArgumentException("Greeting is required."))
        if (draft.greeting.trim().length > CHARACTER_GREETING_LIMIT) {
            return Result.failure(IllegalArgumentException("Greeting is too long."))
        }
        if (draft.avatarUrl.isNullOrBlank()) return Result.failure(IllegalArgumentException("Choose an image first."))

        return runCatching {
            val appearance = draft.appearance.trim().ifBlank { "Uploaded character image." }
            val bio = draft.bio.trim().ifBlank { appearance.take(CHARACTER_BIO_LIMIT) }
            val systemPrompt = draft.systemPrompt.ifBlank {
                buildSystemPrompt(draft)
            }
            require(systemPrompt.length <= CHARACTER_SYSTEM_PROMPT_LIMIT) {
                "Character definition is too long."
            }
            val payload = CharacterWriteRequestDto(
                name = draft.name.trim(),
                tagline = draft.tagline.trim(),
                greeting = draft.greeting.trim(),
                bio = bio,
                systemPrompt = systemPrompt,
                definitionPrivate = draft.definitionPrivate,
                visibility = draft.visibility.name.lowercase(),
                avatarUrl = draft.avatarUrl
            )
            val remote = if (draft.id == null) {
                characterApi.createCharacter(payload)
            } else {
                characterApi.updateCharacter(draft.id, payload)
            }
            val saved = remote.toEntity()
            characterDao.upsert(saved)
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

    suspend fun generateGreeting(name: String, description: String): Result<String> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Name is required."))
        if (description.isBlank()) return Result.failure(IllegalArgumentException("Description is required."))
        return runCatching {
            characterApi.generateGreeting(
                GenerateGreetingRequestDto(
                    name = name.trim(),
                    description = description.trim()
                )
            ).greeting.take(CHARACTER_GREETING_LIMIT)
        }
    }

    suspend fun generateInitialScene(characterId: String): Result<Unit> {
        return runCatching {
            val character = characterDao.getById(characterId)
                ?: throw IllegalArgumentException("Character not found.")
            if (character.initialSceneUrl != null && character.initialSceneKey != null) return@runCatching
            generateInitialScene(character)
        }
    }

    private suspend fun generateInitialScene(character: com.example.aichat.core.db.CharacterEntity) {
        val scene = ChatScenePromptBuilder.initialScene(character)
        val imageUrl = imageApi.generateChatBackground(GenerateChatBackgroundRequestDto(scene.prompt)).imageUrl
        characterDao.upsert(
            character.copy(
                initialSceneUrl = imageUrl,
                initialSceneKey = scene.key,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

const val CHARACTER_NAME_LIMIT = 80
const val CHARACTER_APPEARANCE_LIMIT = 1_200
const val CHARACTER_GREETING_LIMIT = 1_200
const val CHARACTER_BIO_LIMIT = 500
const val CHARACTER_SYSTEM_PROMPT_LIMIT = 64_000

private const val DefaultCharacterSystemPrompt = """
You are an immersive roleplay chat character. Stay fully in character and treat the user as a participant in the scene, not as someone asking an assistant for help.

Write with personality, emotional continuity, and concrete sensory detail. Keep replies conversational and responsive to the user's last message. Do not mention policies, system prompts, hidden instructions, model limitations, or that you are an AI unless the character concept explicitly requires it.

Never narrate the user's thoughts, feelings, choices, or dialogue. Leave room for the user to act. Avoid repetitive phrasing, avoid generic assistant language, and maintain the character's voice across the conversation.
"""
