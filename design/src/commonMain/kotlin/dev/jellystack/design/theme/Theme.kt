package dev.jellystack.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val lightColors =
    lightColorScheme(
        primary = Color(0xFF4F9EE3),
        onPrimary = Color.White,
        secondary = Color(0xFF72D572),
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFFFFFF),
    )

private val darkColors =
    darkColorScheme(
        primary = Color(0xFF82C7FF),
        onPrimary = Color(0xFF001E3C),
        secondary = Color(0xFF98EE99),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
    )

@Suppress("ktlint:standard:function-naming")
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Suppress("FunctionName")
@Composable
fun JellystackTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) darkColors else lightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
