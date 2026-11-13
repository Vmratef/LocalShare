// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Holds the user's light/dark choice and remembers it between app starts.
 * Call [init] once (MainActivity.onCreate) before setContent.
 */
object ThemeController {
    private const val PREFS = "localshare_theme"
    private const val KEY_MODE = "theme_mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun init(context: Context) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        mode = runCatching { ThemeMode.valueOf(saved ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, newMode.name)
            .apply()
    }

    /** Flip between light and dark based on what is currently shown. */
    fun toggle(context: Context, currentlyDark: Boolean) {
        set(context, if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }
}
