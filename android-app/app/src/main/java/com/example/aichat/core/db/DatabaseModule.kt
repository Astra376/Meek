package com.example.aichat.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val migration10To11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversation_memories` (
                    `conversationId` TEXT NOT NULL,
                    `shortTerm` TEXT NOT NULL,
                    `longTerm` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`conversationId`),
                    FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_memories_conversationId` " +
                    "ON `conversation_memories` (`conversationId`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "character-chat.db"
        )
            .addMigrations(migration10To11)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideCharacterDao(database: AppDatabase): CharacterDao = database.characterDao()

    @Provides
    fun provideConversationSceneDao(database: AppDatabase): ConversationSceneDao = database.conversationSceneDao()

    @Provides
    fun provideConversationMemoryDao(database: AppDatabase): ConversationMemoryDao = database.conversationMemoryDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideAssistantRegenerationDao(database: AppDatabase): AssistantRegenerationDao =
        database.assistantRegenerationDao()
}
