package dev.jellystack.core.jellyseerr

import kotlinx.datetime.Instant

data class JellyseerrEnvironment(
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    val apiKey: String?,
    val sessionCookie: String?,
    val apiUserId: Int? = null,
)

enum class JellyseerrMediaType {
    MOVIE,
    TV,
    PERSON,
    COLLECTION,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): JellyseerrMediaType =
            when (value?.lowercase()) {
                "movie" -> MOVIE
                "tv" -> TV
                "person" -> PERSON
                "collection" -> COLLECTION
                else -> UNKNOWN
            }
    }
}

enum class JellyseerrRequestStatus {
    PENDING,
    APPROVED,
    DECLINED,
    FAILED,
    COMPLETED,
    UNKNOWN,
    ;

    companion object {
        fun from(value: Int?): JellyseerrRequestStatus =
            when (value) {
                1 -> PENDING
                2 -> APPROVED
                3 -> DECLINED
                4 -> FAILED
                5 -> COMPLETED
                else -> UNKNOWN
            }
    }
}

enum class JellyseerrMediaStatus {
    UNKNOWN,
    PENDING,
    PROCESSING,
    PARTIALLY_AVAILABLE,
    AVAILABLE,
    BLACKLISTED,
    DELETED,
    ;

    companion object {
        fun from(value: Int?): JellyseerrMediaStatus =
            when (value) {
                1 -> UNKNOWN
                2 -> PENDING
                3 -> PROCESSING
                4 -> PARTIALLY_AVAILABLE
                5 -> AVAILABLE
                6 -> BLACKLISTED
                7 -> DELETED
                else -> UNKNOWN
            }
    }
}

data class JellyseerrUser(
    val id: Int?,
    val displayName: String?,
    val username: String?,
    val permissions: Int?,
)

data class JellyseerrSeasonStatus(
    val seasonNumber: Int,
    val status: JellyseerrRequestStatus,
)

data class JellyseerrMediaAvailability(
    val standard: JellyseerrMediaStatus?,
    val `4k`: JellyseerrMediaStatus?,
) {
    val isAvailable: Boolean
        get() = standard == JellyseerrMediaStatus.AVAILABLE
}

data class JellyseerrRequestSummary(
    val id: Int,
    val mediaId: Int?,
    val tmdbId: Int?,
    val tvdbId: Int?,
    val title: String?,
    val originalTitle: String?,
    val mediaType: JellyseerrMediaType,
    val requestStatus: JellyseerrRequestStatus,
    val availability: JellyseerrMediaAvailability,
    val is4k: Boolean,
    val canRemoveFromService: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val requestedBy: JellyseerrUser?,
    val profileName: String?,
    val seasons: List<JellyseerrSeasonStatus>,
    val posterPath: String?,
    val backdropPath: String?,
)

data class JellyseerrRequestCounts(
    val total: Int = 0,
    val movie: Int = 0,
    val tv: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val declined: Int = 0,
    val processing: Int = 0,
    val available: Int = 0,
    val completed: Int = 0,
)

data class JellyseerrRequestsPage(
    val page: Int,
    val pageSize: Int,
    val totalResults: Int,
    val totalPages: Int,
    val results: List<JellyseerrRequestSummary>,
)

data class JellyseerrSearchItem(
    val tmdbId: Int,
    val mediaType: JellyseerrMediaType,
    val title: String,
    val overview: String?,
    val releaseYear: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaInfoId: Int?,
    val tvdbId: Int?,
    val availability: JellyseerrMediaAvailability,
    val requests: List<JellyseerrRequestSummary>,
) {
    val isRequested: Boolean
        get() = requests.isNotEmpty()
}

enum class JellyseerrRequestFilter(
    val queryValue: String?,
) {
    ALL(null),
    PENDING("pending"),
    APPROVED("approved"),
    PROCESSING("processing"),
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    FAILED("failed"),
    DELETED("deleted"),
}

sealed interface JellyseerrCreateSelection {
    data object AllSeasons : JellyseerrCreateSelection

    data class Seasons(
        val numbers: List<Int>,
    ) : JellyseerrCreateSelection
}

data class JellyseerrCreateRequest(
    val mediaId: Int,
    val tvdbId: Int?,
    val mediaType: JellyseerrMediaType,
    val is4k: Boolean = false,
    val seasons: JellyseerrCreateSelection? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val languageProfileId: Int? = null,
)

data class JellyseerrLanguageProfileOption(
    val languageProfileId: Int?,
    val name: String,
    val serviceId: Int?,
    val serviceName: String?,
    val is4k: Boolean,
    val isDefault: Boolean,
    val profileId: Int?,
)

data class JellyseerrLanguageProfiles(
    val movies: List<JellyseerrLanguageProfileOption>,
    val tv: List<JellyseerrLanguageProfileOption>,
) {
    companion object {
        val EMPTY = JellyseerrLanguageProfiles(emptyList(), emptyList())
    }
}

data class JellyseerrProfile(
    val id: Int,
    val displayName: String?,
    val permissions: Int,
) {
    fun canManageRequests(): Boolean = permissions.hasPermission(JellyseerrPermission.MANAGE_REQUESTS)
}

enum class JellyseerrMessageKind { INFO, ERROR }

data class JellyseerrMessage(
    val kind: JellyseerrMessageKind,
    val text: String,
)

sealed interface JellyseerrRequestsState {
    data object Loading : JellyseerrRequestsState

    data object MissingServer : JellyseerrRequestsState

    data class Ready(
        val filter: JellyseerrRequestFilter,
        val requests: List<JellyseerrRequestSummary>,
        val counts: JellyseerrRequestCounts?,
        val query: String,
        val searchResults: List<JellyseerrSearchItem>,
        val isSearching: Boolean,
        val isRefreshing: Boolean,
        val isPerformingAction: Boolean,
        val message: JellyseerrMessage?,
        val isAdmin: Boolean,
        val lastUpdated: Instant?,
        val languageProfiles: JellyseerrLanguageProfiles,
    ) : JellyseerrRequestsState

    data class Error(
        val message: String,
    ) : JellyseerrRequestsState
}

sealed interface JellyseerrCreateResult {
    data class Success(
        val request: JellyseerrRequestSummary,
    ) : JellyseerrCreateResult

    data class Duplicate(
        val message: String,
    ) : JellyseerrCreateResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : JellyseerrCreateResult
}

object JellyseerrPermission {
    const val ADMIN: Int = 2
    const val MANAGE_REQUESTS: Int = 16
}

fun Int?.hasPermission(permission: Int): Boolean {
    val value = this ?: return false
    return value and permission == permission || value and JellyseerrPermission.ADMIN == JellyseerrPermission.ADMIN
}
