package dev.jellystack.core.jellyfin

import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential

class ServerRepositoryEnvironmentProvider(
    private val repository: ServerRepository,
    private val deviceNameProvider: () -> String = { "Jellystack" },
) : JellyfinEnvironmentProvider {
    override suspend fun current(): JellyfinEnvironment? {
        val server =
            repository
                .currentServers()
                .firstOrNull { it.type == ServerType.JELLYFIN } ?: return null
        val credential = server.credentials as? StoredCredential.Jellyfin ?: return null
        return JellyfinEnvironment(
            serverKey = server.id,
            baseUrl = server.baseUrl,
            accessToken = credential.accessToken,
            userId = credential.userId,
            deviceId = credential.deviceId ?: credential.username,
            deviceName = deviceNameProvider(),
        )
    }
}
