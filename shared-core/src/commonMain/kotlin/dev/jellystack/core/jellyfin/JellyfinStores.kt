package dev.jellystack.core.jellyfin

import kotlinx.datetime.Instant

data class JellyfinLibraryRecord(
    val id: String,
    val serverId: String,
    val name: String,
    val collectionType: String?,
    val primaryImageTag: String?,
    val itemCount: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class JellyfinItemRecord(
    val id: String,
    val serverId: String,
    val libraryId: String?,
    val name: String,
    val sortName: String?,
    val overview: String?,
    val type: String,
    val mediaType: String?,
    val taglines: List<String>,
    val parentId: String?,
    val primaryImageTag: String?,
    val thumbImageTag: String?,
    val backdropImageTag: String?,
    val seriesId: String?,
    val seriesPrimaryImageTag: String?,
    val seriesThumbImageTag: String?,
    val seriesBackdropImageTag: String?,
    val parentLogoImageTag: String?,
    val runTimeTicks: Long?,
    val positionTicks: Long?,
    val playedPercentage: Double?,
    val productionYear: Long?,
    val premiereDate: String?,
    val communityRating: Double?,
    val officialRating: String?,
    val indexNumber: Long?,
    val parentIndexNumber: Long?,
    val seriesName: String?,
    val seasonId: String?,
    val episodeTitle: String?,
    val lastPlayed: String?,
    val updatedAt: Instant,
)

data class JellyfinItemDetailRecord(
    val itemId: String,
    val json: String,
    val updatedAt: Instant,
)

interface JellyfinLibraryStore {
    suspend fun replaceAll(
        serverId: String,
        libraries: List<JellyfinLibraryRecord>,
    )

    suspend fun list(serverId: String): List<JellyfinLibraryRecord>
}

interface JellyfinItemStore {
    suspend fun replaceForLibrary(
        serverId: String,
        libraryId: String,
        items: List<JellyfinItemRecord>,
    )

    suspend fun upsert(items: List<JellyfinItemRecord>)

    suspend fun listByLibrary(
        serverId: String,
        libraryId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord>

    suspend fun listContinueWatching(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord>

    suspend fun get(itemId: String): JellyfinItemRecord?
}

interface JellyfinItemDetailStore {
    suspend fun get(itemId: String): JellyfinItemDetailRecord?

    suspend fun upsert(record: JellyfinItemDetailRecord)
}
