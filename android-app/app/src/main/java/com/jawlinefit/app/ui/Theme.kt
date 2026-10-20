package com.jawlinefit.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF245C4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7E5D4),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4D635B),
    surface = Color(0xFFF7FAF8),
    surfaceVariant = Color(0xFFDDE5E1),
    error = Color(0xFFBA1A1A)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BCFBE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF075143),
    secondary = Color(0xFFB5CCC3),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF404944)
)

@Composable fun JawlineFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
