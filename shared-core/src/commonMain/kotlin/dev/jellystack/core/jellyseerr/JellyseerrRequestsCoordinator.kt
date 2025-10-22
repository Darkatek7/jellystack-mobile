package dev.jellystack.core.jellyseerr

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class JellyseerrRequestsCoordinator(
    private val repository: JellyseerrRepository,
    private val environmentProvider: JellyseerrEnvironmentProvider,
    private val scope: CoroutineScope,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val enablePolling: Boolean = true,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<JellyseerrRequestsState>(JellyseerrRequestsState.Loading)
    val state: StateFlow<JellyseerrRequestsState> = _state

    private val environmentJob: Job
    private var currentEnvironment: JellyseerrEnvironment? = null
    private var currentProfile: JellyseerrProfile? = null
    private var currentFilter: JellyseerrRequestFilter = JellyseerrRequestFilter.ALL
    private var lastRequests: List<JellyseerrRequestSummary> = emptyList()
    private var lastCounts: JellyseerrRequestCounts? = null
    private var currentQuery: String = ""
    private var lastSearchResults: List<JellyseerrSearchItem> = emptyList()
    private var lastLanguageProfiles: JellyseerrLanguageProfiles = JellyseerrLanguageProfiles.EMPTY
    private var pollJob: Job? = null
    private var lastUpdated: Instant? = null

    init {
        environmentJob =
            scope.launch {
                environmentProvider.observe().collect { environment ->
                    handleEnvironmentChange(environment)
                }
            }
    }

    fun refresh() {
        scope.launch {
            refreshInternal(fetchCounts = true)
        }
    }

    fun shutdown() {
        pollJob?.cancel()
        environmentJob.cancel()
    }

    fun selectFilter(filter: JellyseerrRequestFilter) {
        scope.launch {
            mutex.withLock {
                if (currentFilter == filter) return@launch
                currentFilter = filter
                updateReadyState { it.copy(filter = filter) }
            }
            refreshInternal(fetchCounts = true)
        }
    }

    fun search(query: String) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment }
            if (environment == null) {
                mutex.withLock {
                    currentQuery = ""
                    lastSearchResults = emptyList()
                    updateReadyState { it.copy(query = "", searchResults = emptyList(), isSearching = false) }
                }
                return@launch
            }
            val trimmed = query.trim()
            mutex.withLock {
                currentQuery = trimmed
                if (trimmed.isEmpty()) {
                    lastSearchResults = emptyList()
                    updateReadyState { it.copy(query = "", searchResults = emptyList(), isSearching = false) }
                    return@launch
                }
                updateReadyState { it.copy(query = trimmed, isSearching = true, searchResults = it.searchResults) }
            }
            runCatching { repository.search(environment, trimmed) }
                .onSuccess { results ->
                    mutex.withLock {
                        lastSearchResults = results
                        updateReadyState {
                            it.copy(
                                query = trimmed,
                                searchResults = results,
                                isSearching = false,
                            )
                        }
                    }
                }.onFailure { error ->
                    JellystackLog.e(
                        "Jellyseerr search failed for ${environment.serverId} with query '$trimmed': ${error.message}",
                        error,
                    )
                    mutex.withLock {
                        lastSearchResults = emptyList()
                        updateReadyState {
                            it.copy(
                                query = trimmed,
                                searchResults = emptyList(),
                                isSearching = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.ERROR,
                                        error.message ?: "Search failed.",
                                    ),
                            )
                        }
                    }
                }
        }
    }

    fun clearSearch() {
        scope.launch {
            mutex.withLock {
                currentQuery = ""
                lastSearchResults = emptyList()
                updateReadyState { it.copy(query = "", searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun submitRequest(
        item: JellyseerrSearchItem,
        languageProfile: JellyseerrLanguageProfileOption?,
        seasons: JellyseerrCreateSelection? = null,
    ) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            mutex.withLock {
                updateReadyState { it.copy(isPerformingAction = true) }
            }
            val availableProfiles =
                when (item.mediaType) {
                    JellyseerrMediaType.MOVIE -> lastLanguageProfiles.movies
                    JellyseerrMediaType.TV -> lastLanguageProfiles.tv
                    else -> emptyList()
                }
            val effectiveProfile = languageProfile ?: availableProfiles.preferredLanguageOption()
            if (effectiveProfile == null) {
                mutex.withLock {
                    updateReadyState {
                        it.copy(
                            isPerformingAction = false,
                            message =
                                JellyseerrMessage(
                                    JellyseerrMessageKind.ERROR,
                                    "No language profiles are configured for this media type.",
                                ),
                        )
                    }
                }
                return@launch
            }
            val payload =
                JellyseerrCreateRequest(
                    mediaId = item.mediaInfoId ?: item.tmdbId,
                    tvdbId = item.tvdbId,
                    mediaType = item.mediaType,
                    is4k = effectiveProfile.is4k,
                    seasons = seasons,
                    serverId = effectiveProfile.serviceId,
                    profileId = effectiveProfile.profileId,
                    languageProfileId = effectiveProfile.languageProfileId,
                )
            when (val result = repository.createRequest(environment, payload)) {
                is JellyseerrCreateResult.Success -> {
                    mutex.withLock {
                        lastRequests = (lastRequests.filterNot { it.id == result.request.id } + result.request).sortedBy { it.id }
                        updateReadyState {
                            it.copy(
                                requests = lastRequests,
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.INFO,
                                        "Request submitted for ${item.title}.",
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = true)
                }
                is JellyseerrCreateResult.Duplicate -> {
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.ERROR,
                                        result.message,
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = false)
                }
                is JellyseerrCreateResult.Failure -> {
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.ERROR,
                                        result.message,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun deleteRequest(requestId: Int) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            mutex.withLock { updateReadyState { it.copy(isPerformingAction = true) } }
            repository
                .deleteRequest(environment, requestId)
                .onSuccess {
                    mutex.withLock {
                        lastRequests = lastRequests.filterNot { it.id == requestId }
                        updateReadyState {
                            it.copy(
                                requests = lastRequests,
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.INFO,
                                        "Request removed.",
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = true)
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to delete Jellyseerr request $requestId for ${environment.serverId}: ${error.message}",
                        error,
                    )
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.ERROR,
                                        error.message ?: "Failed to delete request.",
                                    ),
                            )
                        }
                    }
                }
        }
    }

    fun removeMedia(summary: JellyseerrRequestSummary) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            val mediaId =
                summary.mediaId
                    ?: run {
                        mutex.withLock {
                            updateReadyState {
                                it.copy(
                                    message =
                                        JellyseerrMessage(
                                            JellyseerrMessageKind.ERROR,
                                            "Unable to remove ${summary.title ?: "media"} because the media id is unknown.",
                                        ),
                                )
                            }
                        }
                        return@launch
                    }
            mutex.withLock { updateReadyState { it.copy(isPerformingAction = true) } }
            repository
                .removeMediaFromService(environment, mediaId, summary.is4k)
                .onSuccess {
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.INFO,
                                        "Removal queued for ${summary.title ?: "media"}.",
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = true)
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to remove Jellyseerr media ${summary.mediaId} for ${environment.serverId}: ${error.message}",
                        error,
                    )
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = false,
                                message =
                                    JellyseerrMessage(
                                        JellyseerrMessageKind.ERROR,
                                        error.message ?: "Failed to remove media.",
                                    ),
                            )
                        }
                    }
                }
        }
    }

    fun acknowledgeMessage() {
        scope.launch {
            mutex.withLock {
                updateReadyState { it.copy(message = null) }
            }
        }
    }

    private suspend fun handleEnvironmentChange(environment: JellyseerrEnvironment?) {
        mutex.withLock {
            pollJob?.cancel()
            currentEnvironment = environment
            currentProfile = null
            currentFilter = JellyseerrRequestFilter.ALL
            lastRequests = emptyList()
            lastCounts = null
            currentQuery = ""
            lastSearchResults = emptyList()
            lastLanguageProfiles = JellyseerrLanguageProfiles.EMPTY
            lastUpdated = null
            _state.value =
                when (environment) {
                    null -> JellyseerrRequestsState.MissingServer
                    else -> JellyseerrRequestsState.Loading
                }
        }
        if (environment == null) {
            return
        }
        JellystackLog.d(
            "Loading Jellyseerr environment ${environment.serverId} at ${environment.baseUrl}",
        )

        data class InitialLoad(
            val profile: JellyseerrProfile,
            val page: JellyseerrRequestsPage,
            val counts: JellyseerrRequestCounts,
            val languageProfiles: JellyseerrLanguageProfiles,
        )
        val loadResult =
            runCatching {
                val profile = repository.profile(environment)
                val languageProfiles = repository.fetchLanguageProfiles(environment)
                val page = repository.fetchRequests(environment, currentFilter)
                val counts = repository.fetchCounts(environment)
                InitialLoad(profile, page, counts, languageProfiles)
            }
        loadResult
            .onSuccess { result ->
                JellystackLog.d(
                    "Loaded Jellyseerr environment ${environment.serverId} with ${result.page.results.size} requests",
                )
                mutex.withLock {
                    currentProfile = result.profile
                    lastRequests = result.page.results
                    lastCounts = result.counts
                    lastLanguageProfiles = result.languageProfiles
                    lastUpdated = clock.now()
                    _state.value =
                        JellyseerrRequestsState.Ready(
                            filter = currentFilter,
                            requests = lastRequests,
                            counts = lastCounts,
                            query = currentQuery,
                            searchResults = lastSearchResults,
                            isSearching = false,
                            isRefreshing = false,
                            isPerformingAction = false,
                            message = null,
                            isAdmin = result.profile.canManageRequests(),
                            lastUpdated = lastUpdated,
                            languageProfiles = lastLanguageProfiles,
                        )
                }
                startPolling()
            }.onFailure { error ->
                JellystackLog.e(
                    "Failed to load Jellyseerr environment ${environment.serverId}: ${error.message}",
                    error,
                )
                mutex.withLock {
                    _state.value =
                        JellyseerrRequestsState.Error(error.message ?: "Failed to load Jellyseerr data.")
                }
            }
    }

    private fun startPolling() {
        if (!enablePolling) {
            return
        }
        pollJob?.cancel()
        pollJob =
            scope.launch {
                while (isActive) {
                    delay(pollIntervalMillis)
                    refreshInternal(fetchCounts = false)
                }
            }
    }

    private suspend fun refreshInternal(fetchCounts: Boolean) {
        val environment = mutex.withLock { currentEnvironment } ?: return
        mutex.withLock {
            updateReadyState { it.copy(isRefreshing = true) }
        }
        val requestsResult = runCatching { repository.fetchRequests(environment, currentFilter) }
        val countsResult =
            if (fetchCounts) {
                runCatching { repository.fetchCounts(environment) }
            } else {
                Result.success(mutex.withLock { lastCounts })
            }
        mutex.withLock {
            requestsResult
                .onSuccess { page ->
                    lastRequests = page.results
                    lastUpdated = clock.now()
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to refresh Jellyseerr requests for ${environment.serverId}: ${error.message}",
                        error,
                    )
                    updateReadyState {
                        it.copy(
                            isRefreshing = false,
                            message =
                                JellyseerrMessage(
                                    JellyseerrMessageKind.ERROR,
                                    error.message ?: "Failed to refresh requests.",
                                ),
                        )
                    }
                    return@withLock
                }
            countsResult.onSuccess { counts ->
                lastCounts = counts
            }
            updateReadyState {
                it.copy(
                    requests = lastRequests,
                    counts = lastCounts,
                    isRefreshing = false,
                    isPerformingAction = false,
                    lastUpdated = lastUpdated,
                )
            }
        }
    }

    private suspend fun updateReadyState(transform: (JellyseerrRequestsState.Ready) -> JellyseerrRequestsState.Ready) {
        _state.update { current ->
            if (current is JellyseerrRequestsState.Ready) {
                transform(current)
            } else {
                current
            }
        }
    }

    private fun List<JellyseerrLanguageProfileOption>.preferredLanguageOption(): JellyseerrLanguageProfileOption? =
        firstOrNull { it.isDefault } ?: firstOrNull()

    companion object {
        private const val DEFAULT_POLL_INTERVAL_MILLIS = 30_000L
    }
}
