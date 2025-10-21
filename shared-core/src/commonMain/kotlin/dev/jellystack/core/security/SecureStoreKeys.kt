package dev.jellystack.core.security

import kotlinx.serialization.Serializable

object SecureStoreKeys {
    object Jellyseerr {
        private const val SESSION_PREFIX = "security.jellyseerr.session"

        fun session(serverId: String): String = "$SESSION_PREFIX.$serverId"
    }
}

@Serializable
data class JellyseerrSessionSecretsDto(
    val baseUrl: String,
    val jellyfinServerId: String,
    val sessionCookie: String?,
)
