package dev.jellystack.core.jellyfin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class JellyfinHomeState(
    val isInitialLoading: Boolean = false,
    val isPageLoading: Boolean = false,
    val libraries: List<JellyfinLibrary> = emptyList(),
    val continueWatching: List<JellyfinItem> = emptyList(),
    val recentShows: List<JellyfinItem> = emptyList(),
    val recentMovies: List<JellyfinItem> = emptyList(),
    val selectedLibraryId: String? = null,
    val libraryItems: List<JellyfinItem> = emptyList(),
    val currentPage: Int = 0,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val imageBaseUrl: String? = null,
    val imageAccessToken: String? = null,
)

class JellyfinBrowseCoordinator(
    private val repository: JellyfinBrowseRepository,
    private val scope: CoroutineScope,
    private val pageSize: Int = 30,
) {
    private companion object {
        private const val HOME_SECTION_ITEM_LIMIT = 12
    }

    private val mutableState = MutableStateFlow(JellyfinHomeState(isInitialLoading = true))
    private val loadMutex = Mutex()
    private var refreshJob: Job? = null

    val state: StateFlow<JellyfinHomeState> = mutableState.asStateFlow()

    init {
        bootstrap(forceRefresh = false)
    }

    fun bootstrap(forceRefresh: Boolean) {
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                loadMutex.withLock {
                    mutableState.value = mutableState.value.copy(isInitialLoading = true, errorMessage = null)
                    try {
                        val cachedLibraries = repository.listLibraries()
                        val libraries =
                            if (forceRefresh || cachedLibraries.isEmpty()) {
                                repository.refreshLibraries()
                            } else {
                                cachedLibraries
                            }
                        val selectedId = selectDefaultLibrary(libraries)
                        val imageBaseUrl = repository.currentServerBaseUrl()
                        val imageAccessToken = repository.currentAccessToken()

                        val (continueWatching, firstPage) =
                            coroutineScope {
                                val continueWatchingDeferred = async { repository.refreshContinueWatching(limit = HOME_SECTION_ITEM_LIMIT) }
                                val firstPageDeferred =
                                    selectedId?.let { id ->
                                        async {
                                            val cached = repository.cachedLibraryPage(id, page = 0, pageSize = pageSize)
                                            if (cached.isNotEmpty() && !forceRefresh) {
                                                cached
                                            } else {
                                                repository.loadLibraryPage(id, page = 0, pageSize = pageSize, refresh = true)
                                            }
                                        }
                                    }

                                val continueWatchingResult = continueWatchingDeferred.await()
                                val firstPageResult = firstPageDeferred?.await() ?: emptyList()
                                continueWatchingResult to firstPageResult
                            }
                        val recentShows =
                            selectedId?.let { id ->
                                repository.refreshRecentlyAddedShows(id, limit = HOME_SECTION_ITEM_LIMIT)
                            } ?: emptyList()
                        val recentMovies =
                            selectedId?.let { id ->
                                repository.refreshRecentlyAddedMovies(id, limit = HOME_SECTION_ITEM_LIMIT)
                            } ?: emptyList()

                        mutableState.value =
                            mutableState.value.copy(
                                isInitialLoading = false,
                                isPageLoading = false,
                                libraries = libraries,
                                continueWatching = continueWatching,
                                recentShows = recentShows,
                                recentMovies = recentMovies,
                                selectedLibraryId = selectedId,
                                libraryItems = firstPage,
                                currentPage = 0,
                                endReached = firstPage.size < pageSize,
                                errorMessage = null,
                                imageBaseUrl = imageBaseUrl,
                                imageAccessToken = imageAccessToken,
                            )
                    } catch (t: Throwable) {
                        val imageBaseUrl = repository.currentServerBaseUrl()
                        val imageAccessToken = repository.currentAccessToken()
                        mutableState.value =
                            mutableState.value.copy(
                                isInitialLoading = false,
                                isPageLoading = false,
                                errorMessage = t.message ?: "Failed to load Jellyfin data",
                                imageBaseUrl = imageBaseUrl,
                                imageAccessToken = imageAccessToken,
                            )
                    }
                }
            }
    }

    fun selectLibrary(libraryId: String) {
        if (libraryId == mutableState.value.selectedLibraryId) {
            return
        }
        scope.launch {
            loadMutex.withLock {
                mutableState.value =
                    mutableState.value.copy(
                        selectedLibraryId = libraryId,
                        libraryItems = emptyList(),
                        currentPage = 0,
                        recentShows = emptyList(),
                        recentMovies = emptyList(),
                        endReached = false,
                        isInitialLoading = true,
                        errorMessage = null,
                    )
            }
            loadLibraryPage(page = 0, refresh = true)
        }
    }

    fun refreshSelectedLibrary() {
        scope.launch {
            loadLibraryPage(page = 0, refresh = true)
        }
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (current.selectedLibraryId == null || current.isPageLoading || current.endReached) {
            return
        }
        scope.launch {
            loadLibraryPage(page = current.currentPage + 1, refresh = false)
        }
    }

    private suspend fun loadLibraryPage(
        page: Int,
        refresh: Boolean,
    ) {
        loadMutex.withLock {
            val selectedId = mutableState.value.selectedLibraryId ?: return
            val stateBefore = mutableState.value
            val imageBaseUrl = repository.currentServerBaseUrl()
            val imageAccessToken = repository.currentAccessToken()
            mutableState.value =
                stateBefore.copy(
                    isInitialLoading = refresh && page == 0,
                    isPageLoading = !refresh,
                    errorMessage = null,
                    imageBaseUrl = imageBaseUrl,
                    imageAccessToken = imageAccessToken,
                )
            try {
                val items = repository.loadLibraryPage(selectedId, page = page, pageSize = pageSize, refresh = refresh)
                val recentShows =
                    if (page == 0 && (refresh || stateBefore.recentShows.isEmpty())) {
                        repository.refreshRecentlyAddedShows(selectedId, limit = HOME_SECTION_ITEM_LIMIT)
                    } else {
                        null
                    }
                val recentMovies =
                    if (page == 0 && (refresh || stateBefore.recentMovies.isEmpty())) {
                        repository.refreshRecentlyAddedMovies(selectedId, limit = HOME_SECTION_ITEM_LIMIT)
                    } else {
                        null
                    }
                val merged =
                    if (page == 0) {
                        items
                    } else {
                        (stateBefore.libraryItems + items).distinctBy { it.id }
                    }
                mutableState.value =
                    mutableState.value.copy(
                        isInitialLoading = false,
                        isPageLoading = false,
                        libraryItems = merged,
                        currentPage = page,
                        endReached = items.size < pageSize,
                        imageBaseUrl = imageBaseUrl,
                        imageAccessToken = imageAccessToken,
                        recentShows = recentShows ?: mutableState.value.recentShows,
                        recentMovies = recentMovies ?: mutableState.value.recentMovies,
                    )
            } catch (t: Throwable) {
                mutableState.value =
                    mutableState.value.copy(
                        isInitialLoading = false,
                        isPageLoading = false,
                        errorMessage = t.message ?: "Failed to load items",
                        imageBaseUrl = imageBaseUrl,
                        imageAccessToken = imageAccessToken,
                    )
            }
        }
    }

    private fun selectDefaultLibrary(libraries: List<JellyfinLibrary>): String? =
        mutableState.value.selectedLibraryId?.takeIf { id -> libraries.any { it.id == id } }
            ?: libraries.firstOrNull()?.id
}
