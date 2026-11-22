package com.tvunboxed.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2B544),
    onPrimary = Color(0xFF1B1405),
    secondary = Color(0xFF8FD3C7),
    surface = Color(0xFF14161B),
    onSurface = Color(0xFFECEFF4)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB26A00),
    onPrimary = Color.White,
    secondary = Color(0xFF1F6F63),
    surface = Color(0xFFF7F4EC),
    onSurface = Color(0xFF201B12)
)

@Composable
fun TVunboxedTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
