package com.example.aichat.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        CharacterEntity::class,
        ConversationEntity::class,
        ConversationSceneEntity::class,
        ConversationMemoryEntity::class,
        MessageEntity::class,
        AssistantRegenerationEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun characterDao(): CharacterDao
    abstract fun conversationSceneDao(): ConversationSceneDao
    abstract fun conversationMemoryDao(): ConversationMemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun assistantRegenerationDao(): AssistantRegenerationDao
}
