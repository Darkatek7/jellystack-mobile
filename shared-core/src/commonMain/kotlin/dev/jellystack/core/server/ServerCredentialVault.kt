package dev.jellystack.core.server

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue
import dev.jellystack.core.jellyseerr.JellyseerrSessionMetadata
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ServerCredentialVault(
    private val secureStore: SecureStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun saveJellyfinPassword(
        serverId: String,
        password: String,
    ) {
        val primaryKey = jellyfinPasswordKey(serverId)
        secureStore.write(primaryKey, secretValue(password))
        legacyJellyfinPasswordKeys(serverId).forEach { key -> secureStore.remove(key) }
    }

    suspend fun readJellyfinPassword(serverId: String): SecretValue? {
        val primaryKey = jellyfinPasswordKey(serverId)
        secureStore.read(primaryKey)?.let { return it }

        legacyJellyfinPasswordKeys(serverId).forEach { legacyKey ->
            val legacySecret = secureStore.read(legacyKey)
            if (legacySecret != null) {
                saveJellyfinPassword(serverId, legacySecret.reveal())
                return legacySecret
            }
        }

        return null
    }

    suspend fun removeJellyfinPassword(serverId: String) {
        val primaryKey = jellyfinPasswordKey(serverId)
        secureStore.remove(primaryKey)
        legacyJellyfinPasswordKeys(serverId).forEach { key -> secureStore.remove(key) }
    }

    suspend fun saveJellyseerrSessionMetadata(
        serverId: String,
        metadata: JellyseerrSessionMetadata,
    ) {
        val key = jellyseerrMetadataKey(serverId)
        val payload = json.encodeToString(metadata)
        secureStore.write(key, secretValue(payload))
    }

    suspend fun readJellyseerrSessionMetadata(serverId: String): JellyseerrSessionMetadata? {
        val key = jellyseerrMetadataKey(serverId)
        val payload = secureStore.read(key)?.reveal() ?: return null
        return runCatching { json.decodeFromString<JellyseerrSessionMetadata>(payload) }.getOrNull()
    }

    suspend fun removeJellyseerrSessionMetadata(serverId: String) {
        val key = jellyseerrMetadataKey(serverId)
        secureStore.remove(key)
    }

    private fun jellyfinPasswordKey(serverId: String): String = "servers.$serverId.jellyfin.password"

    private fun legacyJellyfinPasswordKeys(serverId: String): List<String> =
        listOf(
            "servers.$serverId.password",
            "servers.$serverId.jellyfinPassword",
        )

    private fun jellyseerrMetadataKey(serverId: String): String = "servers.$serverId.jellyseerr.link"
}
