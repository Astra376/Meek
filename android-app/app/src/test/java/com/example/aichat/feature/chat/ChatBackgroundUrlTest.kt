package com.example.aichat.feature.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatBackgroundUrlTest {
    @Test
    fun canonicalizesLegacyAssetUrlWithLiteralSlashes() {
        assertThat(
            canonicalChatBackgroundUrl(
                "https://worker.example/v1/assets/chat-backgrounds/user_1/background_1.jpg"
            )
        ).isEqualTo(
            "https://worker.example/v1/assets/chat-backgrounds%2Fuser_1%2Fbackground_1.jpg"
        )
    }

    @Test
    fun preservesCanonicalEncodedAssetUrl() {
        val url =
            "https://worker.example/v1/assets/chat-backgrounds%2Fuser_1%2Fbackground_1.jpg"

        assertThat(canonicalChatBackgroundUrl(url)).isEqualTo(url)
    }

    @Test
    fun preservesOtherHttpImageUrlsAndRejectsInvalidValues() {
        assertThat(canonicalChatBackgroundUrl("https://cdn.example/scene.jpg"))
            .isEqualTo("https://cdn.example/scene.jpg")
        assertThat(canonicalChatBackgroundUrl("content://local/scene.jpg")).isNull()
        assertThat(canonicalChatBackgroundUrl(null)).isNull()
    }
}
