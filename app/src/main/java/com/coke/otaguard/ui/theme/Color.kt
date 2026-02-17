package com.coke.otaguard.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class OtaColors(
    val bg: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val textDim: Color,
    val textDark: Color,
    val green: Color,
    val greenBg: Color,
    val red: Color,
    val redBg: Color,
)

val DarkPalette = OtaColors(
    bg = Color(0xFF0A0A0A),
    card = Color(0xFF18181B),
    border = Color(0xFF27272A),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFA1A1AA),
    textDim = Color(0xFF71717A),
    textDark = Color(0xFF52525B),
    green = Color(0xFF22C55E),
    greenBg = Color(0xFF052E16),
    red = Color(0xFFEF4444),
    redBg = Color(0xFF450A0A),
)

val LightPalette = OtaColors(
    bg = Color(0xFFF4F4F5),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE4E4E7),
    text = Color(0xFF09090B),
    textMuted = Color(0xFF52525B),
    textDim = Color(0xFF71717A),
    textDark = Color(0xFFA1A1AA),
    green = Color(0xFF16A34A),
    greenBg = Color(0xFFDCFCE7),
    red = Color(0xFFDC2626),
    redBg = Color(0xFFFEE2E2),
)

val LocalOtaColors = staticCompositionLocalOf { DarkPalette }
