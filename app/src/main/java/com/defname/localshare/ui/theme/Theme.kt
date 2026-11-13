// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** True when the app is currently drawn in dark mode (respects the in-app toggle). */
val LocalDarkTheme = staticCompositionLocalOf { false }

// iOS-like palettes
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3A75),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E2D6B),
    onSecondaryContainer = Color(0xFFE0DFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFD1D1D6),
    surfaceContainerLowest = Color(0xFF111113),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF232326),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF48484A),
    error = Color(0xFFFF453A),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E3FF),
    onSecondaryContainer = Color(0xFF1B1A5E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF48484A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF5F5FA),
    surfaceContainerHigh = Color(0xFFEFEFF4),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFC6C6C8),
    error = Color(0xFFFF3B30),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun LocalShareTheme(
    // null = follow the in-app toggle (ThemeController), which itself defaults to the system
    darkTheme: Boolean? = null,
    // iOS look by default. Set to true if you prefer Material You wallpaper colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = darkTheme ?: when (ThemeController.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    // Glass look: a colorful gradient behind translucent surfaces.
    val glass = if (isDark) 0.55f else 0.62f
    val glassStrong = 0.95f // dialogs / menus stay readable
    val glassBackground = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0B1020), Color(0xFF2A2470), Color(0xFF0F4C5C)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFC7D2FE), Color(0xFFE9D5FF), Color(0xFFA5F3FC)))
    }

    CompositionLocalProvider(LocalDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme.copy(
                tertiary = if (isDark) Color(0xFF30D158) else Color(0xFF34C759),
                onTertiary = if (isDark) Color(0xFF003300) else Color.White,
                tertiaryContainer = if (isDark) Color(0xFF1B5E20) else Color(0xFFC8E6C9),
                onTertiaryContainer = if (isDark) Color(0xFFC8E6C9) else Color(0xFF002100),
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
                    .background(glassBackground)
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
