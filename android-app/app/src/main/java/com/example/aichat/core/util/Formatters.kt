package com.example.aichat.core.util

import java.text.SimpleDateFormat
import java.security.SecureRandom
import java.util.Date
import java.util.Locale

private val crockfordBase32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
private val ulidRandom = SecureRandom()

fun formatRelativeTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

fun formatRelativeTimeWords(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    if (diff < 0) return "Just now"
    
    val minutes = diff / (60 * 1000)
    if (minutes < 60) {
        return if (minutes <= 1) "1 Min" else "$minutes Mins"
    }
    
    val hours = minutes / 60
    if (hours < 24) {
        return if (hours == 1L) "1 Hour" else "$hours Hours"
    }
    
    val days = hours / 24
    if (days < 7) {
        return if (days == 1L) "1 Day" else "$days Days"
    }
    
    val weeks = days / 7
    if (weeks < 4) {
        return if (weeks == 1L) "1 Week" else "$weeks Weeks"
    }
    
    val months = days / 30
    if (months < 12) {
        return if (months == 1L) "1 Month" else "$months Months"
    }
    
    val years = days / 365
    return if (years == 1L) "1 Year" else "$years Years"
}

fun generateUlid(timeMillis: Long = System.currentTimeMillis()): String {
    val randomBytes = ByteArray(10)
    ulidRandom.nextBytes(randomBytes)

    var timestamp = timeMillis
    val chars = CharArray(26)

    for (index in 9 downTo 0) {
        chars[index] = crockfordBase32[(timestamp and 31L).toInt()]
        timestamp = timestamp ushr 5
    }

    var buffer = 0
    var bitsLeft = 0
    var outputIndex = 10
    for (byte in randomBytes) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xff)
        bitsLeft += 8
        while (bitsLeft >= 5 && outputIndex < chars.size) {
            bitsLeft -= 5
            chars[outputIndex] = crockfordBase32[(buffer shr bitsLeft) and 31]
            outputIndex += 1
        }
    }
    if (outputIndex < chars.size) {
        chars[outputIndex] = crockfordBase32[(buffer shl (5 - bitsLeft)) and 31]
    }

    return String(chars)
}
