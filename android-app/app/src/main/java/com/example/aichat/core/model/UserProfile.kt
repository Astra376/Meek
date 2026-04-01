package com.example.aichat.core.model

data class UserProfile(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class AppSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long
)

data class SessionUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val profile: UserProfile? = null
)

