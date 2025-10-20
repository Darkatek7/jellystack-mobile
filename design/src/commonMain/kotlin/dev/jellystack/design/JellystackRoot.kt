package dev.jellystack.design

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import dev.jellystack.core.downloads.DownloadRequest
import dev.jellystack.core.downloads.DownloadStatus
import dev.jellystack.core.downloads.OfflineDownloadManager
import dev.jellystack.core.downloads.OfflineMediaKind
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyseerr.JellyfinServerComponents
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationException
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationException.Reason
import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSsoAuthenticator
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.preferences.ThemePreferenceRepository
import dev.jellystack.core.server.ConnectivityException
import dev.jellystack.core.server.CredentialInput
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerRegistration
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.design.jellyfin.JellyfinBrowseScreen
import dev.jellystack.design.jellyfin.JellyfinDetailContent
import dev.jellystack.design.jellyfin.SeasonEpisodes
import dev.jellystack.design.jellyfin.buildSeasonEpisodes
import dev.jellystack.design.jellyseerr.JellyseerrRequestsScreen
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.design.theme.LocalThemeController
import dev.jellystack.design.theme.ThemeController
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.JellyfinPlaybackSourceResolver
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.PlaybackStreamSelector
import dev.jellystack.players.ResolvedPlaybackSource
import dev.jellystack.players.SubtitleFormat
import dev.jellystack.players.SubtitleTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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

internal enum class ServerFormType {
    JELLYFIN,
    JELLYSEERR,
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

internal data class ServerFormState(
    val type: ServerFormType = ServerFormType.JELLYFIN,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val jellyfinServerId: String? = null,
    val requiresJellyseerrPassword: Boolean = false,
) {
    val isValid: Boolean
        get() =
            when (type) {
                ServerFormType.JELLYFIN ->
                    name.isNotBlank() && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                ServerFormType.JELLYSEERR ->
                    baseUrl.isNotBlank() &&
                        jellyfinServerId != null &&
                        (!requiresJellyseerrPassword || password.isNotBlank())
            }
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
    downloadManager: OfflineDownloadManager? = null,
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
    val offlineDownloadManager = downloadManager
    val downloadStatusesFlow =
        remember(offlineDownloadManager) {
            offlineDownloadManager?.statuses
                ?: MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
        }
    val downloadStatuses by downloadStatusesFlow.collectAsState()
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
                        PlaybackMode.LOCAL -> "Offline"
                    }
                "Playing ${state.mediaId} ($modeLabel) on ${state.deviceName}"
            }
            PlaybackState.Stopped -> "Stopped"
        }
    var currentScreen by remember { mutableStateOf(JellystackScreen.Home) }
    var currentTab by remember { mutableStateOf(JellystackTab.Home) }
    var detailState by remember { mutableStateOf<JellyfinDetailUiState>(JellyfinDetailUiState.Hidden) }
    var detailEpisodeCache by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var detailJob by remember { mutableStateOf<Job?>(null) }
    var bulkDownloadJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val streamSelector = remember { PlaybackStreamSelector() }
    val downloadSourceResolver = remember { JellyfinPlaybackSourceResolver() }

    val browseRepository = remember { koin.get<JellyfinBrowseRepository>() }
    val jellyseerrRepository = remember { koin.get<JellyseerrRepository>() }
    val jellyseerrSsoAuthenticator = remember { koin.get<JellyseerrSsoAuthenticator>() }
    val jellyseerrEnvironmentProvider = remember { koin.get<JellyseerrEnvironmentProvider>() }
    val jellyseerrCoordinator =
        remember(jellyseerrRepository, jellyseerrEnvironmentProvider, coroutineScope) {
            JellyseerrRequestsCoordinator(
                repository = jellyseerrRepository,
                environmentProvider = jellyseerrEnvironmentProvider,
                scope = coroutineScope,
            )
        }
    val jellyseerrState by jellyseerrCoordinator.state.collectAsState()
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

    platformBackHandler(
        enabled =
            isSettingsOpen ||
                currentScreen != JellystackScreen.Home ||
                detailState !is JellyfinDetailUiState.Hidden,
    ) {
        when {
            isSettingsOpen -> isSettingsOpen = false
            detailState !is JellyfinDetailUiState.Hidden -> {
                detailState = JellyfinDetailUiState.Hidden
                currentScreen = JellystackScreen.Home
            }
            currentScreen != JellystackScreen.Home -> currentScreen = JellystackScreen.Home
        }
    }

    val derivedSelection =
        remember(detailState, streamSelector) {
            val loaded = detailState as? JellyfinDetailUiState.Loaded ?: return@remember null
            if (loaded.detail.mediaSources.isEmpty()) {
                null
            } else {
                runCatching { streamSelector.select(loaded.detail.mediaSources) }.getOrNull()
            }
        }

    var preferredAudioTrackId by remember { mutableStateOf<String?>(null) }
    var preferredSubtitleTrackId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(derivedSelection?.sourceId) {
        preferredAudioTrackId = derivedSelection?.audioTracks?.defaultAudioTrackId()
        preferredSubtitleTrackId = derivedSelection?.subtitleTracks?.defaultSubtitleTrackId()
    }

    LaunchedEffect(activePlaybackForDetail) {
        val playback = activePlaybackForDetail ?: return@LaunchedEffect
        preferredAudioTrackId = playback.audioTrack?.id
        preferredSubtitleTrackId = playback.subtitleTrack?.id
    }

    val availableAudioTracks =
        activePlaybackForDetail?.stream?.audioTracks
            ?: derivedSelection?.audioTracks
            ?: emptyList()

    val availableSubtitleTracks =
        activePlaybackForDetail?.stream?.subtitleTracks
            ?: derivedSelection?.subtitleTracks
            ?: emptyList()

    val selectedAudioTrack =
        activePlaybackForDetail?.audioTrack
            ?: availableAudioTracks.firstOrNull { it.id == preferredAudioTrackId }

    val selectedSubtitleTrack =
        activePlaybackForDetail?.subtitleTrack
            ?: availableSubtitleTracks.firstOrNull { it.id == preferredSubtitleTrackId }

    val loadedDetail = detailState as? JellyfinDetailUiState.Loaded
    val detailDownloadStatus = loadedDetail?.let { downloadStatuses[it.item.id] }
    val detailEpisodes =
        if (loadedDetail != null) {
            findEpisodesForDetail(
                state = loadedDetail,
                libraryItems = browseState.libraryItems,
                knownEpisodes = detailEpisodeCache,
            )
        } else {
            emptyList()
        }
    val detailSeasonGroups =
        if (loadedDetail != null) {
            buildSeasonEpisodes(detailEpisodes)
        } else {
            emptyList()
        }
    val detailAllEpisodes =
        if (loadedDetail != null) {
            detailSeasonGroups.flatMap { it.episodes }
        } else {
            emptyList()
        }
    val isEpisodeDetail =
        loadedDetail?.item?.type?.equals("Episode", ignoreCase = true) == true
    val serverUiState =
        ServerManagementUiState(
            servers = managedServers,
            isDialogOpen = showAddServerDialog,
            form = serverFormState,
            isSaving = isSavingServer,
            errorMessage = serverErrorMessage,
        )

    val playbackAction: (JellyfinItem, JellyfinItemDetail) -> Unit = { item, detail ->
        val pendingAudio = preferredAudioTrackId
        val pendingSubtitle = preferredSubtitleTrackId
        val hasSubtitleOptions = availableSubtitleTracks.isNotEmpty()
        coroutineScope.launch {
            val environment = environmentProvider.current()
            if (environment != null) {
                playbackController.play(PlaybackRequest.from(item, detail), environment)
                pendingAudio?.let { playbackController.selectAudioTrack(it) }
                if (hasSubtitleOptions || pendingSubtitle != null) {
                    playbackController.selectSubtitle(pendingSubtitle)
                }
            } else {
                serverErrorMessage = "Connect a Jellyfin server to start playback."
                isSettingsOpen = true
            }
        }
    }

    val pauseDownload: (String) -> Unit = { mediaId ->
        offlineDownloadManager?.pause(mediaId)
    }
    val resumeDownload: (String) -> Unit = { mediaId ->
        offlineDownloadManager?.resume(mediaId)
    }
    val removeDownload: (String) -> Unit = { mediaId ->
        offlineDownloadManager?.remove(mediaId)
    }

    suspend fun enqueueDownloadRequests(
        manager: OfflineDownloadManager,
        requests: List<DownloadRequest>,
    ): Int {
        if (requests.isEmpty()) return 0
        val snapshot = downloadStatuses
        var enqueued = 0
        requests.forEach { request ->
            val status = snapshot[request.mediaId]
            if (status !is DownloadStatus.InProgress && status !is DownloadStatus.Queued && status !is DownloadStatus.Completed) {
                manager.enqueue(request)
                enqueued += 1
                JellystackLog.d("Queued offline download for ${request.mediaId}")
            }
        }
        return enqueued
    }

    suspend fun enqueueDirectDownload(
        item: JellyfinItem,
        detail: JellyfinItemDetail,
        environment: JellyfinEnvironment,
        manager: OfflineDownloadManager,
        showErrors: Boolean = true,
    ): Boolean {
        val playbackRequest = PlaybackRequest.from(item, detail)
        if (playbackRequest.mediaSources.isEmpty()) {
            if (showErrors) {
                serverErrorMessage = "No playable source available for offline download."
            }
            return false
        }
        val selection =
            runCatching { streamSelector.select(playbackRequest.mediaSources) }
                .getOrElse {
                    if (showErrors) {
                        serverErrorMessage = "Unable to determine a playback source for offline download."
                    }
                    return false
                }
        if (selection.mode != PlaybackMode.DIRECT) {
            if (showErrors) {
                serverErrorMessage = "Offline downloads require a direct play source."
            }
            return false
        }
        val resolved =
            downloadSourceResolver.resolve(
                request = playbackRequest,
                selection = selection,
                environment = environment,
                startPositionMs = 0L,
            )
        val requests = buildDownloadRequests(item, playbackRequest, selection, resolved, environment)
        val enqueued = enqueueDownloadRequests(manager, requests)
        return enqueued > 0
    }

    suspend fun queueDownloadsForEpisodes(
        episodes: List<JellyfinItem>,
        environment: JellyfinEnvironment,
        manager: OfflineDownloadManager,
    ): Int {
        if (episodes.isEmpty()) return 0
        val uniqueEpisodes = episodes.distinctBy { it.id }
        var queuedCount = 0
        for (episode in uniqueEpisodes) {
            val status = downloadStatuses[episode.id]
            if (status is DownloadStatus.InProgress || status is DownloadStatus.Queued || status is DownloadStatus.Completed) {
                continue
            }
            val detail =
                try {
                    browseRepository.getItemDetail(episode.id, forceRefresh = true)
                } catch (t: Throwable) {
                    JellystackLog.e("Failed to load detail for ${episode.id}", t)
                    null
                } ?: continue
            if (detail.mediaSources.isEmpty()) {
                continue
            }
            val queued = enqueueDirectDownload(episode, detail, environment, manager, showErrors = false)
            if (queued) {
                queuedCount += 1
            }
        }
        return queuedCount
    }

    suspend fun queueDownloadFor(
        item: JellyfinItem,
        detail: JellyfinItemDetail,
        environment: JellyfinEnvironment,
        manager: OfflineDownloadManager,
    ): Boolean {
        val playbackRequest = PlaybackRequest.from(item, detail)
        if (playbackRequest.mediaSources.isEmpty()) {
            val episodeCandidates =
                when {
                    item.type.equals("Series", ignoreCase = true) -> browseRepository.episodesForSeries(item.id)
                    item.type.equals("Season", ignoreCase = true) -> browseRepository.episodesForSeason(item.id)
                    else -> emptyList()
                }
            if (episodeCandidates.isEmpty()) {
                serverErrorMessage = "No episodes available for offline download yet."
                return false
            }
            val queued = queueDownloadsForEpisodes(episodeCandidates, environment, manager)
            if (queued == 0) {
                serverErrorMessage = "Episodes already downloaded or queued."
            }
            return queued > 0
        }
        return enqueueDirectDownload(item, detail, environment, manager, showErrors = true)
    }
    val queueDownloadAction: (JellyfinItem, JellyfinItemDetail) -> Unit = { item, detail ->
        val manager = offlineDownloadManager
        val existingStatus = downloadStatuses[item.id]
        if (existingStatus is DownloadStatus.Completed) {
            serverErrorMessage = "Item already available offline."
        } else if (manager == null) {
            serverErrorMessage = "Offline downloads unavailable on this device."
        } else {
            serverErrorMessage = null
            coroutineScope.launch {
                val environment = environmentProvider.current()
                if (environment == null) {
                    serverErrorMessage = "Connect a Jellyfin server to manage downloads."
                    isSettingsOpen = true
                    return@launch
                }
                queueDownloadFor(item, detail, environment, manager)
            }
        }
    }

    val downloadSeriesAction: (() -> Unit)? =
        if (loadedDetail != null && detailSeasonGroups.isNotEmpty() && !isEpisodeDetail) {
            {
                val manager = offlineDownloadManager
                when {
                    manager == null -> serverErrorMessage = "Offline downloads unavailable on this device."
                    detailAllEpisodes.isEmpty() -> serverErrorMessage = "No episodes available to download."
                    bulkDownloadJob?.isActive == true -> Unit
                    else -> {
                        serverErrorMessage = null
                        bulkDownloadJob =
                            coroutineScope.launch {
                                try {
                                    val environment = environmentProvider.current()
                                    if (environment == null) {
                                        serverErrorMessage = "Connect a Jellyfin server to manage downloads."
                                        isSettingsOpen = true
                                        return@launch
                                    }
                                    val queued = queueDownloadsForEpisodes(detailAllEpisodes, environment, manager)
                                    if (queued == 0) {
                                        serverErrorMessage = "Series already downloaded or queued."
                                    }
                                } finally {
                                    bulkDownloadJob = null
                                }
                            }
                    }
                }
            }
        } else {
            null
        }
    val downloadSeasonAction: ((SeasonEpisodes) -> Unit)? =
        if (loadedDetail != null && detailSeasonGroups.isNotEmpty()) {
            { season ->
                val manager = offlineDownloadManager
                when {
                    manager == null -> serverErrorMessage = "Offline downloads unavailable on this device."
                    season.episodes.isEmpty() -> serverErrorMessage = "No episodes available to download."
                    bulkDownloadJob?.isActive == true -> Unit
                    else -> {
                        serverErrorMessage = null
                        bulkDownloadJob =
                            coroutineScope.launch {
                                try {
                                    val environment = environmentProvider.current()
                                    if (environment == null) {
                                        serverErrorMessage = "Connect a Jellyfin server to manage downloads."
                                        isSettingsOpen = true
                                        return@launch
                                    }
                                    val queued = queueDownloadsForEpisodes(season.episodes, environment, manager)
                                    if (queued == 0) {
                                        serverErrorMessage = "Season already downloaded or queued."
                                    }
                                } finally {
                                    bulkDownloadJob = null
                                }
                            }
                    }
                }
            }
        } else {
            null
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
        detailEpisodeCache = emptyList()
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

    val viewSeriesAction: (() -> Unit)? =
        if (loadedDetail != null && isEpisodeDetail) {
            {
                val target = loadedDetail.item.toSeriesNavigationTarget()
                loadDetail(target, false)
            }
        } else {
            null
        }

    val submitServer = submitServer@{
        if (isSavingServer) return@submitServer
        val form = serverFormState
        if (!form.isValid) {
            serverErrorMessage = "Complete all required fields before continuing."
            return@submitServer
        }
        coroutineScope.launch {
            isSavingServer = true
            serverErrorMessage = null
            when (form.type) {
                ServerFormType.JELLYFIN -> {
                    val normalizedUrl = normalizeServerBaseUrl(form.baseUrl)
                    JellystackLog.d("Submitting Jellyfin server connection to $normalizedUrl as ${form.username}")
                    try {
                        serverRepository.register(
                            ServerRegistration(
                                type = ServerType.JELLYFIN,
                                name = form.name,
                                baseUrl = normalizedUrl,
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
                        JellystackLog.d("Jellyfin server connected: $normalizedUrl")
                    } catch (t: Throwable) {
                        serverErrorMessage = t.connectivityErrorMessage()
                        JellystackLog.e("Failed to connect to Jellyfin server $normalizedUrl: $serverErrorMessage", t)
                    } finally {
                        isSavingServer = false
                    }
                }
                ServerFormType.JELLYSEERR -> {
                    val linkedServerId = form.jellyfinServerId
                    val linkedServer =
                        managedServers.firstOrNull { it.id == linkedServerId && it.type == ServerType.JELLYFIN }
                    val linkedCredential = linkedServer?.credentials as? StoredCredential.Jellyfin
                    if (linkedServer == null || linkedCredential == null) {
                        serverErrorMessage = "Select a Jellyfin server first."
                        isSavingServer = false
                        return@launch
                    }
                    val components = parseServerUrlComponents(linkedServer.baseUrl)
                    if (components == null) {
                        serverErrorMessage = "Unable to parse Jellyfin server URL ${linkedServer.baseUrl}."
                        isSavingServer = false
                        return@launch
                    }
                    val normalizedUrl = normalizeServerBaseUrl(form.baseUrl)
                    JellystackLog.d("Attempting Jellyseerr auto-auth at $normalizedUrl using ${linkedCredential.username}")
                    try {
                        val authResult =
                            jellyseerrSsoAuthenticator.authenticateWithLinkedJellyfin(
                                jellyseerrUrl = normalizedUrl,
                                jellyfinServerId = linkedServer.id,
                                components = components,
                                passwordOverride = form.password.takeIf { it.isNotBlank() },
                            )
                        serverRepository.register(
                            ServerRegistration(
                                type = ServerType.JELLYSEERR,
                                name = form.name.ifBlank { "${linkedServer.name} Requests" },
                                baseUrl = normalizedUrl,
                                credentials =
                                    CredentialInput.ApiKey(
                                        apiKey = authResult.apiKey,
                                        userId = authResult.userId?.toString(),
                                    ),
                            ),
                        )
                        serverFormState = ServerFormState()
                        showAddServerDialog = false
                        jellyseerrCoordinator.refresh()
                        JellystackLog.d("Jellyseerr server connected: $normalizedUrl")
                    } catch (authError: JellyseerrAuthenticationException) {
                        if (authError.reason == Reason.MISSING_JELLYFIN_PASSWORD) {
                            serverFormState =
                                serverFormState.copy(
                                    requiresJellyseerrPassword = true,
                                    password = "",
                                )
                            serverErrorMessage = "Enter your Jellyfin password to finish connecting."
                        } else {
                            serverErrorMessage = authError.message ?: "Unable to authenticate with Jellyseerr."
                        }
                        JellystackLog.e("Jellyseerr authentication failed for $normalizedUrl: $serverErrorMessage", authError)
                    } catch (t: Throwable) {
                        serverErrorMessage = t.connectivityErrorMessage()
                        JellystackLog.e("Failed to connect to Jellyseerr server $normalizedUrl: $serverErrorMessage", t)
                    } finally {
                        isSavingServer = false
                    }
                }
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

    LaunchedEffect(detailState) {
        when (val state = detailState) {
            is JellyfinDetailUiState.Loaded -> {
                val seriesId =
                    when {
                        state.item.type.equals("Series", ignoreCase = true) -> state.item.id
                        !state.item.seriesId.isNullOrBlank() -> state.item.seriesId
                        else -> null
                    }
                detailEpisodeCache =
                    if (seriesId != null) {
                        browseRepository.refreshEpisodesForSeries(seriesId)
                    } else {
                        emptyList()
                    }
            }
            else -> detailEpisodeCache = emptyList()
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
                                    JellyseerrRequestsScreen(
                                        state = jellyseerrState,
                                        onSearch = jellyseerrCoordinator::search,
                                        onClearSearch = jellyseerrCoordinator::clearSearch,
                                        onSelectFilter = jellyseerrCoordinator::selectFilter,
                                        onRefresh = jellyseerrCoordinator::refresh,
                                        onCreateRequest = { item, is4k ->
                                            val selection =
                                                if (item.mediaType == JellyseerrMediaType.TV) {
                                                    JellyseerrCreateSelection.AllSeasons
                                                } else {
                                                    null
                                                }
                                            jellyseerrCoordinator.submitRequest(item, is4k, selection)
                                        },
                                        onDeleteRequest = { summary ->
                                            jellyseerrCoordinator.deleteRequest(summary.id)
                                        },
                                        onRemoveMedia = jellyseerrCoordinator::removeMedia,
                                        onMessageAcknowledged = jellyseerrCoordinator::acknowledgeMessage,
                                        onAddServer = {
                                            isSettingsOpen = true
                                        },
                                        modifier =
                                            Modifier
                                                .padding(padding)
                                                .fillMaxSize(),
                                    )
                            }

                        JellystackScreen.Detail -> {
                            DetailContent(
                                state = detailState,
                                libraryItems = browseState.libraryItems,
                                knownEpisodes = detailEpisodeCache,
                                onViewSeries = viewSeriesAction,
                                onRetry = onRetryDetail,
                                onPlay = playbackAction,
                                downloadStatus = detailDownloadStatus,
                                onQueueDownload = queueDownloadAction,
                                onPauseDownload = pauseDownload,
                                onResumeDownload = resumeDownload,
                                onRemoveDownload = removeDownload,
                                onDownloadSeries = downloadSeriesAction,
                                onDownloadSeason = downloadSeasonAction,
                                audioTracks = availableAudioTracks,
                                selectedAudioTrack = selectedAudioTrack,
                                onSelectAudioTrack = { track ->
                                    preferredAudioTrackId = track.id
                                    if (activePlaybackForDetail != null) {
                                        playbackController.selectAudioTrack(track.id)
                                    }
                                },
                                subtitleTracks = availableSubtitleTracks,
                                selectedSubtitleTrack = selectedSubtitleTrack,
                                onSelectSubtitleTrack = { track ->
                                    preferredSubtitleTrackId = track?.id
                                    if (activePlaybackForDetail != null) {
                                        playbackController.selectSubtitle(track?.id)
                                    }
                                },
                                modifier = Modifier.padding(padding),
                            )
                        }
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
                        PlaybackMode.LOCAL -> "Offline"
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
    val previewRequestsState =
        remember {
            JellyseerrRequestsState.Ready(
                filter = JellyseerrRequestFilter.ALL,
                requests = emptyList(),
                counts = null,
                query = "",
                searchResults = emptyList(),
                isSearching = false,
                isRefreshing = false,
                isPerformingAction = false,
                message = null,
                isAdmin = true,
                lastUpdated = null,
            )
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
                                    JellyseerrRequestsScreen(
                                        state = previewRequestsState,
                                        onSearch = {},
                                        onClearSearch = {},
                                        onSelectFilter = {},
                                        onRefresh = {},
                                        onCreateRequest = { _, _ -> },
                                        onDeleteRequest = {},
                                        onRemoveMedia = {},
                                        onMessageAcknowledged = {},
                                        onAddServer = { isSettingsOpen = true },
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
                                knownEpisodes = emptyList(),
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
@OptIn(ExperimentalMaterial3Api::class)
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
@OptIn(ExperimentalMaterial3Api::class)
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
    knownEpisodes: List<JellyfinItem>,
    onViewSeries: (() -> Unit)? = null,
    onRetry: () -> Unit,
    onPlay: (JellyfinItem, JellyfinItemDetail) -> Unit,
    downloadStatus: DownloadStatus? = null,
    onQueueDownload: (JellyfinItem, JellyfinItemDetail) -> Unit = { _, _ -> },
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onDownloadSeries: (() -> Unit)? = null,
    onDownloadSeason: ((SeasonEpisodes) -> Unit)? = null,
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
            val episodes =
                remember(state.detail.id, libraryItems, knownEpisodes) {
                    findEpisodesForDetail(
                        state = state,
                        libraryItems = libraryItems,
                        knownEpisodes = knownEpisodes,
                    )
                }
            val seasonGroups = remember(episodes) { buildSeasonEpisodes(episodes) }
            val isEpisode = state.item.type.equals("Episode", ignoreCase = true)
            JellyfinDetailContent(
                detail = state.detail,
                baseUrl = state.imageBaseUrl,
                accessToken = state.imageAccessToken,
                seasons = seasonGroups,
                isEpisode = isEpisode,
                onPlay = { onPlay(state.item, state.detail) },
                downloadStatus = downloadStatus,
                onQueueDownload = { onQueueDownload(state.item, state.detail) },
                onPauseDownload = { onPauseDownload(state.item.id) },
                onResumeDownload = { onResumeDownload(state.item.id) },
                onRemoveDownload = { onRemoveDownload(state.item.id) },
                onDownloadSeries = onDownloadSeries,
                onDownloadSeason = onDownloadSeason,
                onViewSeries = if (isEpisode) onViewSeries else null,
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
    knownEpisodes: List<JellyfinItem>,
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
            state.item.seriesId?.let { add(it) }
        }
    return (if (knownEpisodes.isNotEmpty()) knownEpisodes else libraryItems)
        .asSequence()
        .filter { it.type.equals("Episode", ignoreCase = true) }
        .filter { episode ->
            val matchesName = episode.seriesName?.lowercase()?.let { it in targetNames } ?: false
            val matchesId =
                episode.parentId?.let { it in targetIds } == true ||
                    episode.seasonId?.let { it in targetIds } == true ||
                    episode.seriesId?.let { it in targetIds } == true
            matchesName || matchesId
        }.toList()
}

private fun JellyfinItem.toSeriesNavigationTarget(): JellyfinItem {
    val targetId = seriesId ?: parentId ?: id
    return copy(
        id = targetId,
        type = "Series",
        name = seriesName ?: name,
        sortName = sortName ?: seriesName,
        seriesId = targetId,
        parentId = null,
        seasonId = null,
        indexNumber = null,
        parentIndexNumber = null,
        episodeTitle = null,
    )
}

private fun List<AudioTrack>.defaultAudioTrackId(): String? = firstOrNull { it.isDefault }?.id ?: firstOrNull()?.id

private fun List<SubtitleTrack>.defaultSubtitleTrackId(): String? = firstOrNull { it.isDefault }?.id ?: firstOrNull { !it.isForced }?.id

private fun durationMillisFromTicks(ticks: Long?): Long? = ticks?.div(10_000L)

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
    val jellyfinServers = serverState.servers.filter { it.type == ServerType.JELLYFIN }
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
                Text("Add server")
            }
        }
    }
    if (serverState.isDialogOpen) {
        AddServerDialog(
            state = serverState.form,
            isSaving = serverState.isSaving,
            errorMessage = serverState.errorMessage,
            availableJellyfinServers = jellyfinServers,
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
    availableJellyfinServers: List<ManagedServer>,
    onValueChange: (ServerFormState) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    var passwordVisible by remember(state.requiresJellyseerrPassword) { mutableStateOf(false) }
    var serverMenuExpanded by remember { mutableStateOf(false) }
    val jellyfinServers = availableJellyfinServers.filter { it.type == ServerType.JELLYFIN }
    val selectedJellyfinServer = jellyfinServers.firstOrNull { it.id == state.jellyfinServerId }
    val selectedCredential = selectedJellyfinServer?.credentials as? StoredCredential.Jellyfin
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Connect server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.type == ServerFormType.JELLYFIN,
                        onClick = {
                            if (!isSaving) {
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        type = ServerFormType.JELLYFIN,
                                        jellyfinServerId = null,
                                        password = "",
                                        requiresJellyseerrPassword = false,
                                    ),
                                )
                                onClearError()
                            }
                        },
                        enabled = !isSaving,
                        label = { Text("Jellyfin") },
                    )
                    FilterChip(
                        selected = state.type == ServerFormType.JELLYSEERR,
                        onClick = {
                            if (!isSaving && jellyfinServers.isNotEmpty()) {
                                val default = jellyfinServers.first()
                                val credential = default.credentials as? StoredCredential.Jellyfin
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        type = ServerFormType.JELLYSEERR,
                                        jellyfinServerId = default.id,
                                        username = credential?.username ?: state.username,
                                        name = if (state.name.isBlank()) "${default.name} Requests" else state.name,
                                        password = "",
                                        requiresJellyseerrPassword = false,
                                    ),
                                )
                                onClearError()
                            }
                        },
                        enabled = !isSaving && jellyfinServers.isNotEmpty(),
                        label = { Text("Jellyseerr") },
                    )
                }
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
                when (state.type) {
                    ServerFormType.JELLYFIN -> {
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
                            visualTransformation =
                                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions =
                                KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    )
                                }
                            },
                        )
                    }
                    ServerFormType.JELLYSEERR -> {
                        if (jellyfinServers.isEmpty()) {
                            Text(
                                text = "Add a Jellyfin server first to enable automatic Jellyseerr authentication.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Box {
                                OutlinedTextField(
                                    value = selectedJellyfinServer?.name ?: "Select Jellyfin server",
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !isSaving,
                                    label = { Text("Jellyfin server") },
                                    singleLine = true,
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = null,
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isSaving && jellyfinServers.isNotEmpty()) {
                                                if (jellyfinServers.isNotEmpty()) {
                                                    serverMenuExpanded = true
                                                }
                                            },
                                )
                                DropdownMenu(
                                    expanded = serverMenuExpanded,
                                    onDismissRequest = { serverMenuExpanded = false },
                                ) {
                                    jellyfinServers.forEach { server ->
                                        val credential = server.credentials as? StoredCredential.Jellyfin
                                        DropdownMenuItem(
                                            text = { Text(server.name) },
                                            onClick = {
                                                serverMenuExpanded = false
                                                onValueChange(
                                                    state.copy(
                                                        jellyfinServerId = server.id,
                                                        username = credential?.username ?: state.username,
                                                        password = "",
                                                        requiresJellyseerrPassword = false,
                                                    ),
                                                )
                                                onClearError()
                                            },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = selectedCredential?.username ?: state.username,
                                onValueChange = {},
                                label = { Text("Jellyfin username") },
                                singleLine = true,
                                enabled = false,
                            )
                            if (state.requiresJellyseerrPassword) {
                                OutlinedTextField(
                                    value = state.password,
                                    onValueChange = {
                                        onValueChange(state.copy(password = it))
                                        onClearError()
                                    },
                                    label = { Text("Jellyfin password") },
                                    singleLine = true,
                                    enabled = !isSaving,
                                    visualTransformation =
                                        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions =
                                        KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            )
                                        }
                                    },
                                )
                                Text(
                                    text = "Enter your Jellyfin password so we can save it securely for future requests.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (state.type == ServerFormType.JELLYSEERR && jellyfinServers.isNotEmpty()) {
                    Text(
                        text = "We'll sign in to Jellyseerr with your Jellyfin account to obtain the API key automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    val label =
                        when (state.type) {
                            ServerFormType.JELLYFIN -> "Connect Jellyfin"
                            ServerFormType.JELLYSEERR -> "Connect Jellyseerr"
                        }
                    Text(label)
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

private fun buildDownloadRequests(
    item: JellyfinItem,
    request: PlaybackRequest,
    selection: PlaybackStreamSelection,
    source: ResolvedPlaybackSource,
    environment: JellyfinEnvironment,
): List<DownloadRequest> {
    val requests = mutableListOf<DownloadRequest>()
    val durationMs = durationMillisFromTicks(request.durationTicks)
    val videoBitrate = selection.videoBitrate
    val expectedSize =
        if (durationMs != null && durationMs > 0 && videoBitrate != null && videoBitrate > 0) {
            val seconds = durationMs / 1_000.0
            ((videoBitrate.toLong() * seconds) / 8.0).toLong()
        } else {
            null
        }
    requests +=
        DownloadRequest(
            mediaId = item.id,
            downloadUrl = source.url,
            headers = source.headers,
            mimeType = source.mimeType,
            expectedSizeBytes = expectedSize,
            checksumSha256 = null,
            kind = OfflineMediaKind.VIDEO,
            language = null,
            relativePath = buildVideoRelativePath(item, selection, source),
        )
    selection.subtitleTracks.forEach { track ->
        val index = track.id.toIntOrNull() ?: return@forEach
        val formatSegment =
            when (track.format) {
                SubtitleFormat.SRT -> "srt"
                SubtitleFormat.VTT -> "vtt"
            }
        val ext = formatSegment.lowercase()
        val subtitleUrl = buildSubtitleUrl(environment, item.id, selection.sourceId, index, formatSegment, ext)
        requests +=
            DownloadRequest(
                mediaId = "${item.id}::sub::$index",
                downloadUrl = subtitleUrl,
                headers = source.headers,
                mimeType = subtitleMime(track.format),
                expectedSizeBytes = null,
                checksumSha256 = null,
                kind = OfflineMediaKind.SUBTITLE,
                language = track.language,
                relativePath = subtitleRelativePath(item, track, ext, index),
            )
    }
    return requests
}

private fun buildVideoRelativePath(
    item: JellyfinItem,
    selection: PlaybackStreamSelection,
    source: ResolvedPlaybackSource,
): String {
    val extension = determineVideoExtension(selection, source)
    return "${item.id}/${item.id}.$extension"
}

private fun determineVideoExtension(
    selection: PlaybackStreamSelection,
    source: ResolvedPlaybackSource,
): String {
    val container = selection.container?.lowercase()
    if (!container.isNullOrBlank()) return container
    return when (source.mimeType) {
        "video/mp4", "application/mp4" -> "mp4"
        "video/x-matroska" -> "mkv"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> "mp4"
    }
}

private fun buildSubtitleUrl(
    environment: JellyfinEnvironment,
    itemId: String,
    sourceId: String,
    index: Int,
    formatSegment: String,
    extension: String,
): String {
    val baseUrl = environment.baseUrl.trimEnd('/')
    return buildString {
        append(baseUrl)
        append("/Videos/")
        append(itemId)
        append("/")
        append(sourceId)
        append("/Subtitles/")
        append(index)
        append("/")
        append(formatSegment)
        append("/Stream.")
        append(extension)
        append("?api_key=")
        append(environment.accessToken)
        append("&DeviceId=")
        append(environment.deviceId)
        append("&UserId=")
        append(environment.userId)
    }
}

private fun subtitleMime(format: SubtitleFormat): String =
    when (format) {
        SubtitleFormat.SRT -> "application/x-subrip"
        SubtitleFormat.VTT -> "text/vtt"
    }

private fun subtitleRelativePath(
    item: JellyfinItem,
    track: SubtitleTrack,
    extension: String,
    index: Int,
): String {
    val base =
        track.language?.let(::sanitizeFileSegment)
            ?: track.title?.let(::sanitizeFileSegment)
            ?: "track$index"
    return "${item.id}/subtitles/$base.$extension"
}

private fun sanitizeFileSegment(value: String): String {
    val sanitized = value.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
    val trimmed = sanitized.trim('_')
    return if (trimmed.isBlank()) "file" else trimmed
}

private data class ParsedServerUrl(
    val scheme: String,
    val host: String,
    val explicitPort: Int?,
    val path: String,
)

private val SERVER_URL_REGEX =
    Regex("^(https?)://([^/:?#]+)(?::(\\d+))?([^?#]*)?.*$", RegexOption.IGNORE_CASE)

private fun normalizeServerBaseUrl(url: String): String {
    val trimmed = url.trim()
    val parsed = parseServerUrl(trimmed) ?: return trimmed.trimTrailingSlashesPreserveScheme()
    val defaultPort = if (parsed.scheme == "https") 443 else 80
    val port = parsed.explicitPort ?: defaultPort
    val includePort = parsed.explicitPort != null && parsed.explicitPort != defaultPort
    return buildString {
        append(parsed.scheme)
        append("://")
        append(parsed.host)
        if (includePort) {
            append(':')
            append(port)
        }
        if (parsed.path.isNotBlank()) {
            append(parsed.path)
        }
    }
}

private fun parseServerUrlComponents(baseUrl: String): JellyfinServerComponents? {
    val parsed = parseServerUrl(baseUrl) ?: return null
    val defaultPort = if (parsed.scheme == "https") 443 else 80
    val port = parsed.explicitPort ?: defaultPort
    return JellyfinServerComponents(
        hostname = parsed.host,
        port = port,
        urlBase = parsed.path,
        useSsl = parsed.scheme == "https",
    )
}

private fun parseServerUrl(url: String): ParsedServerUrl? {
    val match = SERVER_URL_REGEX.matchEntire(url.trim()) ?: return null
    val scheme = match.groupValues[1].lowercase()
    val host = match.groupValues[2]
    val portValue =
        match.groupValues
            .getOrNull(3)
            ?.takeIf { it.isNotBlank() }
            ?.toInt()
    val rawPath = match.groupValues.getOrNull(4) ?: ""
    val sanitizedPath =
        rawPath
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .let { path ->
                when {
                    path.isBlank() || path == "/" -> ""
                    path.startsWith('/') -> path
                    else -> "/$path"
                }
            }
    return ParsedServerUrl(
        scheme = scheme,
        host = host,
        explicitPort = portValue,
        path = sanitizedPath,
    )
}

private fun String.trimTrailingSlashesPreserveScheme(): String {
    if (endsWith("://")) return this
    return trimEnd('/')
}

private fun Throwable.connectivityErrorMessage(): String =
    when (this) {
        is ConnectivityException ->
            listOfNotNull(message, cause?.message)
                .joinToString(separator = ": ")
                .ifBlank { "Unable to connect to server" }
        else -> message ?: "Unable to connect to server"
    }
