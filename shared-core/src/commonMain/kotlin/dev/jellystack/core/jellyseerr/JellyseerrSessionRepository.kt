package dev.jellystack.core.jellyseerr

import dev.jellystack.core.security.JellyseerrSessionSecretsDto
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.SecureStoreKeys
import dev.jellystack.core.security.secretValue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class JellyseerrSessionSecrets(
    val baseUrl: String,
    val jellyfinServerId: String,
    val sessionCookie: String?,
)

class JellyseerrSessionRepository(
    private val secureStore: SecureStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun save(
        serverId: String,
        secrets: JellyseerrSessionSecrets,
    ) {
        val payload = json.encodeToString(secrets.toDto())
        secureStore.write(SecureStoreKeys.Jellyseerr.session(serverId), secretValue(payload))
    }

    suspend fun read(serverId: String): JellyseerrSessionSecrets? {
        val payload = secureStore.read(SecureStoreKeys.Jellyseerr.session(serverId))?.reveal() ?: return null
        val dto = runCatching { json.decodeFromString<JellyseerrSessionSecretsDto>(payload) }.getOrNull() ?: return null
        return dto.toDomain()
    }

    suspend fun clear(serverId: String) {
        secureStore.remove(SecureStoreKeys.Jellyseerr.session(serverId))
    }

    private fun JellyseerrSessionSecrets.toDto(): JellyseerrSessionSecretsDto =
        JellyseerrSessionSecretsDto(
            baseUrl = baseUrl,
            jellyfinServerId = jellyfinServerId,
            sessionCookie = sessionCookie,
        )

    private fun JellyseerrSessionSecretsDto.toDomain(): JellyseerrSessionSecrets =
        JellyseerrSessionSecrets(
            baseUrl = baseUrl,
            jellyfinServerId = jellyfinServerId,
            sessionCookie = sessionCookie,
        )
}
