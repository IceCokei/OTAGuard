package com.coke.otaguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.green,
    secondary = DarkPalette.textMuted,
    background = DarkPalette.bg,
    surface = DarkPalette.card,
    onPrimary = DarkPalette.bg,
    onBackground = DarkPalette.text,
    onSurface = DarkPalette.text,
    outline = DarkPalette.border,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.green,
    secondary = LightPalette.textMuted,
    background = LightPalette.bg,
    surface = LightPalette.card,
    onPrimary = LightPalette.bg,
    onBackground = LightPalette.text,
    onSurface = LightPalette.text,
    outline = LightPalette.border,
)

@Composable
fun OTAGuardTheme(isDark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (isDark) DarkPalette else LightPalette
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalOtaColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
