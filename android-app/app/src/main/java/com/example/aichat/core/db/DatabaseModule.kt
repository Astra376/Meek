package com.example.aichat.core.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "character-chat.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideCharacterDao(database: AppDatabase): CharacterDao = database.characterDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideAssistantRegenerationDao(database: AppDatabase): AssistantRegenerationDao =
        database.assistantRegenerationDao()
}

