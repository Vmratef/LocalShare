package com.defname.localshare.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ThemePalette(val label: String, val lightPrimary: Color, val lightSecondary: Color, val lightTertiary: Color, val lightBg: Color, val lightSurface: Color, val darkPrimary: Color, val darkSecondary: Color, val darkTertiary: Color, val darkBg: Color, val darkSurface: Color) {
    CLASSIC("Classic", Color(0xFF3A6EA5), Color(0xFFE49B0F), Color(0xFFADC178), Color(0xFFFBFAF2), Color(0xFFFFF5EE), Color(0xFF9ABCE3), Color(0xFFF0B23A), Color(0xFFBFD18F), Color(0xFF0B1215), Color(0xFF121B21)),
    SUNSET("Sunset", Color(0xFFA84F2A), Color(0xFFCB3534), Color(0xFFE49B0F), Color(0xFFFFF6F0), Color.White, Color(0xFFFFA27F), Color(0xFFFF8A75), Color(0xFFFFC078), Color(0xFF160A08), Color(0xFF24130F)),
    BLOSSOM("Blossom", Color(0xFFB9388E), Color(0xFFE61065), Color(0xFFFF85A2), Color(0xFFFAF3F8), Color.White, Color(0xFFFF8AD1), Color(0xFFFF6FA0), Color(0xFFFFA6C8), Color(0xFF150910), Color(0xFF24121D)),
    OCEAN("Ocean", Color(0xFF0B786E), Color(0xFF0369A1), Color(0xFF3A6EA5), Color(0xFFEFF8F7), Color.White, Color(0xFF75D5C8), Color(0xFF7DD3FC), Color(0xFF82AAFF), Color(0xFF08120F), Color(0xFF0D1D1B)),
    LAVENDER("Lavender", Color(0xFF7B46AF), Color(0xFF9333EA), Color(0xFFB69DFF), Color(0xFFF6F1FB), Color.White, Color(0xFFB69DFF), Color(0xFFC4B5FD), Color(0xFFE0B7FF), Color(0xFF120C18), Color(0xFF1D1526)),
    TOKYO("Tokyo", Color(0xFF0072B2), Color(0xFF1E6FF5), Color(0xFF82AAFF), Color(0xFFF1F5FA), Color.White, Color(0xFF82AAFF), Color(0xFF7DCFFF), Color(0xFFB4C7FF), Color(0xFF0C0E1A), Color(0xFF121525)),
    LIME("Lime", Color(0xFF367A36), Color(0xFF52840B), Color(0xFFADC178), Color(0xFFF1F8F0), Color.White, Color(0xFF7CF7A5), Color(0xFFA3E635), Color(0xFFBFD18F), Color(0xFF0A120D), Color(0xFF101D13)),
    MONOCHROME("Monochrome", Color(0xFF292929), Color(0xFF555555), Color(0xFF777777), Color(0xFFF5F5F5), Color.White, Color(0xFFF2F2F2), Color(0xFFB3B3B3), Color(0xFF888888), Color.Black, Color(0xFF0A0A0A));

    fun colors(dark: Boolean): ColorScheme {
        val p = if (dark) darkPrimary else lightPrimary
        val s = if (dark) darkSecondary else lightSecondary
        val t = if (dark) darkTertiary else lightTertiary
        val bg = if (dark) darkBg else lightBg
        val surface = if (dark) darkSurface else lightSurface
        val ink = if (dark) Color(0xFFEFEFEF) else Color(0xFF172027)
        return if (dark) darkColorScheme(primary=p, onPrimary=Color(0xFF101820), primaryContainer=p.copy(alpha=.28f), onPrimaryContainer=ink, secondary=s, onSecondary=Color(0xFF101820), secondaryContainer=s.copy(alpha=.25f), onSecondaryContainer=ink, tertiary=t, onTertiary=Color(0xFF101820), tertiaryContainer=t.copy(alpha=.24f), onTertiaryContainer=ink, background=bg, onBackground=ink, surface=surface, onSurface=ink, surfaceVariant=surface, onSurfaceVariant=ink.copy(alpha=.78f), surfaceContainerLowest=bg, surfaceContainerLow=surface, surfaceContainer=surface, surfaceContainerHigh=surface, surfaceContainerHighest=surface.copy(alpha=.82f), outline=ink.copy(alpha=.55f), outlineVariant=ink.copy(alpha=.22f))
        else lightColorScheme(primary=p, onPrimary=Color.White, primaryContainer=p.copy(alpha=.14f), onPrimaryContainer=Color(0xFF18232A), secondary=s, onSecondary=Color.White, secondaryContainer=s.copy(alpha=.16f), onSecondaryContainer=Color(0xFF302514), tertiary=t, onTertiary=Color(0xFF172027), tertiaryContainer=t.copy(alpha=.18f), onTertiaryContainer=Color(0xFF202A18), background=bg, onBackground=ink, surface=surface, onSurface=ink, surfaceVariant=Color(0xFFE6E8E8), onSurfaceVariant=Color(0xFF465159), surfaceContainerLowest=Color.White, surfaceContainerLow=bg, surfaceContainer=surface, surfaceContainerHigh=surface, surfaceContainerHighest=Color(0xFFEFEFEF), outline=Color(0xFF77838A), outlineVariant=Color(0xFFD5DADD))
    }
}
