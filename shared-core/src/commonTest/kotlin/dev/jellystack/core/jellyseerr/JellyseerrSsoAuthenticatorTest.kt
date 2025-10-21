package dev.jellystack.core.jellyseerr

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.server.ConnectivityResult
import dev.jellystack.core.server.CredentialInput
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRecord
import dev.jellystack.core.server.ServerRegistration
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerStore
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JellyseerrSsoAuthenticatorTest {
    @Test
    fun authenticatesUsingStoredJellyfinPassword() =
        runTest {
            val storedCredential = jellyfinCredential()
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            val repository = repository(storedCredential, vault)
            val server =
                repository.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.local",
                        credentials = CredentialInput.Jellyfin(username = "demo", password = "pw"),
                    ),
                )
            val authenticator = FakeJellyseerrAuthenticator()
            val coordinator = JellyseerrSsoAuthenticator(repository, authenticator, vault)

            val result =
                coordinator.authenticateWithLinkedJellyfin(
                    jellyseerrUrl = "https://requests.local",
                    jellyfinServerId = server.id,
                    components =
                        JellyfinServerComponents(
                            hostname = "media.local",
                            port = 443,
                            urlBase = "",
                            useSsl = true,
                        ),
                )

            assertEquals("test-key", result.apiKey)
            assertEquals(null, result.sessionCookie)
            assertEquals("pw", authenticator.lastRequest?.password)
            assertEquals("media.local", authenticator.lastRequest?.hostname)
        }

    @Test
    fun throwsWhenLinkedServerMissing() =
        runTest {
            val vault = ServerCredentialVault(FakeSecureStore())
            val repository = repository(jellyfinCredential(), vault)
            val authenticator = FakeJellyseerrAuthenticator()
            val coordinator = JellyseerrSsoAuthenticator(repository, authenticator, vault)

            val error =
                assertFailsWith<JellyseerrAuthenticationException> {
                    coordinator.authenticateWithLinkedJellyfin(
                        jellyseerrUrl = "https://requests.local",
                        jellyfinServerId = "missing",
                        components =
                            JellyfinServerComponents(
                                hostname = "media.local",
                                port = 8096,
                                urlBase = "",
                                useSsl = false,
                            ),
                    )
                }

            assertEquals(JellyseerrAuthenticationException.Reason.SERVER_NOT_FOUND, error.reason)
        }

    @Test
    fun throwsWhenPasswordNotStored() =
        runTest {
            val storedCredential = jellyfinCredential()
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            val repository = repository(storedCredential, vault)
            val server =
                repository.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.local",
                        credentials = CredentialInput.Jellyfin(username = "demo", password = "pw"),
                    ),
                )
            secureStore.remove("servers.${server.id}.jellyfin.password")
            val authenticator = FakeJellyseerrAuthenticator()
            val coordinator = JellyseerrSsoAuthenticator(repository, authenticator, vault)

            val error =
                assertFailsWith<JellyseerrAuthenticationException> {
                    coordinator.authenticateWithLinkedJellyfin(
                        jellyseerrUrl = "https://requests.local",
                        jellyfinServerId = server.id,
                        components =
                            JellyfinServerComponents(
                                hostname = "media.local",
                                port = 443,
                                urlBase = "",
                                useSsl = true,
                            ),
                    )
                }

            assertEquals(JellyseerrAuthenticationException.Reason.MISSING_JELLYFIN_PASSWORD, error.reason)
        }

    @Test
    fun usesManualPasswordWhenProvided() =
        runTest {
            val storedCredential = jellyfinCredential()
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            val repository = repository(storedCredential, vault)
            val server =
                repository.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.local",
                        credentials = CredentialInput.Jellyfin(username = "demo", password = "pw"),
                    ),
                )
            secureStore.remove("servers.${server.id}.jellyfin.password")
            val authenticator = FakeJellyseerrAuthenticator()
            val coordinator = JellyseerrSsoAuthenticator(repository, authenticator, vault)

            val result =
                coordinator.authenticateWithLinkedJellyfin(
                    jellyseerrUrl = "https://requests.local",
                    jellyfinServerId = server.id,
                    components =
                        JellyfinServerComponents(
                            hostname = "media.local",
                            port = 443,
                            urlBase = "",
                            useSsl = true,
                        ),
                    passwordOverride = "manual",
                )

            assertEquals("test-key", result.apiKey)
            assertEquals("manual", authenticator.lastRequest?.password)
            assertEquals("manual", vault.readJellyfinPassword(server.id)?.reveal())
        }

    @Test
    fun capturesSessionCookieWhenApiKeyMissing() =
        runTest {
            val storedCredential = jellyfinCredential()
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            val repository = repository(storedCredential, vault)
            val server =
                repository.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.local",
                        credentials = CredentialInput.Jellyfin(username = "demo", password = "pw"),
                    ),
                )
            val authenticator =
                FakeJellyseerrAuthenticator().apply {
                    nextResult =
                        JellyseerrAuthenticationResult(
                            apiKey = null,
                            userId = 9,
                            sessionCookie = "connect.sid=abc",
                        )
                }
            val coordinator = JellyseerrSsoAuthenticator(repository, authenticator, vault)

            val result =
                coordinator.authenticateWithLinkedJellyfin(
                    jellyseerrUrl = "https://requests.local",
                    jellyfinServerId = server.id,
                    components =
                        JellyfinServerComponents(
                            hostname = "media.local",
                            port = 443,
                            urlBase = "",
                            useSsl = true,
                        ),
                )

            assertEquals(null, result.apiKey)
            assertEquals("connect.sid=abc", result.sessionCookie)
        }

    private fun repository(
        storedCredential: StoredCredential.Jellyfin,
        credentialVault: ServerCredentialVault,
    ): ServerRepository {
        val connectivity =
            dev.jellystack.core.server.ServerConnectivity { registration ->
                when (registration.type) {
                    ServerType.JELLYFIN -> ConnectivityResult.Success("ok", storedCredential)
                    else -> ConnectivityResult.Success("ok", StoredCredential.ApiKey(apiKey = "api"))
                }
            }
        return ServerRepository(
            store = InMemoryServerStore(),
            connectivity = connectivity,
            credentialVault = credentialVault,
            clock = FixedClock,
        )
    }

    private fun jellyfinCredential() =
        StoredCredential.Jellyfin(
            username = "demo",
            deviceId = "device-1",
            accessToken = "token",
            userId = "user42",
        )

    private class FakeJellyseerrAuthenticator : JellyseerrAuthenticator() {
        var lastRequest: JellyseerrAuthRequest? = null
        var nextResult: JellyseerrAuthenticationResult =
            JellyseerrAuthenticationResult(apiKey = "test-key", userId = 7, sessionCookie = null)

        override suspend fun authenticate(request: JellyseerrAuthRequest): JellyseerrAuthenticationResult {
            lastRequest = request
            return nextResult
        }
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
