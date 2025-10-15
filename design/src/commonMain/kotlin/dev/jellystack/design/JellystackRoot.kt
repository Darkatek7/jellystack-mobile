package dev.jellystack.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.preferences.ThemePreferenceRepository
import dev.jellystack.core.server.ConnectivityException
import dev.jellystack.core.server.CredentialInput
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerRegistration
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.design.jellyfin.JellyfinBrowseScreen
import dev.jellystack.design.jellyfin.JellyfinDetailContent
import dev.jellystack.design.jellyfin.buildSeasonEpisodes
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.design.theme.LocalThemeController
import dev.jellystack.design.theme.ThemeController
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.SubtitleTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class JellystackScreen {
    Home,
    Detail,
}

private enum class JellystackTab {
    Home,
    Library,
    Media,
}

private sealed interface JellyfinDetailUiState {
    data object Hidden : JellyfinDetailUiState

    data class Loading(
        val item: JellyfinItem,
        val imageBaseUrl: String?,
        val imageAccessToken: String?,
    ) : JellyfinDetailUiState

    data class Loaded(
        val item: JellyfinItem,
        val detail: JellyfinItemDetail,
        val imageBaseUrl: String?,
        val imageAccessToken: String?,
    ) : JellyfinDetailUiState

    data class Error(
        val item: JellyfinItem,
        val message: String,
        val imageBaseUrl: String?,
        val imageAccessToken: String?,
    ) : JellyfinDetailUiState
}

private fun JellyfinDetailUiState.withImageInfo(
    imageBaseUrl: String?,
    imageAccessToken: String?,
): JellyfinDetailUiState =
    when (this) {
        JellyfinDetailUiState.Hidden -> this
        is JellyfinDetailUiState.Error -> copy(imageBaseUrl = imageBaseUrl, imageAccessToken = imageAccessToken)
        is JellyfinDetailUiState.Loaded -> copy(imageBaseUrl = imageBaseUrl, imageAccessToken = imageAccessToken)
        is JellyfinDetailUiState.Loading -> copy(imageBaseUrl = imageBaseUrl, imageAccessToken = imageAccessToken)
    }

private data class ServerFormState(
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isValid: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

private data class ServerManagementUiState(
    val servers: List<ManagedServer> = emptyList(),
    val isDialogOpen: Boolean = false,
    val form: ServerFormState = ServerFormState(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@Suppress("FunctionName", "ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellystackRoot(
    defaultDarkTheme: Boolean = false,
    controller: PlaybackController? = null,
) {
    if (!JellystackDI.isStarted()) {
        JellystackPreviewRoot(defaultDarkTheme, controller ?: PlaybackController())
        return
    }

    val koin = remember { JellystackDI.koin }
    val playbackController =
        remember(controller, koin) {
            controller ?: PlaybackController()
        }
    val themePreferences = remember(koin) { koin.get<ThemePreferenceRepository>() }
    val environmentProvider = remember(koin) { koin.get<JellyfinEnvironmentProvider>() }
    val themeController =
        remember(themePreferences, defaultDarkTheme) {
            val initialTheme = themePreferences.currentTheme() ?: defaultDarkTheme
            ThemeController(initialTheme, onThemeChanged = themePreferences::setDarkTheme)
        }
    val isDarkTheme by themeController.isDark.collectAsState()
    val playbackState by playbackController.state.collectAsState()
    val playbackDescription =
        when (val state = playbackState) {
            is PlaybackState.Playing -> {
                val modeLabel =
                    when (state.source.mode) {
                        PlaybackMode.DIRECT -> "Direct play"
                        PlaybackMode.HLS -> "HLS"
                    }
                "Playing ${state.mediaId} ($modeLabel) on ${state.deviceName}"
            }
            PlaybackState.Stopped -> "Stopped"
        }
    var currentScreen by remember { mutableStateOf(JellystackScreen.Home) }
    var currentTab by remember { mutableStateOf(JellystackTab.Home) }
    var detailState by remember { mutableStateOf<JellyfinDetailUiState>(JellyfinDetailUiState.Hidden) }
    var detailJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val browseRepository = remember { koin.get<JellyfinBrowseRepository>() }
    val serverRepository = remember { koin.get<ServerRepository>() }
    val managedServers by serverRepository.observeServers().collectAsState()
    val browseCoordinator =
        remember(browseRepository, coroutineScope) {
            JellyfinBrowseCoordinator(browseRepository, coroutineScope)
        }
    val browseState by browseCoordinator.state.collectAsState()

    var showAddServerDialog by remember { mutableStateOf(false) }
    var serverFormState by remember { mutableStateOf(ServerFormState()) }
    var isSavingServer by remember { mutableStateOf(false) }
    var serverErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    val activePlaybackForDetail =
        (playbackState as? PlaybackState.Playing)?.takeIf {
            val loaded = detailState as? JellyfinDetailUiState.Loaded
            loaded != null && it.mediaId == loaded.detail.id
        }

    val serverUiState =
        ServerManagementUiState(
            servers = managedServers,
            isDialogOpen = showAddServerDialog,
            form = serverFormState,
            isSaving = isSavingServer,
            errorMessage = serverErrorMessage,
        )

    val playbackAction: (JellyfinItem, JellyfinItemDetail) -> Unit = { item, detail ->
        coroutineScope.launch {
            val environment = environmentProvider.current()
            if (environment != null) {
                playbackController.play(PlaybackRequest.from(item, detail), environment)
            } else {
                serverErrorMessage = "Connect a Jellyfin server to start playback."
                isSettingsOpen = true
            }
        }
    }

    val onSelectLibrary: (String) -> Unit = browseCoordinator::selectLibrary
    val onRefreshLibraries: () -> Unit = { browseCoordinator.bootstrap(forceRefresh = true) }
    val onLoadMore: () -> Unit = browseCoordinator::loadNextPage

    val openAddServerDialog: () -> Unit = {
        serverErrorMessage = null
        serverFormState = ServerFormState()
        showAddServerDialog = true
    }

    val dismissAddServerDialog = dismissAddServerDialog@{
        if (isSavingServer) return@dismissAddServerDialog
        showAddServerDialog = false
        serverFormState = ServerFormState()
        serverErrorMessage = null
    }

    val loadDetail: (JellyfinItem, Boolean) -> Unit = { item, forceRefresh ->
        val baseUrl = browseCoordinator.state.value.imageBaseUrl
        val accessToken = browseCoordinator.state.value.imageAccessToken
        detailState = JellyfinDetailUiState.Loading(item, baseUrl, accessToken)
        detailJob?.cancel()
        val job =
            coroutineScope.launch {
                try {
                    val detail = browseRepository.getItemDetail(item.id, forceRefresh)
                    if (detail != null) {
                        detailState =
                            JellyfinDetailUiState.Loaded(
                                item = item,
                                detail = detail,
                                imageBaseUrl = browseCoordinator.state.value.imageBaseUrl,
                                imageAccessToken = browseCoordinator.state.value.imageAccessToken,
                            )
                    } else {
                        detailState =
                            JellyfinDetailUiState.Error(
                                item = item,
                                message = "Item detail unavailable",
                                imageBaseUrl = browseCoordinator.state.value.imageBaseUrl,
                                imageAccessToken = browseCoordinator.state.value.imageAccessToken,
                            )
                    }
                } catch (t: Throwable) {
                    detailState =
                        JellyfinDetailUiState.Error(
                            item = item,
                            message = t.message ?: "Failed to load item detail",
                            imageBaseUrl = browseCoordinator.state.value.imageBaseUrl,
                            imageAccessToken = browseCoordinator.state.value.imageAccessToken,
                        )
                }
            }
        detailJob = job
        job.invokeOnCompletion { detailJob = null }
    }

    val onOpenItemDetail: (JellyfinItem) -> Unit = { item ->
        currentScreen = JellystackScreen.Detail
        loadDetail(item, false)
    }

    val submitServer = submitServer@{
        if (isSavingServer) return@submitServer
        val form = serverFormState
        if (!form.isValid) {
            serverErrorMessage = "All fields are required"
            return@submitServer
        }
        coroutineScope.launch {
            isSavingServer = true
            serverErrorMessage = null
            JellystackLog.d("Submitting Jellyfin server connection to ${form.baseUrl} as ${form.username}")
            try {
                serverRepository.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = form.name,
                        baseUrl = form.baseUrl,
                        credentials =
                            CredentialInput.Jellyfin(
                                username = form.username,
                                password = form.password,
                                deviceId = null,
                            ),
                    ),
                )
                browseCoordinator.bootstrap(forceRefresh = true)
                serverFormState = ServerFormState()
                showAddServerDialog = false
                JellystackLog.d("Jellyfin server connected: ${form.baseUrl}")
            } catch (t: Throwable) {
                serverErrorMessage =
                    when (t) {
                        is ConnectivityException ->
                            listOfNotNull(t.message, t.cause?.message)
                                .joinToString(separator = ": ")
                                .ifBlank { "Unable to connect to server" }
                        else -> t.message ?: "Unable to connect to server"
                    }
                JellystackLog.e("Failed to connect to Jellyfin server ${form.baseUrl}: $serverErrorMessage", t)
            } finally {
                isSavingServer = false
            }
        }
    }

    val removeServer: (ManagedServer) -> Unit = { server ->
        coroutineScope.launch {
            try {
                serverRepository.remove(server.id)
                if (server.type == ServerType.JELLYFIN) {
                    browseCoordinator.bootstrap(forceRefresh = true)
                }
                serverErrorMessage = null
            } catch (t: Throwable) {
                serverErrorMessage = t.message ?: "Failed to remove server"
            }
        }
    }

    val onRetryDetail: () -> Unit = {
        when (val state = detailState) {
            is JellyfinDetailUiState.Error -> loadDetail(state.item, true)
            is JellyfinDetailUiState.Loaded -> loadDetail(state.item, true)
            JellyfinDetailUiState.Hidden,
            is JellyfinDetailUiState.Loading,
            -> Unit
        }
    }

    val onBackFromDetail: () -> Unit = {
        detailJob?.cancel()
        detailState = JellyfinDetailUiState.Hidden
        currentScreen = JellystackScreen.Home
    }

    LaunchedEffect(serverRepository) {
        serverRepository
            .observeServers()
            .map { servers -> servers.firstOrNull { it.type == ServerType.JELLYFIN }?.id }
            .distinctUntilChanged()
            .collect { serverId ->
                if (serverId != null) {
                    browseCoordinator.bootstrap(forceRefresh = true)
                } else {
                    detailState = JellyfinDetailUiState.Hidden
                    currentScreen = JellystackScreen.Home
                    browseCoordinator.bootstrap(forceRefresh = false)
                }
            }
    }

    LaunchedEffect(browseState.imageBaseUrl, browseState.imageAccessToken) {
        detailState = detailState.withImageInfo(browseState.imageBaseUrl, browseState.imageAccessToken)
    }
    CompositionLocalProvider(LocalThemeController provides themeController) {
        JellystackTheme(isDarkTheme = isDarkTheme) {
            Surface {
                val topBarTitle =
                    when (currentScreen) {
                        JellystackScreen.Home ->
                            when (currentTab) {
                                JellystackTab.Home -> "Jellyfin"
                                JellystackTab.Library -> "Library"
                                JellystackTab.Media -> "Media"
                            }
                        JellystackScreen.Detail ->
                            when (val state = detailState) {
                                is JellyfinDetailUiState.Loaded -> state.detail.name
                                is JellyfinDetailUiState.Error -> state.item.name
                                is JellyfinDetailUiState.Loading -> state.item.name
                                JellyfinDetailUiState.Hidden -> "Details"
                            }
                    }
                val canNavigateBack = currentScreen != JellystackScreen.Home

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = topBarTitle) },
                            navigationIcon = {
                                if (canNavigateBack) {
                                    IconButton(
                                        modifier =
                                            Modifier.semantics {
                                                role = Role.Button
                                                contentDescription = "Navigate back"
                                            },
                                        onClick = onBackFromDetail,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    modifier = Modifier.testTag(JellystackTags.OPEN_SETTINGS),
                                    onClick = {
                                        isSettingsOpen = true
                                        serverErrorMessage = null
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Open settings",
                                    )
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (currentScreen == JellystackScreen.Home) {
                            JellystackBottomBar(
                                currentTab = currentTab,
                                onTabSelected = { selected ->
                                    currentTab = selected
                                },
                            )
                        }
                    },
                ) { padding ->
                    when (currentScreen) {
                        JellystackScreen.Home ->
                            when (currentTab) {
                                JellystackTab.Home ->
                                    HomeContent(
                                        browseState = browseState,
                                        onSelectLibrary = onSelectLibrary,
                                        onRefreshLibraries = onRefreshLibraries,
                                        onLoadMore = onLoadMore,
                                        onOpenItemDetail = onOpenItemDetail,
                                        onAddServer = {
                                            isSettingsOpen = true
                                            openAddServerDialog()
                                        },
                                        modifier = Modifier.padding(padding),
                                    )

                                JellystackTab.Library ->
                                    LibraryContent(
                                        browseState = browseState,
                                        onSelectLibrary = onSelectLibrary,
                                        onRefreshLibraries = onRefreshLibraries,
                                        onLoadMore = onLoadMore,
                                        onOpenItemDetail = onOpenItemDetail,
                                        onAddServer = {
                                            isSettingsOpen = true
                                            openAddServerDialog()
                                        },
                                        showLibrarySelector = true,
                                        modifier = Modifier.padding(padding),
                                    )

                                JellystackTab.Media ->
                                    MediaPlaceholder(
                                        modifier =
                                            Modifier
                                                .padding(padding)
                                                .fillMaxSize(),
                                    )
                            }

                        JellystackScreen.Detail ->
                            DetailContent(
                                state = detailState,
                                libraryItems = browseState.libraryItems,
                                onRetry = onRetryDetail,
                                onPlay = playbackAction,
                                audioTracks = activePlaybackForDetail?.stream?.audioTracks ?: emptyList(),
                                selectedAudioTrack = activePlaybackForDetail?.audioTrack,
                                onSelectAudioTrack = { track -> playbackController.selectAudioTrack(track.id) },
                                subtitleTracks = activePlaybackForDetail?.stream?.subtitleTracks ?: emptyList(),
                                selectedSubtitleTrack = activePlaybackForDetail?.subtitleTrack,
                                onSelectSubtitleTrack = { track -> playbackController.selectSubtitle(track?.id) },
                                modifier = Modifier.padding(padding),
                            )
                    }

                    if (isSettingsOpen) {
                        SettingsDialog(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = themeController::toggle,
                            serverState = serverUiState,
                            onOpenAddServer = {
                                openAddServerDialog()
                            },
                            onDismissAddServer = {
                                dismissAddServerDialog()
                            },
                            onUpdateServerForm = { serverFormState = it },
                            onSubmitServer = submitServer,
                            onRemoveServer = removeServer,
                            onClearServerError = { serverErrorMessage = null },
                            onClose = {
                                showAddServerDialog = false
                                serverFormState = ServerFormState()
                                serverErrorMessage = null
                                isSettingsOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionName")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JellystackPreviewRoot(
    defaultDarkTheme: Boolean,
    controller: PlaybackController,
) {
    val themeController = remember { ThemeController(defaultDarkTheme) }
    val isDarkTheme by themeController.isDark.collectAsState()
    val playbackState by controller.state.collectAsState()
    val playbackDescription =
        when (val state = playbackState) {
            is PlaybackState.Playing -> {
                val modeLabel =
                    when (state.source.mode) {
                        PlaybackMode.DIRECT -> "Direct play"
                        PlaybackMode.HLS -> "HLS"
                    }
                "Playing ${state.mediaId} ($modeLabel) on ${state.deviceName}"
            }
            PlaybackState.Stopped -> "Stopped"
        }
    var currentScreen by remember { mutableStateOf(JellystackScreen.Home) }
    var currentTab by remember { mutableStateOf(JellystackTab.Home) }
    var detailState by remember { mutableStateOf<JellyfinDetailUiState>(JellyfinDetailUiState.Hidden) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    val browseState = remember { JellyfinHomeState() }

    CompositionLocalProvider(LocalThemeController provides themeController) {
        JellystackTheme(isDarkTheme = isDarkTheme) {
            Surface {
                val topBarTitle =
                    when (currentScreen) {
                        JellystackScreen.Home ->
                            when (currentTab) {
                                JellystackTab.Home -> "Jellyfin"
                                JellystackTab.Library -> "Library"
                                JellystackTab.Media -> "Media"
                            }
                        JellystackScreen.Detail ->
                            when (val state = detailState) {
                                is JellyfinDetailUiState.Loaded -> state.detail.name
                                is JellyfinDetailUiState.Error -> state.item.name
                                is JellyfinDetailUiState.Loading -> state.item.name
                                JellyfinDetailUiState.Hidden -> "Details"
                            }
                    }
                val canNavigateBack = currentScreen != JellystackScreen.Home

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = topBarTitle) },
                            navigationIcon = {
                                if (canNavigateBack) {
                                    IconButton(
                                        modifier =
                                            Modifier.semantics {
                                                role = Role.Button
                                                contentDescription = "Navigate back"
                                            },
                                        onClick = { currentScreen = JellystackScreen.Home },
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    modifier = Modifier.testTag(JellystackTags.OPEN_SETTINGS),
                                    onClick = { isSettingsOpen = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Open settings",
                                    )
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (currentScreen == JellystackScreen.Home) {
                            JellystackBottomBar(
                                currentTab = currentTab,
                                onTabSelected = { selected -> currentTab = selected },
                            )
                        }
                    },
                ) { padding ->
                    when (currentScreen) {
                        JellystackScreen.Home ->
                            when (currentTab) {
                                JellystackTab.Home ->
                                    HomeContent(
                                        browseState = browseState,
                                        onSelectLibrary = {},
                                        onRefreshLibraries = {},
                                        onLoadMore = {},
                                        onOpenItemDetail = {
                                            detailState = JellyfinDetailUiState.Loading(it, null, null)
                                            currentScreen = JellystackScreen.Detail
                                        },
                                        onAddServer = { isSettingsOpen = true },
                                        modifier = Modifier.padding(padding),
                                    )

                                JellystackTab.Library ->
                                    LibraryContent(
                                        browseState = browseState,
                                        onSelectLibrary = {},
                                        onRefreshLibraries = {},
                                        onLoadMore = {},
                                        onOpenItemDetail = {
                                            detailState = JellyfinDetailUiState.Loading(it, null, null)
                                            currentScreen = JellystackScreen.Detail
                                        },
                                        onAddServer = { isSettingsOpen = true },
                                        showLibrarySelector = true,
                                        modifier = Modifier.padding(padding),
                                    )

                                JellystackTab.Media ->
                                    MediaPlaceholder(
                                        modifier =
                                            Modifier
                                                .padding(padding)
                                                .fillMaxSize(),
                                    )
                            }

                        JellystackScreen.Detail ->
                            DetailContent(
                                state = detailState,
                                libraryItems = browseState.libraryItems,
                                onRetry = {},
                                onPlay = { _, _ -> },
                                modifier = Modifier.padding(padding),
                            )
                    }

                    if (isSettingsOpen) {
                        SettingsDialog(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = themeController::toggle,
                            serverState = ServerManagementUiState(),
                            onOpenAddServer = {},
                            onDismissAddServer = {},
                            onUpdateServerForm = {},
                            onSubmitServer = {},
                            onRemoveServer = {},
                            onClearServerError = {},
                            onClose = { isSettingsOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun LibraryContent(
    browseState: JellyfinHomeState,
    onSelectLibrary: (String) -> Unit,
    onRefreshLibraries: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItemDetail: (JellyfinItem) -> Unit,
    onAddServer: () -> Unit,
    showLibraryItems: Boolean = true,
    showLibrarySelector: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JellyfinBrowseScreen(
            state = browseState,
            onSelectLibrary = onSelectLibrary,
            onRefresh = onRefreshLibraries,
            onLoadMore = onLoadMore,
            onOpenDetail = onOpenItemDetail,
            onConnectServer = onAddServer,
            showLibrarySelector = showLibrarySelector,
            showLibraryItems = showLibraryItems,
            modifier = Modifier.weight(1f, fill = true),
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun HomeContent(
    browseState: JellyfinHomeState,
    onSelectLibrary: (String) -> Unit,
    onRefreshLibraries: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItemDetail: (JellyfinItem) -> Unit,
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryContent(
        browseState = browseState,
        onSelectLibrary = onSelectLibrary,
        onRefreshLibraries = onRefreshLibraries,
        onLoadMore = onLoadMore,
        onOpenItemDetail = onOpenItemDetail,
        onAddServer = onAddServer,
        showLibraryItems = false,
        modifier = modifier,
    )
}

@Suppress("FunctionName")
@Composable
private fun MediaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Media hub coming soon",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "This tab will connect to Jellyseerr in a future update.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun JellystackBottomBar(
    currentTab: JellystackTab,
    onTabSelected: (JellystackTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentTab == JellystackTab.Home,
            onClick = { onTabSelected(JellystackTab.Home) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                )
            },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = currentTab == JellystackTab.Library,
            onClick = { onTabSelected(JellystackTab.Library) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Library",
                )
            },
            label = { Text("Library") },
        )
        NavigationBarItem(
            selected = currentTab == JellystackTab.Media,
            onClick = { onTabSelected(JellystackTab.Media) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = "Media",
                )
            },
            label = { Text("Media") },
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun DetailContent(
    state: JellyfinDetailUiState,
    libraryItems: List<JellyfinItem>,
    onRetry: () -> Unit,
    onPlay: (JellyfinItem, JellyfinItemDetail) -> Unit,
    audioTracks: List<AudioTrack> = emptyList(),
    selectedAudioTrack: AudioTrack? = null,
    onSelectAudioTrack: (AudioTrack) -> Unit = {},
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    selectedSubtitleTrack: SubtitleTrack? = null,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        JellyfinDetailUiState.Hidden ->
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Select an item to view details",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

        is JellyfinDetailUiState.Loading ->
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

        is JellyfinDetailUiState.Error ->
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }

        is JellyfinDetailUiState.Loaded -> {
            val episodes = remember(state.detail.id, libraryItems) { findEpisodesForDetail(state, libraryItems) }
            val seasonGroups = remember(episodes) { buildSeasonEpisodes(episodes) }
            JellyfinDetailContent(
                detail = state.detail,
                baseUrl = state.imageBaseUrl,
                accessToken = state.imageAccessToken,
                seasons = seasonGroups,
                onPlay = { onPlay(state.item, state.detail) },
                onQueueDownload = {},
                audioTracks = audioTracks,
                selectedAudioTrack = selectedAudioTrack,
                onSelectAudioTrack = onSelectAudioTrack,
                subtitleTracks = subtitleTracks,
                selectedSubtitleTrack = selectedSubtitleTrack,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

private fun findEpisodesForDetail(
    state: JellyfinDetailUiState.Loaded,
    libraryItems: List<JellyfinItem>,
): List<JellyfinItem> {
    val targetNames =
        buildSet {
            add(state.detail.name.lowercase())
            add(state.item.name.lowercase())
            state.item.seriesName
                ?.lowercase()
                ?.let { add(it) }
        }
    val targetIds =
        buildSet {
            if (state.item.type.equals("Series", ignoreCase = true)) {
                add(state.item.id)
            }
            state.item.parentId?.let { add(it) }
            state.item.seasonId?.let { add(it) }
        }
    return libraryItems
        .asSequence()
        .filter { it.type.equals("Episode", ignoreCase = true) }
        .filter { episode ->
            val matchesName = episode.seriesName?.lowercase()?.let { it in targetNames } ?: false
            val matchesId =
                episode.parentId?.let { it in targetIds } == true ||
                    episode.seasonId?.let { it in targetIds } == true
            matchesName || matchesId
        }.toList()
}

@Suppress("FunctionName")
@Composable
private fun SettingsDialog(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    serverState: ServerManagementUiState,
    onOpenAddServer: () -> Unit,
    onDismissAddServer: () -> Unit,
    onUpdateServerForm: (ServerFormState) -> Unit,
    onSubmitServer: () -> Unit,
    onRemoveServer: (ManagedServer) -> Unit,
    onClearServerError: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            SettingsContent(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                serverState = serverState,
                onOpenAddServer = onOpenAddServer,
                onDismissAddServer = onDismissAddServer,
                onUpdateServerForm = onUpdateServerForm,
                onSubmitServer = onSubmitServer,
                onRemoveServer = onRemoveServer,
                onClearServerError = onClearServerError,
                onClose = onClose,
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun SettingsContent(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    serverState: ServerManagementUiState,
    onOpenAddServer: () -> Unit,
    onDismissAddServer: () -> Unit,
    onUpdateServerForm: (ServerFormState) -> Unit,
    onSubmitServer: () -> Unit,
    onRemoveServer: (ManagedServer) -> Unit,
    onClearServerError: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close settings",
                )
            }
        }
        HorizontalDivider()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
            HorizontalDivider()
            Text(
                text = "Jellyfin",
                style = MaterialTheme.typography.titleLarge,
            )
            val jellyfinServers = serverState.servers.filter { it.type == ServerType.JELLYFIN }
            if (jellyfinServers.isEmpty()) {
                AssistChip(
                    onClick = onOpenAddServer,
                    label = { Text("Connect a Jellyfin server") },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    jellyfinServers.forEach { server ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(server.baseUrl, style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { onRemoveServer(server) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick = onOpenAddServer) {
                Text("Add Jellyfin server")
            }
        }
    }
    if (serverState.isDialogOpen) {
        AddServerDialog(
            state = serverState.form,
            isSaving = serverState.isSaving,
            errorMessage = serverState.errorMessage,
            onValueChange = onUpdateServerForm,
            onClearError = onClearServerError,
            onDismiss = onDismissAddServer,
            onSubmit = onSubmitServer,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun AddServerDialog(
    state: ServerFormState,
    isSaving: Boolean,
    errorMessage: String?,
    onValueChange: (ServerFormState) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Connect Jellyfin server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = {
                        onValueChange(state.copy(name = it))
                        onClearError()
                    },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = {
                        onValueChange(state.copy(baseUrl = it))
                        onClearError()
                    },
                    label = { Text("Base URL") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = {
                        onValueChange(state.copy(username = it))
                        onClearError()
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = {
                        onValueChange(state.copy(password = it))
                        onClearError()
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isSaving,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = icon, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                        }
                    },
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = state.isValid && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting…")
                } else {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        },
    )
}

private object JellystackTags {
    const val THEME_SWITCH = "theme_switch"
    const val OPEN_SETTINGS = "open_settings"
}
