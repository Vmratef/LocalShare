// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.main.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defname.localshare.R
import com.defname.localshare.ui.screens.main.NavigationItem
import com.defname.localshare.ui.theme.LocalDarkTheme

@Composable
fun MainMenu(
    drawerItems: List<NavigationItem>,
    currentRoute: String,
    onItemClick: (String) -> Unit,
    onHelpClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dark = LocalDarkTheme.current

    // The theme makes surfaces see-through (glass). The side menu must NOT be,
    // otherwise the page behind it shines through. So it gets an (almost) solid color.
    val menuColor = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.97f) else Color(0xFFF2F2F7).copy(alpha = 0.98f)
    val itemSelected = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.28f else 0.16f)

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.75f),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = menuColor,
        drawerTonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(bottom = 64.dp) // Platz für den Hilfe-Button
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineSmall
                )
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        onClick = { onItemClick(item.route) },
                        selected = currentRoute == item.route,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = itemSelected,
                            unselectedContainerColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
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
