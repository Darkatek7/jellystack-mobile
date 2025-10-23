package dev.jellystack.core.jellyseerr

import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.NetworkJson
import dev.jellystack.network.jellyseerr.JellyseerrApi
import dev.jellystack.network.jellyseerr.JellyseerrCreateRequestPayload
import dev.jellystack.network.jellyseerr.JellyseerrHttpException
import dev.jellystack.network.jellyseerr.JellyseerrLanguageProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrMediaInfoDto
import dev.jellystack.network.jellyseerr.JellyseerrMovieDetailsDto
import dev.jellystack.network.jellyseerr.JellyseerrProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrQualityProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestCountsDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestsResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrSearchResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrSearchResultDto
import dev.jellystack.network.jellyseerr.JellyseerrSeasonDto
import dev.jellystack.network.jellyseerr.JellyseerrServiceDetailsDto
import dev.jellystack.network.jellyseerr.JellyseerrServiceSummaryDto
import dev.jellystack.network.jellyseerr.JellyseerrTvDetailsDto
import dev.jellystack.network.jellyseerr.JellyseerrUserDto
import dev.jellystack.network.jellyseerr.seasonsAll
import dev.jellystack.network.jellyseerr.seasonsList
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private data class CachedApi(
        val api: JellyseerrApi,
    )

    private val apiCache = mutableMapOf<String, CachedApi>()
    private val credentialCache = mutableMapOf<String, Pair<String?, String?>>()
    private val metadataCache = mutableMapOf<MetadataCacheKey, JellyseerrMediaMetadata>()
    private val metadataOrder = ArrayDeque<MetadataCacheKey>()
    private val metadataMutex = Mutex()

    private suspend fun api(environment: JellyseerrEnvironment): JellyseerrApi {
        val cacheEntry = apiCache[environment.serverId]
        if (
            cacheEntry != null &&
            credentialCache[environment.serverId] == environment.apiKey to environment.sessionCookie
        ) {
            return cacheEntry.api
        }
        val api =
            JellyseerrApi
                .create(
                    baseUrl = environment.baseUrl,
                    apiKey = environment.apiKey,
                    sessionCookie = environment.sessionCookie,
                    apiUserId = environment.apiUserId,
                    client = client,
                )
        apiCache[environment.serverId] = CachedApi(api)
        credentialCache[environment.serverId] = environment.apiKey to environment.sessionCookie
        return api
    }

    private suspend fun getCachedMetadata(key: MetadataCacheKey): JellyseerrMediaMetadata? = metadataMutex.withLock { metadataCache[key] }

    private suspend fun setCachedMetadata(
        key: MetadataCacheKey,
        metadata: JellyseerrMediaMetadata,
    ) {
        metadataMutex.withLock {
            metadataCache[key] = metadata
            metadataOrder.remove(key)
            metadataOrder.addLast(key)
            while (metadataOrder.size > METADATA_CACHE_MAX_ENTRIES) {
                val oldest = metadataOrder.removeFirstOrNull() ?: break
                metadataCache.remove(oldest)
            }
        }
    }

    private suspend fun enrichRequestMetadata(
        environment: JellyseerrEnvironment,
        page: JellyseerrRequestsPage,
    ): JellyseerrRequestsPage {
        val results = page.results
        if (results.isEmpty()) {
            return page
        }
        val keysNeedingMetadata =
            results
                .filter { summary ->
                    summary.tmdbId != null &&
                        (
                            summary.title.isNullOrBlank() ||
                                summary.originalTitle.isNullOrBlank() ||
                                summary.posterPath.isNullOrBlank() ||
                                summary.backdropPath.isNullOrBlank()
                        )
                }.mapNotNull { summary ->
                    summary.tmdbId?.let { MetadataCacheKey(environment.serverId, summary.mediaType, it) }
                }.distinct()
        if (keysNeedingMetadata.isEmpty()) {
            return page
        }
        val metadataByKey = mutableMapOf<MetadataCacheKey, JellyseerrMediaMetadata>()
        for (key in keysNeedingMetadata) {
            val cached = getCachedMetadata(key)
            if (cached != null) {
                metadataByKey[key] = cached
                continue
            }
            val fetched = fetchMetadata(environment, key)
            if (fetched != null) {
                metadataByKey[key] = fetched
                setCachedMetadata(key, fetched)
            }
        }
        if (metadataByKey.isEmpty()) {
            return page
        }
        val enrichedResults =
            results.map { summary ->
                val tmdbId = summary.tmdbId ?: return@map summary
                val key = MetadataCacheKey(environment.serverId, summary.mediaType, tmdbId)
                val metadata = metadataByKey[key] ?: return@map summary
                summary.copy(
                    title = summary.title ?: metadata.title,
                    originalTitle = summary.originalTitle ?: metadata.originalTitle,
                    posterPath = summary.posterPath ?: metadata.posterPath,
                    backdropPath = summary.backdropPath ?: metadata.backdropPath,
                )
            }
        return page.copy(results = enrichedResults)
    }

    private suspend fun fetchMetadata(
        environment: JellyseerrEnvironment,
        key: MetadataCacheKey,
    ): JellyseerrMediaMetadata? =
        when (key.mediaType) {
            JellyseerrMediaType.MOVIE ->
                runCatching { api(environment).getMovieDetails(key.tmdbId) }
                    .map { it.toMetadata() }
                    .getOrNull()
            JellyseerrMediaType.TV ->
                runCatching { api(environment).getTvDetails(key.tmdbId) }
                    .map { it.toMetadata() }
                    .getOrNull()
            else -> null
        }

    private fun JellyseerrMovieDetailsDto.toMetadata(): JellyseerrMediaMetadata =
        JellyseerrMediaMetadata(
            title = firstNonBlank(title, originalTitle),
            originalTitle = firstNonBlank(originalTitle, title),
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
        )

    private fun JellyseerrTvDetailsDto.toMetadata(): JellyseerrMediaMetadata =
        JellyseerrMediaMetadata(
            title = firstNonBlank(name, originalName),
            originalTitle = firstNonBlank(originalName, name),
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
        )

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun String?.ifNotBlank(): String? = this?.takeUnless { it.isBlank() }

    private data class MetadataCacheKey(
        val serverId: String,
        val mediaType: JellyseerrMediaType,
        val tmdbId: Int,
    )

    private data class JellyseerrMediaMetadata(
        val title: String?,
        val originalTitle: String?,
        val posterPath: String?,
        val backdropPath: String?,
    )

    suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int = DEFAULT_PAGE_SIZE,
        skip: Int = 0,
    ): JellyseerrRequestsPage =
        try {
            val response = api(environment).listRequests(take = take, skip = skip, filter = filter.queryValue)
            val page = response.toDomain()
            enrichRequestMetadata(environment, page)
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to fetch Jellyseerr requests for ${environment.serverId} at ${environment.baseUrl}: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts =
        try {
            api(environment).getRequestCounts().toDomain()
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to fetch Jellyseerr counts for ${environment.serverId} at ${environment.baseUrl}: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int = 1,
    ): List<JellyseerrSearchItem> =
        try {
            api(environment)
                .search(query = query, page = page)
                .toDomainSearchResults()
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to search Jellyseerr for ${environment.serverId} at ${environment.baseUrl} with query '$query': ${error.message}",
                error,
            )
            throw error
        }

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
                serverId = request.serverId,
                profileId = request.profileId,
                languageProfileId = request.languageProfileId,
            )
        return try {
            val response = api(environment).createRequest(payload)
            JellyseerrCreateResult.Success(response.toDomain())
        } catch (error: JellyseerrHttpException) {
            JellystackLog.e(
                "Jellyseerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            val message = parseErrorMessage(error.responseBody)
            if (error.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(message ?: "This item has already been requested.")
            } else {
                JellyseerrCreateResult.Failure(message ?: "Request failed.", error)
            }
        } catch (error: ClientRequestException) {
            JellystackLog.e(
                "Jellyseerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            val message = extractErrorMessage(error)
            if (error.response.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(message ?: "This item has already been requested.")
            } else {
                JellyseerrCreateResult.Failure(message ?: "Request failed.", error)
            }
        } catch (error: ServerResponseException) {
            JellystackLog.e(
                "Jellyseerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            val message = extractErrorMessage(error)
            JellyseerrCreateResult.Failure(message ?: "Request failed.", error)
        } catch (error: Throwable) {
            JellystackLog.e(
                "Jellyseerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            JellyseerrCreateResult.Failure(error.message ?: "Request failed.", error)
        }
    }

    suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit> {
        val apiInstance = api(environment)
        return runCatching { apiInstance.deleteRequest(requestId) }
            .onFailure { error ->
                JellystackLog.e(
                    "Failed to delete Jellyseerr request $requestId for ${environment.serverId}: ${error.message}",
                    error,
                )
            }
    }

    suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit> {
        val apiInstance = api(environment)
        return runCatching {
            apiInstance.deleteMediaFiles(mediaId, is4k)
            apiInstance.deleteMedia(mediaId)
        }.onFailure { error ->
            JellystackLog.e(
                "Failed to remove Jellyseerr media $mediaId for ${environment.serverId}: ${error.message}",
                error,
            )
        }
    }

    suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary> {
        val apiInstance = api(environment)
        return runCatching { apiInstance.retryRequest(requestId).toDomain() }
            .onFailure { error ->
                JellystackLog.e(
                    "Failed to retry Jellyseerr request $requestId for ${environment.serverId}: ${error.message}",
                    error,
                )
            }
    }

    suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary> {
        val apiInstance = api(environment)
        return runCatching { apiInstance.updateRequestStatus(requestId, status).toDomain() }
            .onFailure { error ->
                JellystackLog.e(
                    "Failed to update Jellyseerr request $requestId for ${environment.serverId}: ${error.message}",
                    error,
                )
            }
    }

    suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile =
        try {
            api(environment).getProfile().toDomain()
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to load Jellyseerr profile for ${environment.serverId} at ${environment.baseUrl}: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun fetchLanguageProfiles(environment: JellyseerrEnvironment): JellyseerrLanguageProfiles {
        val apiInstance = api(environment)
        val radarrSummaries =
            runCatching { apiInstance.listRadarrServices() }
                .onFailure { error ->
                    JellystackLog.e(
                        "Failed to load Radarr services for ${environment.serverId}: ${error.message}",
                        error,
                    )
                }.getOrDefault(emptyList())
        val radarrProfiles =
            radarrSummaries.flatMap { summary ->
                val details =
                    runCatching { apiInstance.getRadarrServiceDetails(summary.id) }
                        .onFailure { error ->
                            JellystackLog.e(
                                "Failed to load Radarr service details ${summary.id} for ${environment.serverId}: ${error.message}",
                                error,
                            )
                        }.getOrNull()
                summary.toDomainLanguageProfiles(details)
            }
        val sonarrSummaries =
            runCatching { apiInstance.listSonarrServices() }
                .onFailure { error ->
                    JellystackLog.e(
                        "Failed to load Sonarr services for ${environment.serverId}: ${error.message}",
                        error,
                    )
                }.getOrDefault(emptyList())
        val sonarrProfiles =
            sonarrSummaries.flatMap { summary ->
                val details =
                    runCatching { apiInstance.getSonarrServiceDetails(summary.id) }
                        .onFailure { error ->
                            JellystackLog.e(
                                "Failed to load Sonarr service details ${summary.id} for ${environment.serverId}: ${error.message}",
                                error,
                            )
                        }.getOrNull()
                summary.toDomainLanguageProfiles(details)
            }
        return JellyseerrLanguageProfiles(
            movies = radarrProfiles,
            tv = sonarrProfiles,
        )
    }

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
        val resolvedTitle =
            firstNonBlank(
                effectiveMedia?.title,
                effectiveMedia?.name,
                effectiveMedia?.originalTitle,
                effectiveMedia?.originalName,
            )
        val resolvedOriginalTitle =
            firstNonBlank(
                effectiveMedia?.originalTitle,
                effectiveMedia?.originalName,
            )
        return JellyseerrRequestSummary(
            id = id,
            mediaId = effectiveMedia?.id ?: mediaId,
            tmdbId = effectiveMedia?.tmdbId,
            tvdbId = effectiveMedia?.tvdbId,
            title = resolvedTitle,
            originalTitle = resolvedOriginalTitle,
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
            posterPath = effectiveMedia?.posterPath.ifNotBlank(),
            backdropPath = effectiveMedia?.backdropPath.ifNotBlank(),
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

    private fun JellyseerrServiceSummaryDto.toDomainLanguageProfiles(
        details: JellyseerrServiceDetailsDto?,
    ): List<JellyseerrLanguageProfileOption> {
        val server = details?.server ?: this
        val resolvedName = server.name?.takeIf { it.isNotBlank() } ?: "Server ${server.id}"
        val fallbackProfileId = details?.server?.activeProfileId ?: activeProfileId
        val fallbackLanguageProfileId = details?.server?.activeLanguageProfileId ?: activeLanguageProfileId
        val languageProfiles = details?.languageProfiles.orEmpty()
        val qualityProfiles = details?.profiles.orEmpty()
        if (languageProfiles.isNotEmpty()) {
            return languageProfiles.map { profile ->
                profile.toDomain(
                    server = server,
                    resolvedName = resolvedName,
                    fallbackProfileId = fallbackProfileId,
                    activeLanguageProfileId = fallbackLanguageProfileId,
                )
            }
        }
        if (qualityProfiles.isNotEmpty()) {
            return qualityProfiles.map { profile ->
                profile.toDomain(
                    server = server,
                    resolvedName = resolvedName,
                    activeProfileId = fallbackProfileId,
                )
            }
        }
        return listOf(
            JellyseerrLanguageProfileOption(
                languageProfileId = fallbackLanguageProfileId,
                name = resolvedName,
                serviceId = server.id,
                serviceName = server.name,
                is4k = server.is4k ?: false,
                isDefault = (server.isDefault ?: false) || fallbackLanguageProfileId != null,
                profileId = fallbackProfileId,
            ),
        )
    }

    private fun JellyseerrLanguageProfileDto.toDomain(
        server: JellyseerrServiceSummaryDto,
        resolvedName: String,
        fallbackProfileId: Int?,
        activeLanguageProfileId: Int?,
    ): JellyseerrLanguageProfileOption =
        JellyseerrLanguageProfileOption(
            languageProfileId = id,
            name = name.takeIf { it.isNotBlank() } ?: resolvedName,
            serviceId = server.id,
            serviceName = server.name,
            is4k = server.is4k ?: false,
            isDefault =
                when {
                    activeLanguageProfileId != null -> id == activeLanguageProfileId
                    else -> server.isDefault ?: false
                },
            profileId = profileId ?: fallbackProfileId,
        )

    private fun JellyseerrQualityProfileDto.toDomain(
        server: JellyseerrServiceSummaryDto,
        resolvedName: String,
        activeProfileId: Int?,
    ): JellyseerrLanguageProfileOption =
        JellyseerrLanguageProfileOption(
            languageProfileId = null,
            name = name.takeIf { it.isNotBlank() } ?: resolvedName,
            serviceId = server.id,
            serviceName = server.name,
            is4k = server.is4k ?: false,
            isDefault =
                when {
                    activeProfileId != null -> id == activeProfileId
                    else -> server.isDefault ?: false
                },
            profileId = id,
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
        private const val METADATA_CACHE_MAX_ENTRIES = 100
    }
}
