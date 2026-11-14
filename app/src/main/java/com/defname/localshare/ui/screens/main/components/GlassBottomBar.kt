// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defname.localshare.ui.theme.LocalDarkTheme
import com.defname.localshare.ui.theme.PaletteInk
import com.defname.localshare.ui.theme.PaletteIvory
import com.defname.localshare.ui.theme.PaletteMist

data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Floating iOS-style "glass" tab bar (Home / Files / Transfers).
 */
@Composable
fun GlassBottomBar(
    items: List<BottomBarItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalDarkTheme.current
    val glassColor = if (dark) PaletteInk.copy(alpha = 0.55f) else PaletteIvory.copy(alpha = 0.72f)
    val borderBrush = Brush.verticalGradient(
        if (dark) listOf(PaletteMist.copy(alpha = 0.30f), PaletteMist.copy(alpha = 0.06f))
        else listOf(Color.White.copy(alpha = 0.95f), PaletteIvory.copy(alpha = 0.40f))
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = glassColor,
        border = BorderStroke(1.dp, borderBrush),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.28f else 0.16f)
                    else Color.Transparent,
                    label = "tabBg"
                )
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    label = "tabTint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(bg)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onItemClick(item.route) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
