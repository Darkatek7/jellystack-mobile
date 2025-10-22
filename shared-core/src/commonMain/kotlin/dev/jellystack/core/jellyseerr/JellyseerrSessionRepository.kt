package dev.jellystack.core.jellyseerr

import dev.jellystack.core.security.JellyseerrSessionSecretsDto
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.SecureStoreKeys
import dev.jellystack.core.security.secretValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val sessions = MutableStateFlow<Map<String, JellyseerrSessionSecrets?>>(emptyMap())

    fun observeSessions(): StateFlow<Map<String, JellyseerrSessionSecrets?>> = sessions.asStateFlow()

    fun observeSession(serverId: String): Flow<JellyseerrSessionSecrets?> = sessions.map { it[serverId] }.distinctUntilChanged()

    suspend fun save(
        serverId: String,
        secrets: JellyseerrSessionSecrets,
    ) {
        val payload = json.encodeToString(secrets.toDto())
        secureStore.write(SecureStoreKeys.Jellyseerr.session(serverId), secretValue(payload))
        sessions.value = sessions.value + (serverId to secrets)
    }

    suspend fun read(serverId: String): JellyseerrSessionSecrets? {
        val payload =
            secureStore.read(SecureStoreKeys.Jellyseerr.session(serverId))?.reveal()
                ?: run {
                    sessions.value = sessions.value - serverId
                    return null
                }
        val dto = runCatching { json.decodeFromString<JellyseerrSessionSecretsDto>(payload) }.getOrNull()
        val secrets = dto?.toDomain()
        sessions.value =
            if (secrets != null) {
                sessions.value + (serverId to secrets)
            } else {
                sessions.value - serverId
            }
        return secrets
    }

    suspend fun clear(serverId: String) {
        secureStore.remove(SecureStoreKeys.Jellyseerr.session(serverId))
        sessions.value = sessions.value - serverId
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
