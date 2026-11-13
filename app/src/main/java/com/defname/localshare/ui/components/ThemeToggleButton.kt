// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.defname.localshare.ui.theme.LocalDarkTheme
import com.defname.localshare.ui.theme.ThemeController

/** Top-bar button that switches between light and dark mode. */
@Composable
fun ThemeToggleButton() {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    IconButton(onClick = { ThemeController.toggle(context, isDark) }) {
        Icon(
            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode"
        )
    }
}
