package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.AUDIO
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.OTHER
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.SUBTITLE
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.VIDEO
import dev.jellystack.network.NetworkJson
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import dev.jellystack.network.jellyfin.JellyfinItemDetailDto
import dev.jellystack.network.jellyfin.JellyfinItemDto
import dev.jellystack.network.jellyfin.JellyfinLibraryDto
import dev.jellystack.network.jellyfin.JellyfinMediaSourceDto
import dev.jellystack.network.jellyfin.JellyfinMediaStreamDto
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

typealias JellyfinBrowseApiFactory = (JellyfinEnvironment) -> JellyfinBrowseApi

class JellyfinBrowseRepository(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val libraryStore: JellyfinLibraryStore,
    private val itemStore: JellyfinItemStore,
    private val detailStore: JellyfinItemDetailStore,
    private val apiFactory: JellyfinBrowseApiFactory,
    private val clock: Clock = Clock.System,
) {
    private val cachedApis = mutableMapOf<String, JellyfinBrowseApi>()

    suspend fun refreshLibraries(): List<JellyfinLibrary> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val response = api.fetchLibraries(environment.userId)
        val now = clock.now()
        val records = response.items.map { it.toRecord(environment, now) }
        libraryStore.replaceAll(environment.serverKey, records)
        return records.map { it.toDomain() }
    }

    suspend fun listLibraries(): List<JellyfinLibrary> {
        val environment = environmentProvider.current() ?: return emptyList()
        return libraryStore.list(environment.serverKey).map { it.toDomain() }
    }

    suspend fun cachedContinueWatching(limit: Int): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listContinueWatching(environment.serverKey, limit.toLong()).map { it.toDomain() }
    }

    suspend fun cachedRecentShows(
        libraryId: String?,
        limit: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listRecentShows(environment.serverKey, libraryId, limit.toLong()).map { it.toDomain() }
    }

    suspend fun cachedRecentMovies(
        libraryId: String?,
        limit: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listRecentMovies(environment.serverKey, libraryId, limit.toLong()).map { it.toDomain() }
    }

    suspend fun loadLibraryPage(
        libraryId: String,
        page: Int,
        pageSize: Int,
        refresh: Boolean = page == 0,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val startIndex = page * pageSize
        val now = clock.now()
        val response = api.fetchLibraryItems(environment.userId, libraryId, startIndex, pageSize)
        val records = response.items.map { it.toRecord(environment, libraryId, now) }
        if (refresh) {
            itemStore.replaceForLibrary(environment.serverKey, libraryId, records)
        } else {
            itemStore.upsert(records)
        }
        return itemStore
            .listByLibrary(environment.serverKey, libraryId, limit = pageSize.toLong(), offset = startIndex.toLong())
            .map { it.toDomain() }
    }

    suspend fun cachedLibraryPage(
        libraryId: String,
        page: Int,
        pageSize: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val startIndex = page * pageSize
        return itemStore
            .listByLibrary(environment.serverKey, libraryId, limit = pageSize.toLong(), offset = startIndex.toLong())
            .map { it.toDomain() }
    }

    suspend fun refreshContinueWatching(limit: Int): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val now = clock.now()
        val response = api.fetchContinueWatching(environment.userId, limit)
        val records = response.items.map { it.toRecord(environment, fallbackLibraryId = it.parentId, updatedAt = now) }
        itemStore.upsert(records)
        return itemStore.listContinueWatching(environment.serverKey, limit.toLong()).map { it.toDomain() }
    }

    suspend fun refreshRecentlyAddedShows(
        libraryId: String,
        limit: Int,
    ): List<JellyfinItem> = refreshRecentlyAdded(libraryId = libraryId, limit = limit, includeItemTypes = "Series,Episode")

    suspend fun refreshRecentlyAddedMovies(
        libraryId: String,
        limit: Int,
    ): List<JellyfinItem> = refreshRecentlyAdded(libraryId = libraryId, limit = limit, includeItemTypes = "Movie")

    suspend fun episodesForSeries(seriesId: String): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listEpisodesForSeries(environment.serverKey, seriesId).map { it.toDomain() }
    }

    suspend fun episodesForSeason(seasonId: String): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listEpisodesForSeason(environment.serverKey, seasonId).map { it.toDomain() }
    }

    suspend fun reportOfflineProgress(
        mediaId: String,
        positionMs: Long,
    ) {
        val environment = environmentProvider.current() ?: return
        val api = apiFor(environment)
        val ticks = if (positionMs <= 0) 0L else positionMs * 10_000
        runCatching { api.reportPlaybackProgress(environment.userId, mediaId, ticks) }
    }

    suspend fun markOfflinePlaybackCompleted(mediaId: String) {
        val environment = environmentProvider.current() ?: return
        val api = apiFor(environment)
        runCatching { api.markPlaybackCompleted(environment.userId, mediaId) }
    }

    private suspend fun refreshRecentlyAdded(
        libraryId: String,
        limit: Int,
        includeItemTypes: String,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val now = clock.now()
        val items = api.fetchLatestItems(environment.userId, libraryId, limit, includeItemTypes)
        val records = items.map { it.toRecord(environment, fallbackLibraryId = libraryId, updatedAt = now) }
        itemStore.upsert(records)
        return records.map { it.toDomain() }
    }

    suspend fun getItemDetail(
        itemId: String,
        forceRefresh: Boolean = false,
    ): JellyfinItemDetail? {
        val environment = environmentProvider.current() ?: return null
        val now = clock.now()
        val cached = detailStore.get(itemId)
        if (!forceRefresh && cached != null) {
            return cached.toDomain()
        }
        val api = apiFor(environment)
        val dto = api.fetchItemDetail(environment.userId, itemId)
        detailStore.upsert(
            JellyfinItemDetailRecord(
                itemId = itemId,
                json = NetworkJson.default.encodeToString(dto),
                updatedAt = now,
            ),
        )
        // Sync base item metadata with latest detail overview.
        itemStore.get(itemId)?.let { existing ->
            itemStore.upsert(
                listOf(
                    existing.copy(
                        overview = dto.overview ?: existing.overview,
                        taglines = dto.taglines ?: existing.taglines,
                        runTimeTicks = dto.runTimeTicks ?: existing.runTimeTicks,
                        communityRating = dto.communityRating ?: existing.communityRating,
                        officialRating = dto.officialRating ?: existing.officialRating,
                        updatedAt = now,
                    ),
                ),
            )
        }
        return dto.toDomain()
    }

    private fun apiFor(environment: JellyfinEnvironment): JellyfinBrowseApi =
        cachedApis.getOrPut(environment.serverKey) { apiFactory(environment) }

    suspend fun currentServerBaseUrl(): String? = environmentProvider.current()?.baseUrl

    suspend fun currentAccessToken(): String? = environmentProvider.current()?.accessToken
}

private fun JellyfinLibraryDto.toRecord(
    environment: JellyfinEnvironment,
    now: Instant,
): JellyfinLibraryRecord =
    JellyfinLibraryRecord(
        id = id,
        serverId = environment.serverKey,
        name = name,
        collectionType = collectionType,
        primaryImageTag = primaryImageTag,
        itemCount = itemCount,
        createdAt = now,
        updatedAt = now,
    )

private fun JellyfinLibraryRecord.toDomain(): JellyfinLibrary =
    JellyfinLibrary(
        id = id,
        name = name,
        collectionType = collectionType,
        itemCount = itemCount,
        primaryImageTag = primaryImageTag,
    )

private fun JellyfinItemDto.toRecord(
    environment: JellyfinEnvironment,
    fallbackLibraryId: String?,
    updatedAt: Instant,
): JellyfinItemRecord =
    JellyfinItemRecord(
        id = id,
        serverId = environment.serverKey,
        libraryId = fallbackLibraryId ?: parentId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        taglines = taglines ?: emptyList(),
        parentId = parentId,
        primaryImageTag = imageTags?.get("Primary"),
        thumbImageTag = imageTags?.get("Thumb"),
        backdropImageTag = backdropImageTags?.firstOrNull() ?: parentBackdropImageTags?.firstOrNull(),
        seriesId = seriesId ?: parentId,
        seriesPrimaryImageTag =
            seriesPrimaryImageTag
                ?: imageTags?.get("Primary")?.takeIf { type.equals("Series", ignoreCase = true) },
        seriesThumbImageTag =
            seriesThumbImageTag
                ?: imageTags?.get("Thumb")?.takeIf { type.equals("Series", ignoreCase = true) },
        seriesBackdropImageTag =
            seriesBackdropImageTag
                ?: parentBackdropImageTags?.firstOrNull(),
        parentLogoImageTag = parentLogoImageTag ?: imageTags?.get("Logo"),
        runTimeTicks = runTimeTicks,
        positionTicks = userData?.playbackPositionTicks,
        playedPercentage = userData?.playedPercentage,
        productionYear = productionYear?.toLong(),
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        indexNumber = indexNumber?.toLong(),
        parentIndexNumber = parentIndexNumber?.toLong(),
        seriesName = seriesName,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        lastPlayed = userData?.lastPlayedDate,
        updatedAt = updatedAt,
    )

private fun JellyfinItemRecord.toDomain(): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = libraryId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        taglines = taglines,
        parentId = parentId,
        primaryImageTag = primaryImageTag,
        thumbImageTag = thumbImageTag,
        backdropImageTag = backdropImageTag,
        seriesId = seriesId,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        seriesThumbImageTag = seriesThumbImageTag,
        seriesBackdropImageTag = seriesBackdropImageTag,
        parentLogoImageTag = parentLogoImageTag,
        runTimeTicks = runTimeTicks,
        positionTicks = positionTicks,
        playedPercentage = playedPercentage,
        productionYear = productionYear?.toInt(),
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        indexNumber = indexNumber?.toInt(),
        parentIndexNumber = parentIndexNumber?.toInt(),
        seriesName = seriesName,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        lastPlayed = lastPlayed,
    )

private fun JellyfinItemDetailRecord.toDomain(): JellyfinItemDetail =
    NetworkJson.default.decodeFromString<JellyfinItemDetailDto>(json).toDomain()

private fun JellyfinItemDetailDto.toDomain(): JellyfinItemDetail =
    JellyfinItemDetail(
        id = id,
        name = name,
        overview = overview,
        taglines = taglines ?: emptyList(),
        runTimeTicks = runTimeTicks,
        productionYear = productionYear,
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        genres = genres ?: emptyList(),
        studios = studios?.map { it.name } ?: emptyList(),
        primaryImageTag = imageTags?.get("Primary"),
        backdropImageTags =
            buildList {
                backdropImageTags?.let { addAll(it) }
                parentBackdropImageTags?.let { addAll(it) }
            },
        mediaSources = mediaSources.map { it.toDomain() },
    )

private fun JellyfinMediaSourceDto.toDomain(): JellyfinMediaSource =
    JellyfinMediaSource(
        id = id,
        name = name,
        runTimeTicks = runTimeTicks,
        container = container,
        videoBitrate = videoBitrate,
        supportsDirectPlay = supportsDirectPlay ?: false,
        supportsDirectStream = supportsDirectStream ?: false,
        supportsTranscoding = supportsTranscoding ?: false,
        streams = mediaStreams.map { it.toDomain() },
    )

private fun JellyfinMediaStreamDto.toDomain(): JellyfinMediaStream =
    JellyfinMediaStream(
        type =
            when (type.lowercase()) {
                "video" -> VIDEO
                "audio" -> AUDIO
                "subtitle" -> SUBTITLE
                else -> OTHER
            },
        index = index,
        displayTitle = displayTitle,
        codec = codec,
        language = language,
        isDefault = isDefault ?: false,
        isForced = isForced ?: false,
    )
