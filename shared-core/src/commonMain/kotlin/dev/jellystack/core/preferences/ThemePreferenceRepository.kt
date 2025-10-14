package dev.jellystack.core.preferences

import com.russhwolf.settings.Settings

private const val KEY_IS_DARK_THEME = "appearance.dark_theme_enabled"

class ThemePreferenceRepository(
    private val settings: Settings,
) {
    fun currentTheme(): Boolean? =
        if (settings.hasKey(KEY_IS_DARK_THEME)) {
            settings.getBoolean(KEY_IS_DARK_THEME, defaultValue = false)
        } else {
            null
        }

    fun setDarkTheme(isDark: Boolean) {
        settings.putBoolean(KEY_IS_DARK_THEME, isDark)
    }
}
