package com.tvunboxed.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mixtape palette ported from demo/demo2.html
object TvTokens {
    // dark
    val DarkBg = Color(0xFF0E0F1A)
    val DarkCard = Color(0xFF16182B)
    val DarkMuted = Color(0xFF1F2238)
    val DarkRecess = Color(0xFF0B0C16)
    val DarkLine = Color(0xFF2C3048)
    val DarkBord = Color(0xFF636889)
    val DarkText = Color(0xFFEDEFFC)
    val DarkDim = Color(0xFFA6ACD6)
    val DarkSub = Color(0xFF8E94BD)
    val DarkAccent = Color(0xFFD5CDFF)
    val DarkAccentInk = Color(0xFF2A1159)
    val DarkOk = Color(0xFFC5E17A)
    val DarkInfo = Color(0xFFADDBFF)
    val DarkWarn = Color(0xFFFFE08A)
    val DarkDanger = Color(0xFFFFC3C3)
    val DarkPink = Color(0xFFFFBFD9)

    // light
    val LightBg = Color(0xFFCCCFFA)
    val LightCard = Color.White
    val LightMuted = Color(0xFFF1ECE6)
    val LightRecess = Color(0xFFE9E4DA)
    val LightLine = Color(0xFFDCD3C6)
    val LightBord = Color(0xFF2D241B)
    val LightText = Color(0xFF2D241B)
    val LightDim = Color(0xFF5A5146)
    val LightSub = Color(0xFF6B6154)
    val LightAccent = Color(0xFF2D241B)
    val LightAccentInk = Color(0xFFCCCFFA)
    val LightOk = Color(0xFFC5E17A)
    val LightInfo = Color(0xFFADDBFF)
    val LightWarn = Color(0xFFFFE08A)
    val LightDanger = Color(0xFFFFC3C3)
    val LightPink = Color(0xFFFFBFD9)
}

private val DarkColors = darkColorScheme(
    primary = TvTokens.DarkAccent,
    onPrimary = TvTokens.DarkAccentInk,
    secondary = TvTokens.DarkInfo,
    onSecondary = Color(0xFF003454),
    tertiary = TvTokens.DarkOk,
    background = TvTokens.DarkBg,
    onBackground = TvTokens.DarkText,
    surface = TvTokens.DarkCard,
    onSurface = TvTokens.DarkText,
    surfaceVariant = TvTokens.DarkMuted,
    onSurfaceVariant = TvTokens.DarkDim,
    outline = TvTokens.DarkBord,
    error = TvTokens.DarkDanger
)

private val LightColors = lightColorScheme(
    primary = TvTokens.LightAccent,
    onPrimary = TvTokens.LightAccentInk,
    secondary = TvTokens.LightInfo,
    onSecondary = Color(0xFF003454),
    tertiary = TvTokens.LightOk,
    background = TvTokens.LightBg,
    onBackground = TvTokens.LightText,
    surface = TvTokens.LightCard,
    onSurface = TvTokens.LightText,
    surfaceVariant = TvTokens.LightMuted,
    onSurfaceVariant = TvTokens.LightDim,
    outline = TvTokens.LightBord,
    error = Color(0xFFFFC3C3)
)

@Composable
fun TVunboxedTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}