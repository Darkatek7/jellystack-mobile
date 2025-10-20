package dev.jellystack.network.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

private const val API_PREFIX = "/api/v1"
private const val HEADER_API_KEY = "X-API-Key"
private const val HEADER_API_USER = "X-API-User"

/**
 * Thin wrapper over Jellyseerr REST API endpoints used by the app.
 * Handles common headers and request building but keeps response models close
 * to their wire representations so higher layers can map to domain models.
 */
class JellyseerrApi internal constructor(
    private val client: HttpClient,
    private val apiBaseUrl: String,
    private val apiKey: String,
    private val apiUserId: Int? = null,
) {
    companion object {
        fun create(
            baseUrl: String,
            apiKey: String,
            apiUserId: Int? = null,
            client: HttpClient? = null,
            clientConfig: ClientConfig.() -> Unit = {},
        ): JellyseerrApi {
            val normalizedBase = baseUrl.trimEnd('/')
            val httpClient =
                client
                    ?: NetworkClientFactory.create(
                        ClientConfig(
                            installLogging = false,
                        ).apply(clientConfig),
                    )
            return JellyseerrApi(httpClient, normalizedBase + API_PREFIX, apiKey, apiUserId)
        }
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrSearchResponseDto =
        client
            .get("$apiBaseUrl/search") {
                applyAuthHeaders()
                parameter("query", query)
                parameter("page", page)
                if (!language.isNullOrBlank()) {
                    parameter("language", language)
                }
            }.body()

    suspend fun listRequests(
        take: Int = 20,
        skip: Int = 0,
        filter: String? = null,
        sort: String? = null,
        sortDirection: String? = null,
        mediaType: String? = null,
        requestedBy: Int? = null,
    ): JellyseerrRequestsResponseDto =
        client
            .get("$apiBaseUrl/request") {
                applyAuthHeaders()
                parameter("take", take)
                parameter("skip", skip)
                filter?.let { parameter("filter", it) }
                sort?.let { parameter("sort", it) }
                sortDirection?.let { parameter("sortDirection", it) }
                mediaType?.let { parameter("mediaType", it) }
                requestedBy?.let { parameter("requestedBy", it) }
            }.body()

    suspend fun getRequestCounts(): JellyseerrRequestCountsDto =
        client
            .get("$apiBaseUrl/request/count") {
                applyAuthHeaders()
            }.body()

    suspend fun createRequest(payload: JellyseerrCreateRequestPayload): JellyseerrRequestDto =
        client
            .post("$apiBaseUrl/request") {
                applyAuthHeaders()
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.body()

    suspend fun loginWithJellyfin(payload: JellyseerrJellyfinLoginPayload): JellyseerrUserDto =
        client
            .post("$apiBaseUrl/auth/jellyfin") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.body()

    suspend fun fetchMainSettings(): JellyseerrMainSettingsDto =
        client
            .get("$apiBaseUrl/settings/main")
            .body()

    suspend fun logout(): Boolean {
        val response =
            client.post("$apiBaseUrl/auth/logout") {
                contentType(ContentType.Application.Json)
            }
        return response.status.isSuccess()
    }

    suspend fun deleteRequest(requestId: Int) {
        client.delete("$apiBaseUrl/request/$requestId") {
            applyAuthHeaders()
        }
    }

    suspend fun updateRequestStatus(
        requestId: Int,
        status: String,
    ): JellyseerrRequestDto =
        client
            .post("$apiBaseUrl/request/$requestId/$status") {
                applyAuthHeaders()
            }.body()

    suspend fun retryRequest(requestId: Int): JellyseerrRequestDto =
        client
            .post("$apiBaseUrl/request/$requestId/retry") {
                applyAuthHeaders()
            }.body()

    suspend fun deleteMedia(mediaId: Int) {
        client.delete("$apiBaseUrl/media/$mediaId") {
            applyAuthHeaders()
        }
    }

    suspend fun deleteMediaFiles(
        mediaId: Int,
        is4k: Boolean = false,
    ) {
        client.delete("$apiBaseUrl/media/$mediaId/file") {
            applyAuthHeaders()
            parameter("is4k", is4k)
        }
    }

    suspend fun getProfile(): JellyseerrProfileDto =
        client
            .get("$apiBaseUrl/auth/me") {
                applyAuthHeaders()
            }.body()

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders() {
        header(HEADER_API_KEY, apiKey)
        apiUserId?.let { header(HEADER_API_USER, it) }
    }
}

@Serializable
data class JellyseerrSearchResponseDto(
    val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 0,
    @SerialName("totalResults") val totalResults: Int = 0,
    val results: List<JellyseerrSearchResultDto> = emptyList(),
)

@Serializable
data class JellyseerrSearchResultDto(
    val id: Int,
    @SerialName("mediaType") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("mediaInfo") val mediaInfo: JellyseerrMediaInfoDto? = null,
)

@Serializable
data class JellyseerrMediaInfoDto(
    val id: Int,
    @SerialName("tmdbId") val tmdbId: Int? = null,
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    @SerialName("serviceId") val serviceId: Int? = null,
    @SerialName("serviceId4k") val serviceId4k: Int? = null,
    @SerialName("externalServiceSlug") val externalServiceSlug: String? = null,
    @SerialName("externalServiceSlug4k") val externalServiceSlug4k: String? = null,
    @SerialName("title") val title: String? = null,
    val slug: String? = null,
    @SerialName("requestCount") val requestCount: Int? = null,
    val requests: List<JellyseerrRequestDto> = emptyList(),
)

@Serializable
data class JellyseerrRequestDto(
    val id: Int,
    @SerialName("mediaId") val mediaId: Int? = null,
    @SerialName("status") val status: Int = 1,
    @SerialName("type") val type: String,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("profileName") val profileName: String? = null,
    @SerialName("canRemove") val canRemove: Boolean? = null,
    @SerialName("requestedBy") val requestedBy: JellyseerrUserDto? = null,
    @SerialName("modifiedBy") val modifiedBy: JellyseerrUserDto? = null,
    val media: JellyseerrMediaInfoDto? = null,
    val seasons: List<JellyseerrSeasonDto> = emptyList(),
)

@Serializable
data class JellyseerrSeasonDto(
    val id: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("status") val status: Int? = null,
)

@Serializable
data class JellyseerrUserDto(
    val id: Int? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("jellyfinUsername") val jellyfinUsername: String? = null,
    val permissions: Int? = null,
)

@Serializable
data class JellyseerrRequestsResponseDto(
    @SerialName("pageInfo") val pageInfo: JellyseerrPageInfoDto? = null,
    val results: List<JellyseerrRequestDto> = emptyList(),
)

@Serializable
data class JellyseerrPageInfoDto(
    val pages: Int? = null,
    @SerialName("pageSize") val pageSize: Int? = null,
    val results: Int? = null,
    val page: Int? = null,
)

@Serializable
data class JellyseerrRequestCountsDto(
    val total: Int? = null,
    val movie: Int? = null,
    val tv: Int? = null,
    val pending: Int? = null,
    val approved: Int? = null,
    val declined: Int? = null,
    val processing: Int? = null,
    val available: Int? = null,
    val completed: Int? = null,
)

@Serializable
data class JellyseerrProfileDto(
    val id: Int,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    val permissions: Int = 0,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("userType") val userType: String? = null,
)

@Serializable
data class JellyseerrCreateRequestPayload(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("mediaId") val mediaId: Int,
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("seasons") val seasons: JsonElement? = null,
    @SerialName("is4k") val is4k: Boolean? = null,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("languageProfileId") val languageProfileId: Int? = null,
    @SerialName("userId") val userId: Int? = null,
    @SerialName("tags") val tags: List<Int>? = null,
)

fun seasonsAll(): JsonElement = JsonPrimitive("all")

fun seasonsList(numbers: List<Int>): JsonElement = JsonArray(numbers.map { JsonPrimitive(it) })

@Serializable
data class JellyseerrJellyfinLoginPayload(
    val username: String,
    val password: String,
    val hostname: String? = null,
    val port: Int? = null,
    @SerialName("urlBase") val urlBase: String? = null,
    @SerialName("useSsl") val useSsl: Boolean? = null,
    @SerialName("serverType") val serverType: Int = 2,
    val email: String? = null,
)

@Serializable
data class JellyseerrMainSettingsDto(
    @SerialName("apiKey") val apiKey: String? = null,
)
