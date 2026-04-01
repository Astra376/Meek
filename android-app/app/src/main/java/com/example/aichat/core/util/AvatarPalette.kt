package com.example.aichat.core.util

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

fun avatarPalette(seed: String): List<Color> {
    val hash = seed.hashCode().absoluteValue
    val palettes = listOf(
        listOf(Color(0xFF0E7490), Color(0xFF1D4ED8)),
        listOf(Color(0xFF166534), Color(0xFF0F766E)),
        listOf(Color(0xFF9A3412), Color(0xFFC2410C)),
        listOf(Color(0xFF7C2D12), Color(0xFFBE185D)),
        listOf(Color(0xFF4338CA), Color(0xFF0369A1))
    )
    return palettes[hash % palettes.size]
}

