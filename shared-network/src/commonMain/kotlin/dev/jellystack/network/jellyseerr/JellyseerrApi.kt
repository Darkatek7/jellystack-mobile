package dev.jellystack.network.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

interface JellyseerrSessionCookieHandler {
    suspend fun currentCookie(): String?

    suspend fun refreshCookie(): String?
}

/**
 * Thin wrapper over Jellyseerr REST API endpoints used by the app.
 * Handles common headers and request building but keeps response models close
 * to their wire representations so higher layers can map to domain models.
 */
class JellyseerrApi internal constructor(
    private val client: HttpClient,
    private val apiBaseUrl: String,
    private val apiKey: String?,
    sessionCookie: String?,
    private val sessionHandler: JellyseerrSessionCookieHandler?,
    private val apiUserId: Int? = null,
) {
    private var sessionCookieCache: String? = sessionCookie

    companion object {
        fun create(
            baseUrl: String,
            apiKey: String?,
            sessionCookie: String? = null,
            apiUserId: Int? = null,
            sessionHandler: JellyseerrSessionCookieHandler? = null,
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
            return JellyseerrApi(
                client = httpClient,
                apiBaseUrl = normalizedBase + API_PREFIX,
                apiKey = apiKey,
                sessionCookie = sessionCookie,
                sessionHandler = sessionHandler,
                apiUserId = apiUserId,
            )
        }
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrSearchResponseDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/search") {
                    applyAuthHeaders(cookie)
                    parameter("query", query)
                    parameter("page", page)
                    if (!language.isNullOrBlank()) {
                        parameter("language", language)
                    }
                }.body()
        }

    suspend fun listRequests(
        take: Int = 20,
        skip: Int = 0,
        filter: String? = null,
        sort: String? = null,
        sortDirection: String? = null,
        mediaType: String? = null,
        requestedBy: Int? = null,
    ): JellyseerrRequestsResponseDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/request") {
                    applyAuthHeaders(cookie)
                    parameter("take", take)
                    parameter("skip", skip)
                    filter?.let { parameter("filter", it) }
                    sort?.let { parameter("sort", it) }
                    sortDirection?.let { parameter("sortDirection", it) }
                    mediaType?.let { parameter("mediaType", it) }
                    requestedBy?.let { parameter("requestedBy", it) }
                }.body()
        }

    suspend fun getRequestCounts(): JellyseerrRequestCountsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/request/count") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun createRequest(payload: JellyseerrCreateRequestPayload): JellyseerrRequestDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .post("$apiBaseUrl/request") {
                    applyAuthHeaders(cookie)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }.toBodyOrThrow()
        }

    suspend fun loginWithCredentials(payload: JellyseerrLocalLoginPayload): JellyseerrUserDto =
        client
            .post("$apiBaseUrl/auth/local") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.body()

    suspend fun deleteRequest(requestId: Int) {
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client.delete("$apiBaseUrl/request/$requestId") {
                applyAuthHeaders(cookie)
            }
        }
    }

    suspend fun updateRequestStatus(
        requestId: Int,
        status: String,
    ): JellyseerrRequestDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .post("$apiBaseUrl/request/$requestId/$status") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun retryRequest(requestId: Int): JellyseerrRequestDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .post("$apiBaseUrl/request/$requestId/retry") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun deleteMedia(mediaId: Int) {
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client.delete("$apiBaseUrl/media/$mediaId") {
                applyAuthHeaders(cookie)
            }
        }
    }

    suspend fun deleteMediaFiles(
        mediaId: Int,
        is4k: Boolean = false,
    ) {
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client.delete("$apiBaseUrl/media/$mediaId/file") {
                applyAuthHeaders(cookie)
                parameter("is4k", is4k)
            }
        }
    }

    suspend fun getProfile(): JellyseerrProfileDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/auth/me") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    private suspend fun prepareSessionCookie(): String? {
        val handlerCookie = sessionHandler?.currentCookie()
        if (!handlerCookie.isNullOrBlank()) {
            sessionCookieCache = handlerCookie
        }
        return sessionCookieCache
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders(cookie: String?) {
        apiKey?.let { header(HEADER_API_KEY, it) }
        apiUserId?.let { header(HEADER_API_USER, it) }
        cookie?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Cookie, it) }
    }

    private suspend fun <T> withSessionRetry(block: suspend () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                val handler = sessionHandler
                if (handler == null || !shouldRefreshSession(error) || attempt == 1) {
                    throw error
                }
                val refreshResult = runCatching { handler.refreshCookie() }
                if (refreshResult.isFailure) {
                    throw error
                }
                sessionCookieCache = refreshResult.getOrNull()
                attempt++
            }
        }
        throw lastError ?: IllegalStateException("Session retry failed")
    }

    private fun shouldRefreshSession(error: Throwable): Boolean =
        when (error) {
            is ClientRequestException ->
                error.response.status == HttpStatusCode.Unauthorized ||
                    error.response.status == HttpStatusCode.Forbidden
            is JellyseerrHttpException ->
                error.status == HttpStatusCode.Unauthorized ||
                    error.status == HttpStatusCode.Forbidden
            else -> false
        }

    private suspend inline fun <reified T> HttpResponse.toBodyOrThrow(): T {
        if (!status.isSuccess()) {
            val responseText = runCatching { bodyAsText() }.getOrNull()
            throw JellyseerrHttpException(status, responseText)
        }
        return body()
    }
}

class JellyseerrHttpException(
    val status: HttpStatusCode,
    val responseBody: String?,
) : Exception(
        buildString {
            append("HTTP ${status.value} ${status.description}")
            if (!responseBody.isNullOrBlank()) {
                append(": ")
                append(responseBody)
            }
        },
    )

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
    @SerialName("apiKey") val apiKey: String? = null,
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
data class JellyseerrLocalLoginPayload(
    val email: String,
    val password: String,
)
