package dev.jellystack.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeController(
    initialIsDark: Boolean,
    private val onThemeChanged: ((Boolean) -> Unit)? = null,
) {
    private val _isDark = MutableStateFlow(initialIsDark)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun toggle() {
        set(!_isDark.value)
    }

    fun set(isDark: Boolean) {
        if (_isDark.value == isDark) {
            return
        }
        _isDark.value = isDark
        onThemeChanged?.invoke(isDark)
    }
}

val LocalThemeController =
    staticCompositionLocalOf<ThemeController> {
        error("ThemeController not provided")
    }
