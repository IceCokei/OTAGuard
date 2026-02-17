package com.coke.otaguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Green,
    secondary = TextMuted,
    background = BgBlack,
    surface = CardDark,
    onPrimary = BgBlack,
    onBackground = White,
    onSurface = White,
    outline = Border,
)

@Composable
fun OTAGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
