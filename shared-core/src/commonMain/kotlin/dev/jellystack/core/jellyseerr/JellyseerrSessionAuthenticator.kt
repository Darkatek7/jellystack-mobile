package dev.jellystack.core.jellyseerr

import dev.jellystack.core.server.CredentialInput
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRegistration
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.network.jellyseerr.JellyseerrSessionCookieHandler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class JellyseerrSessionMetadata(
    val jellyfinServerId: String,
    val components: JellyfinServerComponents,
)

class JellyseerrSessionAuthenticator(
    private val serverRepository: ServerRepository,
    private val ssoAuthenticator: JellyseerrSsoAuthenticator,
    private val credentialVault: ServerCredentialVault,
) {
    private val handlers = mutableMapOf<String, SessionHandler>()
    private val mutex = Mutex()

    suspend fun rememberLink(
        jellyseerrServerId: String,
        metadata: JellyseerrSessionMetadata,
    ) {
        credentialVault.saveJellyseerrSessionMetadata(jellyseerrServerId, metadata)
        mutex.withLock {
            handlers.remove(jellyseerrServerId)
        }
    }

    suspend fun clearLink(jellyseerrServerId: String) {
        credentialVault.removeJellyseerrSessionMetadata(jellyseerrServerId)
        mutex.withLock {
            handlers.remove(jellyseerrServerId)
        }
    }

    suspend fun sessionHandler(environment: JellyseerrEnvironment): JellyseerrSessionCookieHandler? {
        val metadata = credentialVault.readJellyseerrSessionMetadata(environment.serverId) ?: return null
        return mutex.withLock {
            val existing = handlers[environment.serverId]
            if (existing != null && existing.baseUrl == environment.baseUrl) {
                return@withLock existing
            }
            val handler =
                SessionHandler(
                    serverId = environment.serverId,
                    baseUrl = environment.baseUrl,
                    metadata = metadata,
                )
            handlers[environment.serverId] = handler
            handler
        }
    }

    private inner class SessionHandler(
        private val serverId: String,
        val baseUrl: String,
        private val metadata: JellyseerrSessionMetadata,
    ) : JellyseerrSessionCookieHandler {
        override suspend fun currentCookie(): String? =
            (serverRepository.findServer(serverId)?.credentials as? StoredCredential.ApiKey)?.sessionCookie

        override suspend fun refreshCookie(): String? {
            val server = serverRepository.findServer(serverId) ?: return null
            val authResult =
                ssoAuthenticator.authenticateWithLinkedJellyfin(
                    jellyseerrUrl = baseUrl,
                    jellyfinServerId = metadata.jellyfinServerId,
                    components = metadata.components,
                )
            serverRepository.register(
                ServerRegistration(
                    id = server.id,
                    type = ServerType.JELLYSEERR,
                    name = server.name,
                    baseUrl = server.baseUrl,
                    credentials =
                        CredentialInput.ApiKey(
                            apiKey = authResult.apiKey,
                            userId = authResult.userId?.toString(),
                            sessionCookie = authResult.sessionCookie,
                        ),
                ),
            )
            return currentCookie()
        }
    }
}
