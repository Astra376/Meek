package com.example.aichat.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.aichat.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2B2B2B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4C4C4C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F4F4),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE9E9E9),
    outline = Color(0xFF8B8B8B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF0F0F0F),
    secondary = Color(0xFFD7D7D7),
    onSecondary = Color(0xFF111111),
    tertiary = Color(0xFFBFBFBF),
    onTertiary = Color(0xFF121212),
    background = Color(0xFF090909),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1E1E),
    outline = Color(0xFF5F5F5F)
)

@Composable
fun AppTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
