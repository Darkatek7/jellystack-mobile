package dev.jellystack.core.jellyseerr

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.serialization.Serializable

@Serializable
data class JellyfinServerComponents(
    val hostname: String,
    val port: Int,
    val urlBase: String,
    val useSsl: Boolean,
)

class JellyseerrSsoAuthenticator(
    private val serverRepository: ServerRepository,
    private val authenticator: JellyseerrAuthenticator,
    private val credentialVault: ServerCredentialVault? = null,
) {
    suspend fun authenticateWithLinkedJellyfin(
        jellyseerrUrl: String,
        jellyfinServerId: String,
        components: JellyfinServerComponents,
        passwordOverride: String? = null,
    ): JellyseerrAuthenticationResult {
        val server =
            serverRepository.findServer(jellyfinServerId)
                ?: throw JellyseerrAuthenticationException(
                    message = "Linked Jellyfin server could not be found.",
                    reason = JellyseerrAuthenticationException.Reason.SERVER_NOT_FOUND,
                )
        val credential =
            server.credentials as? StoredCredential.Jellyfin
                ?: throw JellyseerrAuthenticationException(
                    message = "Linked server is not a Jellyfin configuration.",
                    reason = JellyseerrAuthenticationException.Reason.INVALID_LINKED_SERVER,
                )
        if (server.type != ServerType.JELLYFIN) {
            throw JellyseerrAuthenticationException(
                message = "Linked server is not a Jellyfin configuration.",
                reason = JellyseerrAuthenticationException.Reason.INVALID_LINKED_SERVER,
            )
        }

        val override = passwordOverride?.takeIf { it.isNotBlank() }
        val password =
            override
                ?: readPassword(jellyfinServerId)?.reveal()
                ?: throw JellyseerrAuthenticationException(
                    message = "Stored Jellyfin password could not be retrieved.",
                    reason = JellyseerrAuthenticationException.Reason.MISSING_JELLYFIN_PASSWORD,
                )

        val result =
            authenticator.authenticate(
                JellyseerrAuthRequest(
                    baseUrl = jellyseerrUrl,
                    username = credential.username,
                    password = password,
                    hostname = components.hostname,
                    port = components.port,
                    urlBase = components.urlBase.takeIf { it.isNotBlank() },
                    useSsl = components.useSsl,
                    serverType = 2,
                ),
            )
        if (override != null) {
            credentialVault?.saveJellyfinPassword(jellyfinServerId, override)
        }
        return result
    }

    private suspend fun readPassword(serverId: String): SecretValue? =
        credentialVault?.readJellyfinPassword(serverId)
            ?: serverRepository.jellyfinPassword(serverId)
}
