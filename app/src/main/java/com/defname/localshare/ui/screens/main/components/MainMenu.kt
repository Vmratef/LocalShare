// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defname.localshare.R
import com.defname.localshare.ui.screens.main.NavigationItem
import com.defname.localshare.ui.theme.LocalDarkTheme
import com.defname.localshare.ui.theme.glassBackgroundBrush

@Composable
fun MainMenu(
    drawerItems: List<NavigationItem>,
    currentRoute: String,
    onItemClick: (String) -> Unit,
    onHelpClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dark = LocalDarkTheme.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)

    // Same colorful gradient as the home screen, but fully opaque: nothing of the page behind
    // the menu shines through any more. The "glass" comes from the translucent items on top.
    val rim = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
        else listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.30f))
    )
    val itemGlass = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.62f)

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.78f),
        drawerShape = shape,
        drawerContainerColor = Color.Transparent,
        drawerTonalElevation = 0.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassBackgroundBrush(dark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .padding(bottom = 64.dp) // Platz für den Hilfe-Button
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.app_name),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        color = textColor
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = textColor.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(16.dp))

                    drawerItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            onClick = { onItemClick(item.route) },
                            selected = currentRoute == item.route,
                            shape = RoundedCornerShape(22.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = itemGlass,
                                unselectedContainerColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = textColor,
                                unselectedTextColor = textColor
                            ),
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }

                IconButton(
                    onClick = onHelpClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
