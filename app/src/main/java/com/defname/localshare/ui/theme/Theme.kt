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

val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE46B68),
    onPrimary = Color(0xFF2A0908),
    primaryContainer = Color(0xFF641F1D),
    onPrimaryContainer = Color(0xFFFFDAD7),

    secondary = Color(0xFF8DB6DE),
    onSecondary = Color(0xFF102B43),
    secondaryContainer = Color(0xFF294B69),
    onSecondaryContainer = Color(0xFFD5E9FF),

    tertiary = Color(0xFFC1D493),
    onTertiary = Color(0xFF25320F),
    tertiaryContainer = Color(0xFF3D4B27),
    onTertiaryContainer = Color(0xFFDDECC0),

    background = BrandNight,
    onBackground = Color(0xFFE8EEF0),

    surface = Color(0xFF151F23),
    onSurface = Color(0xFFE8EEF0),
    surfaceVariant = Color(0xFF243238),
    onSurfaceVariant = Color(0xFFC0CDD1),

    surfaceContainerLowest = Color(0xFF0B1215),
    surfaceContainerLow = Color(0xFF121B1F),
    surfaceContainer = Color(0xFF192429),
    surfaceContainerHigh = Color(0xFF202D33),
    surfaceContainerHighest = Color(0xFF29383E),

    outline = Color(0xFF71838A),
    outlineVariant = Color(0xFF3A4A50),

    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF641F1D),
    onErrorContainer = Color(0xFFFFDAD7)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1DE),
    onPrimaryContainer = Color(0xFF5C1110),

    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBFA),
    onSecondaryContainer = Color(0xFF102E4B),

    tertiary = Color(0xFF718A3F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4EDCF),
    onTertiaryContainer = Color(0xFF263512),

    background = BrandPaper,
    onBackground = Color(0xFF202729),

    surface = BrandIvory,
    onSurface = Color(0xFF202729),
    surfaceVariant = BrandCool,
    onSurfaceVariant = Color(0xFF4E5B60),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = BrandIvory,
    surfaceContainer = BrandPaper,
    surfaceContainerHigh = BrandMist,
    surfaceContainerHighest = Color(0xFFE5E8E6),

    outline = Color(0xFF879397),
    outlineVariant = Color(0xFFD0D8D8),

    error = BrandRedDeep,
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF5C1110)
)

fun glassBackgroundBrush(dark: Boolean): Brush =
    if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF0B1215),
                Color(0xFF17242A),
                Color(0xFF18232D)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFFFDF0),
                Color(0xFFF2F6FC),
                Color(0xFFFFF5EE)
            )
        )
    }

@Composable
fun LocalShareTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = darkTheme ?: when (ThemeController.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = if (isDark) DarkColorScheme else LightColorScheme
    val glassAlpha = if (isDark) 0.78f else 0.82f

    CompositionLocalProvider(LocalDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = baseScheme.copy(
                background = Color.Transparent,
                surface = baseScheme.surface.copy(alpha = glassAlpha),
                surfaceVariant = baseScheme.surfaceVariant.copy(alpha = glassAlpha),
                surfaceContainerLowest =
                    baseScheme.surfaceContainerLowest.copy(alpha = glassAlpha),
                surfaceContainerLow =
                    baseScheme.surfaceContainerLow.copy(alpha = glassAlpha),
                surfaceContainer =
                    baseScheme.surfaceContainer.copy(alpha = glassAlpha),
                surfaceContainerHigh =
                    baseScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                surfaceContainerHighest =
                    baseScheme.surfaceContainerHighest.copy(alpha = glassAlpha)
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

val androidx.compose.material3.ColorScheme.pendingContainer: Color
    @Composable
    get() = if (LocalDarkTheme.current)
        AmberContainerDark else AmberContainerLight

val androidx.compose.material3.ColorScheme.onPendingContainer: Color
    @Composable
    get() = if (LocalDarkTheme.current)
        OnAmberContainerDark else OnAmberContainerLight
