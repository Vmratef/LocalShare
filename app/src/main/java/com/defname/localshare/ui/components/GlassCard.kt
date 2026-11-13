// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.defname.localshare.ui.theme.LocalDarkTheme

/**
 * iOS-style frosted glass card: soft white gradient fill + bright rim light.
 * [tint] adds a faint color wash (e.g. the primary color while sharing is active).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalDarkTheme.current
    val fill = Brush.linearGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))
        else listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.34f))
    )
    val rim = Brush.linearGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.07f))
        else listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.35f))
    )

    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.2.dp, rim)
    ) {
        Column(
            modifier = Modifier
                .background(fill)
                .then(if (tint != Color.Unspecified) Modifier.background(tint.copy(alpha = 0.20f)) else Modifier),
            content = content
        )
    }
}
