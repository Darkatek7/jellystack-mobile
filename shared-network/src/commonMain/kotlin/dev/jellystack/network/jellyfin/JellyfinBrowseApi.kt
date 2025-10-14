package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal Jellyfin browse client covering libraries, paged items, continue watching, and detail fetches.
 */
class JellyfinBrowseApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val deviceId: String? = null,
    private val clientName: String = "Jellystack",
    private val deviceName: String = "KotlinMultiplatform",
    private val clientVersion: String = "0.1",
) {
    private fun HttpRequestBuilder.configure(pathSuffix: String) {
        url {
            takeFrom(baseUrl)
            path(pathSuffix.trimStart('/'))
        }
        headers.apply {
            appendIfAbsent("X-Emby-Token", accessToken)
            appendIfAbsent("X-Emby-Authorization", authHeaderValue())
        }
    }

    private fun HeadersBuilder.appendIfAbsent(
        name: String,
        value: String,
    ) {
        if (!contains(name)) {
            append(name, value)
        }
    }

    private fun authHeaderValue(): String {
        val sanitizedDevice = deviceId ?: "unknown"
        return buildString {
            append("MediaBrowser Client=\"")
            append(clientName)
            append("\", Device=\"")
            append(deviceName)
            append("\", DeviceId=\"")
            append(sanitizedDevice)
            append("\", Version=\"")
            append(clientVersion)
            append("\"")
        }
    }

    suspend fun fetchLibraries(userId: String): JellyfinViewsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Views")
            }.body()

    suspend fun fetchLibraryItems(
        userId: String,
        libraryId: String,
        startIndex: Int,
        limit: Int,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items")
                parameter("ParentId", libraryId)
                parameter("IncludeItemTypes", "Movie,Series,Episode,BoxSet,MusicAlbum,MusicArtist")
                parameter("Recursive", true)
                parameter("StartIndex", startIndex)
                parameter("Limit", limit)
                parameter("SortBy", "SortName")
                parameter("SortOrder", "Ascending")
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            }.body()

    suspend fun fetchContinueWatching(
        userId: String,
        limit: Int,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items/Resume")
                parameter("Limit", limit)
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            }.body()

    suspend fun fetchItemDetail(
        userId: String,
        itemId: String,
    ): JellyfinItemDetailDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items/$itemId")
                parameter("Fields", DETAIL_FIELDS)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                parameter("ImageTypeLimit", 1)
            }.body()

    companion object {
        private const val REQUIRED_FIELDS =
            "PrimaryImageAspectRatio,MediaSourceCount,BasicSyncInfo,CanDelete,Genres," +
                "SeasonUserData,ChildCount,SeriesInfo,CollectionType,Overview,Taglines,Studios," +
                "PremiereDate,ProductionYear,ProviderIds,ParentLogoImageTag"
        private const val DETAIL_FIELDS =
            REQUIRED_FIELDS +
                ",MediaStreams,SeasonUserData,ParentBackdropImageTags,ParentLogoImageTags," +
                "ProviderIds,Path,MediaSources"
    }
}

@Serializable
data class JellyfinViewsResponse(
    @SerialName("Items")
    val items: List<JellyfinLibraryDto> = emptyList(),
)

@Serializable
data class JellyfinLibraryDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("CollectionType")
    val collectionType: String? = null,
    @SerialName("PrimaryImageTag")
    val primaryImageTag: String? = null,
    @SerialName("ItemCount")
    val itemCount: Long? = null,
)

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items")
    val items: List<JellyfinItemDto> = emptyList(),
    @SerialName("TotalRecordCount")
    val totalRecordCount: Long? = null,
)

@Serializable
data class JellyfinItemDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Type")
    val type: String,
    @SerialName("MediaType")
    val mediaType: String? = null,
    @SerialName("SortName")
    val sortName: String? = null,
    @SerialName("Overview")
    val overview: String? = null,
    @SerialName("Taglines")
    val taglines: List<String>? = null,
    @SerialName("ParentId")
    val parentId: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    @SerialName("PremiereDate")
    val premiereDate: String? = null,
    @SerialName("CommunityRating")
    val communityRating: Double? = null,
    @SerialName("OfficialRating")
    val officialRating: String? = null,
    @SerialName("IndexNumber")
    val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    @SerialName("SeriesName")
    val seriesName: String? = null,
    @SerialName("SeasonName")
    val seasonName: String? = null,
    @SerialName("ChannelId")
    val channelId: String? = null,
    @SerialName("ImageTags")
    val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    @SerialName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
    @SerialName("SeriesPrimaryImageTag")
    val seriesPrimaryImageTag: String? = null,
    @SerialName("SeriesThumbImageTag")
    val seriesThumbImageTag: String? = null,
    @SerialName("SeriesBackdropImageTag")
    val seriesBackdropImageTag: String? = null,
    @SerialName("ParentLogoImageTag")
    val parentLogoImageTag: String? = null,
    @SerialName("UserData")
    val userData: JellyfinItemUserData? = null,
    @SerialName("SeriesId")
    val seriesId: String? = null,
    @SerialName("SeasonId")
    val seasonId: String? = null,
    @SerialName("EpisodeTitle")
    val episodeTitle: String? = null,
)

@Serializable
data class JellyfinItemUserData(
    @SerialName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    @SerialName("PlayCount")
    val playCount: Int? = null,
    @SerialName("Played")
    val played: Boolean? = null,
    @SerialName("PlayedPercentage")
    val playedPercentage: Double? = null,
    @SerialName("LastPlayedDate")
    val lastPlayedDate: String? = null,
)

@Serializable
data class JellyfinItemDetailDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Overview")
    val overview: String? = null,
    @SerialName("Taglines")
    val taglines: List<String>? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    @SerialName("PremiereDate")
    val premiereDate: String? = null,
    @SerialName("CommunityRating")
    val communityRating: Double? = null,
    @SerialName("OfficialRating")
    val officialRating: String? = null,
    @SerialName("Genres")
    val genres: List<String>? = null,
    @SerialName("Studios")
    val studios: List<JellyfinStudioDto>? = null,
    @SerialName("MediaSources")
    val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerialName("ImageTags")
    val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    @SerialName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
)

@Serializable
data class JellyfinStudioDto(
    @SerialName("Name")
    val name: String,
)

@Serializable
data class JellyfinMediaSourceDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("Container")
    val container: String? = null,
    @SerialName("VideoBitrate")
    val videoBitrate: Int? = null,
    @SerialName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean? = null,
    @SerialName("SupportsTranscoding")
    val supportsTranscoding: Boolean? = null,
    @SerialName("MediaStreams")
    val mediaStreams: List<JellyfinMediaStreamDto> = emptyList(),
)

@Serializable
data class JellyfinMediaStreamDto(
    @SerialName("Type")
    val type: String,
    @SerialName("Index")
    val index: Int? = null,
    @SerialName("DisplayTitle")
    val displayTitle: String? = null,
    @SerialName("Codec")
    val codec: String? = null,
    @SerialName("Language")
    val language: String? = null,
    @SerialName("IsDefault")
    val isDefault: Boolean? = null,
    @SerialName("IsForced")
    val isForced: Boolean? = null,
)
