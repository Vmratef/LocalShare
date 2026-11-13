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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun LocalShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Glass look: a colorful gradient behind translucent surfaces.
    val glass = if (darkTheme) 0.45f else 0.55f
    val glassStrong = 0.92f // dialogs / menus stay readable
    val glassBackground = if (darkTheme) {
        Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF134E4A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFC7D2FE), Color(0xFFE9D5FF), Color(0xFFA5F3FC)))
    }

    MaterialTheme(
        colorScheme = colorScheme.copy(
            tertiary = if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32),
            onTertiary = if (darkTheme) Color(0xFF003300) else Color.White,
            tertiaryContainer = if (darkTheme) Color(0xFF1B5E20) else Color(0xFFC8E6C9),
            onTertiaryContainer = if (darkTheme) Color(0xFFC8E6C9) else Color(0xFF002100),
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

// Eigene Erweiterungen für spezielle Status-Farben
val androidx.compose.material3.ColorScheme.pendingContainer: Color
    @Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) AmberContainerDark else AmberContainerLight

val androidx.compose.material3.ColorScheme.onPendingContainer: Color
    @Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) OnAmberContainerDark else OnAmberContainerLight