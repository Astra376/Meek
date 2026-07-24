package com.example.aichat.core.model

data class PublicProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val characterCount: Int,
    val interactionCount: Int,
    val likeCount: Int
)
