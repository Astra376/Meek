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

fun generateId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"

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
