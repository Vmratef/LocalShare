// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** True when the app is currently drawn in dark mode (respects the in-app toggle). */
val LocalDarkTheme = staticCompositionLocalOf { false }

// Light: steel blue + amber + sage on warm ivory/cream surfaces.
private val LightColorScheme = lightColorScheme(
    primary = PaletteSteel,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8F5),
    onPrimaryContainer = Color(0xFF0F2A47),
    secondary = PaletteAmber,
    onSecondary = PaletteInk,
    secondaryContainer = Color(0xFFFBEBC8),
    onSecondaryContainer = Color(0xFF3D2600),
    tertiary = PaletteSage,
    onTertiary = PaletteInk,
    tertiaryContainer = Color(0xFFE6EED0),
    onTertiaryContainer = Color(0xFF2A3814),
    error = PaletteRed,
    onError = Color.White,
    errorContainer = Color(0xFFF8DAD9),
    onErrorContainer = PaletteDarkRed,
    surface = PaletteCream,
    onSurface = PaletteInk,
    surfaceVariant = PaletteGray,
    onSurfaceVariant = Color(0xFF3F4A52),
    surfaceContainerLowest = PaletteIvory,
    surfaceContainerLow = PaletteCream,
    surfaceContainer = PaletteSeashell,
    surfaceContainerHigh = PaletteMist,
    surfaceContainerHighest = PaletteGray,
    outline = Color(0xFF8A949B),
    outlineVariant = Color(0xFFD5D8D6),
    inversePrimary = PalettePink
)

// Dark: same hues on ink (#0B1215), lighter tints of the palette for text and accents.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FA6D4),
    onPrimary = PaletteInk,
    primaryContainer = Color(0xFF1E3D5F),
    onPrimaryContainer = Color(0xFFDCE8F5),
    secondary = Color(0xFFF0B23A),
    onSecondary = PaletteInk,
    secondaryContainer = Color(0xFF4A3408),
    onSecondaryContainer = Color(0xFFFBEBC8),
    tertiary = Color(0xFFBFD18F),
    onTertiary = PaletteInk,
    tertiaryContainer = Color(0xFF3A4A1F),
    onTertiaryContainer = Color(0xFFE6EED0),
    error = Color(0xFFE8605F),
    onError = PaletteInk,
    errorContainer = Color(0xFF5A1514),
    onErrorContainer = Color(0xFFF8DAD9),
    surface = Color(0xFF121B21),
    onSurface = PaletteGray,
    surfaceVariant = Color(0xFF1B2830),
    onSurfaceVariant = Color(0xFFC7CFD4),
    surfaceContainerLowest = PaletteInk,
    surfaceContainerLow = Color(0xFF101A20),
    surfaceContainer = Color(0xFF16222A),
    surfaceContainerHigh = Color(0xFF1B2830),
    surfaceContainerHighest = Color(0xFF243540),
    outline = Color(0xFF7C8A93),
    outlineVariant = Color(0xFF34444D),
    inversePrimary = PalettePink
)

/** The soft gradient behind all the glass surfaces (also used by the side menu). */
fun glassBackgroundBrush(dark: Boolean): Brush =
    if (dark) {
        Brush.linearGradient(
            listOf(PaletteInk, Color(0xFF16304A), Color(0xFF0E1A22), Color(0xFF2B1417))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFCFE0F2), PaletteMist, PaletteSeashell, Color(0xFFFADCE3))
        )
    }

@Composable
fun LocalShareTheme(
    // null = follow the in-app toggle (ThemeController), which itself defaults to the system
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = darkTheme ?: when (ThemeController.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    // Glass look: a soft gradient behind translucent surfaces.
    val glass = if (isDark) 0.55f else 0.62f
    val glassStrong = 0.95f // dialogs / menus stay readable

    CompositionLocalProvider(LocalDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme.copy(
                background = Color.Transparent,
                surface = colorScheme.surface.copy(alpha = glass),
                surfaceVariant = colorScheme.surfaceVariant.copy(alpha = glass),
                surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = glass),
                surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = glass),
                surfaceContainer = colorScheme.surfaceContainer.copy(alpha = glass),
                surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = glassStrong),
                surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = glass)
            ),
            typography = Typography
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassBackgroundBrush(isDark))
            ) {
                content()
            }
        }
    }
}

// Eigene Erweiterungen für spezielle Status-Farben
val androidx.compose.material3.ColorScheme.pendingContainer: Color
    @Composable
    get() = if (LocalDarkTheme.current) AmberContainerDark else AmberContainerLight

val androidx.compose.material3.ColorScheme.onPendingContainer: Color
    @Composable
    get() = if (LocalDarkTheme.current) OnAmberContainerDark else OnAmberContainerLight
