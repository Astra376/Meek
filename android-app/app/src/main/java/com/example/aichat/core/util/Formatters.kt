package com.example.aichat.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRelativeTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

fun generateId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
