package dev.jellystack.core.jellyseerr

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.server.CredentialInput
import dev.jellystack.core.server.ConnectivityResult
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerConnectivity
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRegistration
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerStore
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class JellyseerrSessionAuthenticatorTest {
    @Test
    fun sessionCookiePersistsAcrossRepositories() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            val store = InMemoryServerStore()
            val connectivity = testConnectivity()
            val repository = ServerRepository(store, connectivity, vault, clock = FixedClock)
            val jellyfinServer = registerJellyfin(repository)
            val fakeAuthenticator = FakeJellyseerrAuthenticator()
            fakeAuthenticator.nextResult =
                JellyseerrAuthenticationResult(
                    apiKey = null,
                    userId = 7,
                    sessionCookie = "connect.sid=abc",
                )
            val sso = JellyseerrSsoAuthenticator(repository, fakeAuthenticator, vault)
            val jellyseerrUrl = "https://requests.local"
            val components =
                JellyfinServerComponents(
                    hostname = "media.local",
                    port = 8096,
                    urlBase = "",
                    useSsl = false,
                )
            val authResult =
                sso.authenticateWithLinkedJellyfin(
                    jellyseerrUrl = jellyseerrUrl,
                    jellyfinServerId = jellyfinServer.id,
                    components = components,
                )
            val jellyseerrServer =
                repository.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Requests",
                        baseUrl = jellyseerrUrl,
                        credentials =
                            CredentialInput.ApiKey(
                                apiKey = authResult.apiKey,
                                userId = authResult.userId?.toString(),
                                sessionCookie = authResult.sessionCookie,
                            ),
                    ),
                )
            val sessionAuthenticator = JellyseerrSessionAuthenticator(repository, sso, vault)
            sessionAuthenticator.rememberLink(
                jellyseerrServerId = jellyseerrServer.id,
                metadata = JellyseerrSessionMetadata(jellyfinServer.id, components),
            )

            val reloadedRepository = ServerRepository(store, connectivity, vault, clock = FixedClock)
            val reloadedSso = JellyseerrSsoAuthenticator(reloadedRepository, fakeAuthenticator, vault)
            val reloadedSessionAuthenticator = JellyseerrSessionAuthenticator(reloadedRepository, reloadedSso, vault)
            val reloadedEnvironment =
                reloadedRepository
                    .findServer(jellyseerrServer.id)
                    ?.toEnvironment()
            val handler = reloadedEnvironment?.let { env ->
                reloadedSessionAuthenticator.sessionHandler(env)
            }
            assertNotNull(handler)
            assertEquals("connect.sid=abc", handler.currentCookie())
        }

    @Test
    fun refreshCookieUpdatesStoredCredentials() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            val store = InMemoryServerStore()
            val connectivity = testConnectivity()
            val repository = ServerRepository(store, connectivity, vault, clock = FixedClock)
            val jellyfinServer = registerJellyfin(repository)
            val fakeAuthenticator = FakeJellyseerrAuthenticator()
            val components =
                JellyfinServerComponents(
                    hostname = "media.local",
                    port = 8096,
                    urlBase = "",
                    useSsl = false,
                )
            val sso = JellyseerrSsoAuthenticator(repository, fakeAuthenticator, vault)
            fakeAuthenticator.nextResult =
                JellyseerrAuthenticationResult(
                    apiKey = null,
                    userId = 7,
                    sessionCookie = "connect.sid=initial",
                )
            val authResult =
                sso.authenticateWithLinkedJellyfin(
                    jellyseerrUrl = "https://requests.local",
                    jellyfinServerId = jellyfinServer.id,
                    components = components,
                )
            val jellyseerrServer =
                repository.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Requests",
                        baseUrl = "https://requests.local",
                        credentials =
                            CredentialInput.ApiKey(
                                apiKey = authResult.apiKey,
                                userId = authResult.userId?.toString(),
                                sessionCookie = authResult.sessionCookie,
                            ),
                    ),
                )
            val sessionAuthenticator = JellyseerrSessionAuthenticator(repository, sso, vault)
            sessionAuthenticator.rememberLink(
                jellyseerrServerId = jellyseerrServer.id,
                metadata = JellyseerrSessionMetadata(jellyfinServer.id, components),
            )
            val environment = jellyseerrServer.toEnvironment()
            val handler = sessionAuthenticator.sessionHandler(environment)
            assertNotNull(handler)

            fakeAuthenticator.nextResult =
                JellyseerrAuthenticationResult(
                    apiKey = null,
                    userId = 7,
                    sessionCookie = "connect.sid=renewed",
                )
            val refreshed = handler.refreshCookie()
            assertEquals("connect.sid=renewed", refreshed)
            val updated = repository.findServer(jellyseerrServer.id)
            val storedCredential = updated?.credentials as? StoredCredential.ApiKey
            assertEquals("connect.sid=renewed", storedCredential?.sessionCookie)
        }

    private fun ManagedServer.toEnvironment(): JellyseerrEnvironment {
        val credential = credentials as StoredCredential.ApiKey
        return JellyseerrEnvironment(
            serverId = id,
            serverName = name,
            baseUrl = baseUrl,
            apiKey = credential.apiKey,
            sessionCookie = credential.sessionCookie,
            apiUserId = credential.userId?.toIntOrNull(),
        )
    }

    private fun registerJellyfin(repository: ServerRepository): ManagedServer =
        repository.register(
            ServerRegistration(
                type = ServerType.JELLYFIN,
                name = "Media",
                baseUrl = "https://media.local",
                credentials =
                    CredentialInput.Jellyfin(
                        username = "demo",
                        password = "pw",
                        deviceId = "device-1",
                    ),
            ),
        )

    private fun testConnectivity(): ServerConnectivity =
        ServerConnectivity { registration ->
            when (registration.type) {
                ServerType.JELLYFIN ->
                    ConnectivityResult.Success(
                        message = "ok",
                        credentials =
                            StoredCredential.Jellyfin(
                                username = "demo",
                                deviceId = "device-1",
                                accessToken = "token",
                                userId = "user42",
                            ),
                    )
                ServerType.JELLYSEERR -> {
                    val input = registration.credentials as CredentialInput.ApiKey
                    ConnectivityResult.Success(
                        message = "ok",
                        credentials =
                            StoredCredential.ApiKey(
                                apiKey = input.apiKey,
                                userId = input.userId,
                                sessionCookie = input.sessionCookie,
                            ),
                    )
                }
                else -> ConnectivityResult.Success("ok", StoredCredential.ApiKey(apiKey = "api"))
            }
        }

    private class FakeJellyseerrAuthenticator : JellyseerrAuthenticator() {
        var nextResult: JellyseerrAuthenticationResult =
            JellyseerrAuthenticationResult(apiKey = "key", userId = 7, sessionCookie = "connect.sid=token")

        override suspend fun authenticate(request: JellyseerrAuthRequest): JellyseerrAuthenticationResult = nextResult
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private class InMemoryServerStore : ServerStore {
        private val items = linkedMapOf<String, ServerRecord>()

        override suspend fun list(): List<ServerRecord> = items.values.toList()

        override suspend fun findByTypeAndUrl(
            type: ServerType,
            baseUrl: String,
        ): ServerRecord? = items.values.firstOrNull { it.type == type && it.baseUrl == baseUrl }

        override suspend fun get(id: String): ServerRecord? = items[id]

        override suspend fun upsert(record: ServerRecord) {
            items[record.id] = record
        }

        override suspend fun delete(id: String) {
            items.remove(id)
        }
    }
}
