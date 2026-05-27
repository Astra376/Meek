package com.example.aichat.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        CharacterEntity::class,
        ConversationEntity::class,
        ConversationSceneEntity::class,
        MessageEntity::class,
        AssistantRegenerationEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun characterDao(): CharacterDao
    abstract fun conversationSceneDao(): ConversationSceneDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun assistantRegenerationDao(): AssistantRegenerationDao
}
