package com.example.aichat.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.aichat.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1246B9),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0B7D69),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB85B18),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14171D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C25),
    surfaceVariant = Color(0xFFE7ECF6),
    outline = Color(0xFF74829A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB2FF),
    onPrimary = Color(0xFF03205F),
    secondary = Color(0xFF6ED6BF),
    onSecondary = Color(0xFF00382E),
    tertiary = Color(0xFFF0AA77),
    onTertiary = Color(0xFF4C2500),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE9EDF5),
    surface = Color(0xFF141A23),
    onSurface = Color(0xFFE6EBF3),
    surfaceVariant = Color(0xFF202938),
    outline = Color(0xFF8A95A8)
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

