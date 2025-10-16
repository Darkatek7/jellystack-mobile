package dev.jellystack.core.jellyfin

data class JellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String?,
    val itemCount: Long?,
    val primaryImageTag: String?,
)

data class JellyfinItem(
    val id: String,
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
    val productionYear: Int?,
    val premiereDate: String?,
    val communityRating: Double?,
    val officialRating: String?,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val seriesName: String?,
    val seasonId: String?,
    val episodeTitle: String?,
    val lastPlayed: String?,
)

data class JellyfinItemDetail(
    val id: String,
    val name: String,
    val overview: String?,
    val taglines: List<String>,
    val runTimeTicks: Long?,
    val productionYear: Int?,
    val premiereDate: String?,
    val communityRating: Double?,
    val officialRating: String?,
    val genres: List<String>,
    val studios: List<String>,
    val primaryImageTag: String?,
    val backdropImageTags: List<String>,
    val mediaSources: List<JellyfinMediaSource>,
)

data class JellyfinMediaSource(
    val id: String,
    val name: String?,
    val runTimeTicks: Long?,
    val container: String?,
    val videoBitrate: Int?,
    val supportsDirectPlay: Boolean,
    val supportsDirectStream: Boolean,
    val supportsTranscoding: Boolean,
    val streams: List<JellyfinMediaStream>,
)

data class JellyfinMediaStream(
    val type: JellyfinMediaStreamType,
    val index: Int?,
    val displayTitle: String?,
    val codec: String?,
    val language: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
)

enum class JellyfinMediaStreamType {
    VIDEO,
    AUDIO,
    SUBTITLE,
    OTHER,
}
