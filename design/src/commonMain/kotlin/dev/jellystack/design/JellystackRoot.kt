package dev.jellystack.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.design.jellyfin.JellyfinBrowseScreen
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.design.theme.LocalThemeController
import dev.jellystack.design.theme.ThemeController
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackState

private enum class JellystackScreen {
    Home,
    Settings,
}

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
fun JellystackRoot(
    defaultDarkTheme: Boolean = false,
    controller: PlaybackController = PlaybackController(),
) {
    val themeController = remember { ThemeController(defaultDarkTheme) }
    val isDarkTheme by themeController.isDark.collectAsState()
    val playbackState by controller.state.collectAsState()
    val playbackDescription =
        when (val state = playbackState) {
            is PlaybackState.Playing -> "Playing ${state.mediaId} on ${state.deviceName}"
            PlaybackState.Stopped -> "Stopped"
        }
    var currentScreen by remember { mutableStateOf(JellystackScreen.Home) }
    val browseState = remember { JellyfinHomeState() }

    CompositionLocalProvider(LocalThemeController provides themeController) {
        JellystackTheme(isDarkTheme = isDarkTheme) {
            Surface {
                when (currentScreen) {
                    JellystackScreen.Home ->
                        HomeScreen(
                            isDarkTheme = isDarkTheme,
                            playbackStatus = playbackDescription,
                            onToggleTheme = themeController::toggle,
                            onOpenSettings = { currentScreen = JellystackScreen.Settings },
                            browseState = browseState,
                            onSelectLibrary = {},
                            onRefreshLibraries = {},
                            onLoadMore = {},
                            onOpenItemDetail = {},
                        )

                    JellystackScreen.Settings ->
                        SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = themeController::toggle,
                            onBack = { currentScreen = JellystackScreen.Home },
                        )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun HomeScreen(
    isDarkTheme: Boolean,
    playbackStatus: String,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    browseState: JellyfinHomeState,
    onSelectLibrary: (String) -> Unit,
    onRefreshLibraries: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItemDetail: (JellyfinItem) -> Unit,
) {
    JellystackScaffold(
        title = "Jellystack",
        canNavigateBack = false,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssistChip(
                onClick = onOpenSettings,
                label = { Text("Playback: $playbackStatus") },
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .semantics { role = Role.Button }
                        .testTag(JellystackTags.OPEN_SETTINGS),
            )
            JellyfinBrowseScreen(
                state = browseState,
                onSelectLibrary = onSelectLibrary,
                onRefresh = onRefreshLibraries,
                onLoadMore = onLoadMore,
                onOpenDetail = onOpenItemDetail,
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    JellystackScaffold(
        title = "Settings",
        canNavigateBack = true,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
            )
            HorizontalDivider()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { role = Role.Switch },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Dark theme",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    modifier =
                        Modifier
                            .testTag(JellystackTags.THEME_SWITCH)
                            .semantics { contentDescription = "Toggle dark theme" },
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleTheme() },
                )
                Text(
                    text = if (isDarkTheme) "Dark mode enabled" else "Light mode enabled",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
private fun JellystackScaffold(
    title: String,
    canNavigateBack: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(
                            modifier =
                                Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = "Navigate back"
                                },
                            onClick = { onBack?.invoke() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    IconToggleButton(
                        modifier =
                            Modifier
                                .testTag(JellystackTags.THEME_TOGGLE)
                                .semantics {
                                    role = Role.Switch
                                    contentDescription = "Toggle app theme"
                                },
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        content = content,
    )
}

private object JellystackTags {
    const val THEME_TOGGLE = "theme_toggle"
    const val THEME_SWITCH = "theme_switch"
    const val OPEN_SETTINGS = "open_settings"
}
