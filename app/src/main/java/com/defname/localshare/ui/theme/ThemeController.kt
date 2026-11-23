package com.defname.localshare.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeController {
    private const val PREFS = "localshare_theme"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_LIGHT = "light_palette"
    private const val KEY_DARK = "dark_palette"
    private const val KEY_PURE_BLACK = "pure_black"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var lightPalette by mutableStateOf(ThemePalette.CLASSIC)
        private set
    var darkPalette by mutableStateOf(ThemePalette.CLASSIC)
        private set
    var pureBlack by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
        lightPalette = runCatching { ThemePalette.valueOf(prefs.getString(KEY_LIGHT, ThemePalette.CLASSIC.name) ?: ThemePalette.CLASSIC.name) }.getOrDefault(ThemePalette.CLASSIC)
        darkPalette = runCatching { ThemePalette.valueOf(prefs.getString(KEY_DARK, ThemePalette.CLASSIC.name) ?: ThemePalette.CLASSIC.name) }.getOrDefault(ThemePalette.CLASSIC)
        pureBlack = prefs.getBoolean(KEY_PURE_BLACK, false)
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, newMode.name).apply()
    }

    fun setLight(context: Context, palette: ThemePalette) {
        lightPalette = palette
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LIGHT, palette.name).apply()
    }

    fun setDark(context: Context, palette: ThemePalette) {
        darkPalette = palette
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DARK, palette.name).apply()
    }

    fun setPureBlack(context: Context, enabled: Boolean) {
        pureBlack = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
    }

    fun toggle(context: Context, currentlyDark: Boolean) {
        set(context, if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }
}
