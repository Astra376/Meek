package com.example.aichat.core.util

fun authorLabel(ownerUserId: String, currentUserId: String): String {
    return when {
        ownerUserId == currentUserId -> "By you"
        ownerUserId == "system" -> "By Character Chat"
        else -> "By $ownerUserId"
    }
}
