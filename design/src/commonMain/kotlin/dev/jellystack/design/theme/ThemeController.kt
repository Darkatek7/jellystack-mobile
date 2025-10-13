package dev.jellystack.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeController(
    initialIsDark: Boolean,
) {
    private val _isDark = MutableStateFlow(initialIsDark)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun toggle() {
        _isDark.value = !_isDark.value
    }
}

val LocalThemeController =
    staticCompositionLocalOf<ThemeController> {
        error("ThemeController not provided")
    }
