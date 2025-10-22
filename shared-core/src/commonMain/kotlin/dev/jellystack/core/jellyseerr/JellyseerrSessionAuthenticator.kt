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
    private val sessionRepository: JellyseerrSessionRepository,
) {
    private val handlers = mutableMapOf<String, SessionHandler>()
    private val mutex = Mutex()

    suspend fun rememberLink(
        jellyseerrServerId: String,
        metadata: JellyseerrSessionMetadata,
    ) {
        credentialVault.saveJellyseerrSessionMetadata(jellyseerrServerId, metadata)
        persistSessionSnapshot(jellyseerrServerId, metadata)
        mutex.withLock {
            handlers.remove(jellyseerrServerId)
        }
    }

    suspend fun relink(
        jellyseerrServerId: String,
        passwordOverride: String? = null,
    ): JellyseerrAuthenticationResult {
        val metadata =
            credentialVault.readJellyseerrSessionMetadata(jellyseerrServerId)
                ?: throw JellyseerrAuthenticationException(
                    message = "Requests server is not linked to Jellyfin.",
                    reason = JellyseerrAuthenticationException.Reason.INVALID_LINKED_SERVER,
                )
        val server =
            serverRepository.findServer(jellyseerrServerId)
                ?: throw JellyseerrAuthenticationException(
                    message = "Requests server could not be found.",
                    reason = JellyseerrAuthenticationException.Reason.SERVER_NOT_FOUND,
                )
        val authResult =
            ssoAuthenticator.authenticateWithLinkedJellyfin(
                jellyseerrUrl = server.baseUrl,
                jellyfinServerId = metadata.jellyfinServerId,
                components = metadata.components,
                passwordOverride = passwordOverride,
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
        persistSessionSnapshot(server.id, metadata)
        mutex.withLock {
            handlers.remove(server.id)
        }
        return authResult
    }

    suspend fun clearLink(jellyseerrServerId: String) {
        credentialVault.removeJellyseerrSessionMetadata(jellyseerrServerId)
        sessionRepository.clear(jellyseerrServerId)
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
            persistSessionSnapshot(serverId, metadata)
            return currentCookie()
        }
    }

    private suspend fun persistSessionSnapshot(
        jellyseerrServerId: String,
        metadata: JellyseerrSessionMetadata,
    ) {
        val server = serverRepository.findServer(jellyseerrServerId) ?: return
        val credential = server.credentials as? StoredCredential.ApiKey ?: return
        sessionRepository.save(
            jellyseerrServerId,
            JellyseerrSessionSecrets(
                baseUrl = server.baseUrl,
                jellyfinServerId = metadata.jellyfinServerId,
                sessionCookie = credential.sessionCookie,
            ),
        )
    }
}
