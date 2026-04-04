package com.example.aichat.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles LIMIT 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles LIMIT 1")
    suspend fun getProfile(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clear()
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE visibility = 'PUBLIC'")
    suspend fun getPublicCharacters(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :characterId LIMIT 1")
    fun observeById(characterId: String): Flow<CharacterEntity?>

    @Query("SELECT * FROM characters WHERE ownerUserId = :ownerUserId ORDER BY updatedAt DESC")
    fun observeOwnedCharacters(ownerUserId: String): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE likedByMe = 1 AND visibility = 'PUBLIC' ORDER BY updatedAt DESC")
    fun observeLikedCharacters(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :characterId LIMIT 1")
    suspend fun getById(characterId: String): CharacterEntity?

    @Query(
        """
        SELECT * FROM characters
        WHERE visibility = 'PUBLIC' AND (
            name LIKE '%' || :query || '%'
            OR tagline LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
        )
        """
    )
    suspend fun searchPublicCharacters(query: String): List<CharacterEntity>

    @Upsert
    suspend fun upsert(character: CharacterEntity)

    @Upsert
    suspend fun upsertAll(characters: List<CharacterEntity>)

    @Query("DELETE FROM characters")
    suspend fun clear()
}

data class ConversationSummaryRow(
    val id: String,
    val characterId: String,
    val characterName: String,
    val characterAvatarUrl: String?,
    val updatedAt: Long,
    val startedAt: Long,
    val lastMessageAt: Long?,
    val lastPreview: String
)

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT
            conversations.id AS id,
            conversations.characterId AS characterId,
            characters.name AS characterName,
            characters.avatarUrl AS characterAvatarUrl,
            conversations.updatedAt AS updatedAt,
            conversations.startedAt AS startedAt,
            conversations.lastMessageAt AS lastMessageAt,
            conversations.previewText AS lastPreview
        FROM conversations
        INNER JOIN characters ON characters.id = conversations.characterId
        WHERE conversations.ownerUserId = :ownerUserId
        ORDER BY conversations.updatedAt DESC
        """
    )
    fun observeConversationSummaries(ownerUserId: String): Flow<List<ConversationSummaryRow>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    fun observeById(conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getById(conversationId: String): ConversationEntity?

    @Query(
        "SELECT * FROM conversations WHERE ownerUserId = :ownerUserId AND characterId = :characterId LIMIT 1"
    )
    suspend fun findByOwnerAndCharacter(ownerUserId: String, characterId: String): ConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, position ASC
        """
    )
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, position ASC
        """
    )
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND sendState = 'SENT' ORDER BY position ASC")
    suspend fun getCommittedMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND sendState = 'SENT' ORDER BY position DESC LIMIT 1")
    suspend fun getLatestMessage(conversationId: String): MessageEntity?

    @Query("SELECT COALESCE(MIN(position), 0) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMinimumPosition(conversationId: String): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND sendState != 'SENT' ORDER BY createdAt ASC")
    suspend fun getLocalOnlyMessages(conversationId: String): List<MessageEntity>

    @Upsert
    suspend fun insert(message: MessageEntity)

    @Upsert
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Update
    suspend fun updateAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND position > :position")
    suspend fun deleteAfter(conversationId: String, position: Int)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND sendState = 'SENT'")
    suspend fun deleteCommittedByConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface AssistantRegenerationDao {
    @Query(
        """
        SELECT assistant_regenerations.* FROM assistant_regenerations
        INNER JOIN messages ON messages.id = assistant_regenerations.messageId
        WHERE messages.conversationId = :conversationId
        ORDER BY assistant_regenerations.createdAt ASC
        """
    )
    fun observeConversationRegenerations(conversationId: String): Flow<List<AssistantRegenerationEntity>>

    @Query("SELECT * FROM assistant_regenerations WHERE messageId = :messageId ORDER BY createdAt ASC")
    suspend fun getByMessage(messageId: String): List<AssistantRegenerationEntity>

    @Query("SELECT * FROM assistant_regenerations WHERE id = :regenerationId LIMIT 1")
    suspend fun getById(regenerationId: String): AssistantRegenerationEntity?

    @Upsert
    suspend fun insert(regeneration: AssistantRegenerationEntity)

    @Upsert
    suspend fun insertAll(regenerations: List<AssistantRegenerationEntity>)

    @Query("DELETE FROM assistant_regenerations")
    suspend fun clear()
}
