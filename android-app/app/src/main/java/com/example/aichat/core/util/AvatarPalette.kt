package com.example.aichat.core.util

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

fun avatarPalette(seed: String): List<Color> {
    val hash = seed.hashCode().absoluteValue
    val palettes = listOf(
        listOf(Color(0xFF111111), Color(0xFF2A2A2A)),
        listOf(Color(0xFF1B1B1B), Color(0xFF4A4A4A)),
        listOf(Color(0xFF2D2D2D), Color(0xFF686868)),
        listOf(Color(0xFF151515), Color(0xFF575757)),
        listOf(Color(0xFF090909), Color(0xFF3C3C3C))
    )
    return palettes[hash % palettes.size]
}
