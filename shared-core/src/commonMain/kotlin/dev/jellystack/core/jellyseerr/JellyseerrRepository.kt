package dev.jellystack.core.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.NetworkJson
import dev.jellystack.network.jellyseerr.JellyseerrApi
import dev.jellystack.network.jellyseerr.JellyseerrCreateRequestPayload
import dev.jellystack.network.jellyseerr.JellyseerrHttpException
import dev.jellystack.network.jellyseerr.JellyseerrMediaInfoDto
import dev.jellystack.network.jellyseerr.JellyseerrProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestCountsDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestsResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrSearchResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrSearchResultDto
import dev.jellystack.network.jellyseerr.JellyseerrSeasonDto
import dev.jellystack.network.jellyseerr.JellyseerrUserDto
import dev.jellystack.network.jellyseerr.seasonsAll
import dev.jellystack.network.jellyseerr.seasonsList
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JellyseerrRepository(
    httpClient: HttpClient? = null,
    private val json: Json = NetworkJson.default,
) {
    private val client: HttpClient =
        httpClient ?: NetworkClientFactory.create(ClientConfig(installLogging = false))
    private val apiCache = mutableMapOf<String, JellyseerrApi>()

    fun api(environment: JellyseerrEnvironment): JellyseerrApi =
        apiCache.getOrPut(environment.serverId) {
            JellyseerrApi.create(
                baseUrl = environment.baseUrl,
                apiKey = environment.apiKey,
                apiUserId = environment.apiUserId,
                client = client,
            )
        }

    suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int = DEFAULT_PAGE_SIZE,
        skip: Int = 0,
    ): JellyseerrRequestsPage {
        val response = api(environment).listRequests(take = take, skip = skip, filter = filter.queryValue)
        return response.toDomain()
    }

    suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts = api(environment).getRequestCounts().toDomain()

    suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int = 1,
    ): List<JellyseerrSearchItem> =
        api(environment)
            .search(query = query, page = page)
            .toDomainSearchResults()

    suspend fun createRequest(
        environment: JellyseerrEnvironment,
        request: JellyseerrCreateRequest,
    ): JellyseerrCreateResult {
        val payload =
            JellyseerrCreateRequestPayload(
                mediaType = request.mediaType.toWireValue(),
                mediaId = request.mediaId,
                tvdbId = request.tvdbId,
                seasons =
                    when (val selection = request.seasons) {
                        JellyseerrCreateSelection.AllSeasons -> seasonsAll()
                        is JellyseerrCreateSelection.Seasons -> seasonsList(selection.numbers)
                        null -> null
                    },
                is4k =
                    request.is4k.takeIf {
                        request.mediaType == JellyseerrMediaType.MOVIE || request.mediaType == JellyseerrMediaType.TV
                    },
            )
        return try {
            val response = api(environment).createRequest(payload)
            JellyseerrCreateResult.Success(response.toDomain())
        } catch (error: JellyseerrHttpException) {
            val message = parseErrorMessage(error.responseBody)
            if (error.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(message ?: "This item has already been requested.")
            } else {
                JellyseerrCreateResult.Failure(message ?: "Request failed.", error)
            }
        } catch (error: ClientRequestException) {
            val message = extractErrorMessage(error)
            if (error.response.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(message ?: "This item has already been requested.")
            } else {
                JellyseerrCreateResult.Failure(message ?: "Request failed.", error)
            }
        } catch (error: ServerResponseException) {
            val message = extractErrorMessage(error)
            JellyseerrCreateResult.Failure(message ?: "Request failed.", error)
        } catch (error: Throwable) {
            JellyseerrCreateResult.Failure(error.message ?: "Request failed.", error)
        }
    }

    suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit> =
        runCatching {
            api(environment).deleteRequest(requestId)
        }

    suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit> =
        runCatching {
            val api = api(environment)
            api.deleteMediaFiles(mediaId, is4k)
            api.deleteMedia(mediaId)
        }

    suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary> =
        runCatching {
            api(environment).retryRequest(requestId).toDomain()
        }

    suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary> =
        runCatching {
            api(environment).updateRequestStatus(requestId, status).toDomain()
        }

    suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile = api(environment).getProfile().toDomain()

    private suspend fun extractErrorMessage(error: ClientRequestException): String? =
        runCatching {
            val body = error.response.bodyAsText()
            parseErrorMessage(body)
        }.getOrNull()

    private suspend fun extractErrorMessage(error: ServerResponseException): String? =
        runCatching {
            val body = error.response.bodyAsText()
            parseErrorMessage(body)
        }.getOrNull()

    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<JellyseerrErrorDto>(body).message
        }.getOrNull()
            ?: runCatching {
                json
                    .parseToJsonElement(body)
                    .jsonObject["message"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
            ?: body
    }

    private fun JellyseerrRequestsResponseDto.toDomain(): JellyseerrRequestsPage {
        val pageInfo = pageInfo
        val mapped =
            results.map { it.toDomain() }
        return JellyseerrRequestsPage(
            page = pageInfo?.page ?: 1,
            pageSize = pageInfo?.pageSize ?: mapped.size,
            totalResults = pageInfo?.results ?: mapped.size,
            totalPages = pageInfo?.pages ?: 1,
            results = mapped,
        )
    }

    private fun JellyseerrRequestCountsDto.toDomain(): JellyseerrRequestCounts =
        JellyseerrRequestCounts(
            total = total ?: 0,
            movie = movie ?: 0,
            tv = tv ?: 0,
            pending = pending ?: 0,
            approved = approved ?: 0,
            declined = declined ?: 0,
            processing = processing ?: 0,
            available = available ?: 0,
            completed = completed ?: 0,
        )

    private fun JellyseerrRequestDto.toDomain(): JellyseerrRequestSummary {
        val effectiveMedia = media
        val availability =
            JellyseerrMediaAvailability(
                standard = JellyseerrMediaStatus.from(effectiveMedia?.status),
                `4k` = JellyseerrMediaStatus.from(effectiveMedia?.status4k),
            )
        return JellyseerrRequestSummary(
            id = id,
            mediaId = effectiveMedia?.id ?: mediaId,
            tmdbId = effectiveMedia?.tmdbId,
            tvdbId = effectiveMedia?.tvdbId,
            title = effectiveMedia?.title,
            mediaType = JellyseerrMediaType.from(type),
            requestStatus = JellyseerrRequestStatus.from(status),
            availability = availability,
            is4k = is4k,
            canRemoveFromService = canRemoveFromService(),
            createdAt = parseInstant(createdAt),
            updatedAt = parseInstant(updatedAt),
            requestedBy = requestedBy?.toDomain(),
            profileName = profileName,
            seasons = seasons.mapNotNull { it.toDomain() },
        )
    }

    private fun JellyseerrRequestDto.canRemoveFromService(): Boolean =
        when {
            canRemove == true -> true
            else -> false
        }

    private fun JellyseerrSearchResponseDto.toDomainSearchResults(): List<JellyseerrSearchItem> =
        results
            .mapNotNull { result ->
                if (result.mediaType.equals("person", ignoreCase = true) || result.mediaType.equals("collection", ignoreCase = true)) {
                    return@mapNotNull null
                }
                result.toDomain()
            }

    private fun JellyseerrSearchResultDto.toDomain(): JellyseerrSearchItem {
        val movieOrTvTitle = title ?: name ?: ""
        val releaseDateValue = releaseDate
        val firstAirDateValue = firstAirDate
        val releaseYear =
            when {
                !releaseDateValue.isNullOrBlank() && releaseDateValue.length >= 4 -> releaseDateValue.substring(0, 4)
                !firstAirDateValue.isNullOrBlank() && firstAirDateValue.length >= 4 -> firstAirDateValue.substring(0, 4)
                else -> null
            }
        val info = mediaInfo
        val availability =
            JellyseerrMediaAvailability(
                standard = JellyseerrMediaStatus.from(info?.status),
                `4k` = JellyseerrMediaStatus.from(info?.status4k),
            )
        return JellyseerrSearchItem(
            tmdbId = id,
            mediaType = JellyseerrMediaType.from(mediaType),
            title = movieOrTvTitle,
            overview = overview,
            releaseYear = releaseYear,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaInfoId = info?.id,
            tvdbId = info?.tvdbId,
            availability = availability,
            requests = info?.requests?.map { it.toDomainWith(info) } ?: emptyList(),
        )
    }

    private fun JellyseerrRequestDto.toDomainWith(mediaInfo: JellyseerrMediaInfoDto): JellyseerrRequestSummary =
        this.copy(media = mediaInfo).toDomain()

    private fun JellyseerrSeasonDto.toDomain(): JellyseerrSeasonStatus? {
        val seasonNumber = seasonNumber ?: return null
        return JellyseerrSeasonStatus(
            seasonNumber = seasonNumber,
            status = JellyseerrRequestStatus.from(status),
        )
    }

    private fun JellyseerrUserDto.toDomain(): JellyseerrUser =
        JellyseerrUser(
            id = id,
            displayName = displayName,
            username = username,
            permissions = permissions,
        )

    private fun JellyseerrProfileDto.toDomain(): JellyseerrProfile =
        JellyseerrProfile(
            id = id,
            displayName = displayName ?: username,
            permissions = permissions,
        )

    private fun JellyseerrMediaType.toWireValue(): String =
        when (this) {
            JellyseerrMediaType.MOVIE -> "movie"
            JellyseerrMediaType.TV -> "tv"
            JellyseerrMediaType.PERSON -> "person"
            JellyseerrMediaType.COLLECTION -> "collection"
            JellyseerrMediaType.UNKNOWN -> "movie"
        }

    private fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    @Serializable
    private data class JellyseerrErrorDto(
        val status: Int? = null,
        val message: String? = null,
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
