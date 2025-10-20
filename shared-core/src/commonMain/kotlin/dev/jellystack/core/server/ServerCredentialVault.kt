package dev.jellystack.core.server

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue

class ServerCredentialVault(
    private val secureStore: SecureStore,
) {
    suspend fun saveJellyfinPassword(
        serverId: String,
        password: String,
    ) {
        secureStore.write(jellyfinPasswordKey(serverId), secretValue(password))
    }

    suspend fun readJellyfinPassword(serverId: String): SecretValue? = secureStore.read(jellyfinPasswordKey(serverId))

    suspend fun removeJellyfinPassword(serverId: String) {
        secureStore.remove(jellyfinPasswordKey(serverId))
    }

    private fun jellyfinPasswordKey(serverId: String): String = "servers.$serverId.jellyfin.password"
}
