package com.defname.localshare.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val LocalDarkTheme = staticCompositionLocalOf { false }

fun glassBackgroundBrush(dark: Boolean, palette: ThemePalette = ThemePalette.CLASSIC): Brush {
    val base = if (dark) palette.darkBg else palette.lightBg
    val accent = if (dark) palette.darkPrimary else palette.lightPrimary
    val warm = if (dark) palette.darkSecondary else palette.lightSecondary
    return Brush.linearGradient(
        if (dark) listOf(base, accent.copy(alpha = .30f), Color(0xFF0B1215), warm.copy(alpha = .16f))
        else listOf(accent.copy(alpha = .20f), base, palette.lightSurface, warm.copy(alpha = .14f))
    )
}

@Composable
fun LocalShareTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val isDark = darkTheme ?: when (ThemeController.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val selected = if (isDark) ThemeController.darkPalette else ThemeController.lightPalette
    var scheme = selected.colors(isDark)
    if (isDark && ThemeController.pureBlack) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF101010), surfaceContainerHigh = Color(0xFF171717),
            surfaceContainerHighest = Color(0xFF252525)
        )
    }
    val glass = if (isDark) .58f else .68f
    CompositionLocalProvider(LocalDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = scheme.copy(
                background = Color.Transparent,
                surface = scheme.surface.copy(alpha = glass),
                surfaceVariant = scheme.surfaceVariant.copy(alpha = glass),
                surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = glass),
                surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = glass),
                surfaceContainer = scheme.surfaceContainer.copy(alpha = glass),
                surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = .96f),
                surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = glass)
            ),
            typography = Typography
        ) {
            Box(Modifier.fillMaxSize().background(glassBackgroundBrush(isDark, selected))) { content() }
        }
    }
}

val androidx.compose.material3.ColorScheme.pendingContainer: Color
    @Composable get() = if (LocalDarkTheme.current) AmberContainerDark else AmberContainerLight
val androidx.compose.material3.ColorScheme.onPendingContainer: Color
    @Composable get() = if (LocalDarkTheme.current) OnAmberContainerDark else OnAmberContainerLight
