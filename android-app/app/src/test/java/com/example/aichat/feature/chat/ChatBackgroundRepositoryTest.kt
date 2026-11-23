package com.example.aichat.feature.chat

import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatBackgroundRepositoryTest {
    @Test
    fun automaticSceneOperationsCompleteWithoutAnImageProvider() = runTest {
        val repository = ChatBackgroundRepository()
        repository.ensureInitialBackground("chat").getOrThrow()
        repository.refreshIfSceneChanged("chat").getOrThrow()
        repository.repairFailedBackground("chat", "https://example.invalid/scene.jpg").getOrThrow()
    }
}
