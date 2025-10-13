package dev.jellystack.core.server

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerRepositoryTest {
    @Test
    fun registersJellyfinServerAndPersistsCredentials() = runTest {
        val storedCredential =
            StoredCredential.Jellyfin(
                username = "demo",
                deviceId = "device-1",
                accessToken = "token",
                userId = "user42",
            )
        val repo = repository { ConnectivityResult.Success("ok", storedCredential) }

        val managed =
            repo.register(
                ServerRegistration(
                    type = ServerType.JELLYFIN,
                    name = "Media",
                    baseUrl = "https://media.local/",
                    credentials = CredentialInput.Jellyfin(username = "demo", password = "pw"),
                ),
            )

        assertEquals("https://media.local", managed.baseUrl)
        assertEquals(storedCredential, managed.credentials)
        assertTrue(repo.currentServers().isNotEmpty())
    }

    @Test
    fun duplicateBaseUrlRejected() = runTest {
        val repo = repository { successApiKey() }

        repo.register(
            ServerRegistration(
                type = ServerType.SONARR,
                name = "Shows",
                baseUrl = "https://sonarr.local",
                credentials = CredentialInput.ApiKey("abc"),
            ),
        )

        assertFailsWith<DuplicateServerException> {
            repo.register(
                ServerRegistration(
                    type = ServerType.SONARR,
                    name = "Shows 2",
                    baseUrl = "https://sonarr.local/",
                    credentials = CredentialInput.ApiKey("xyz"),
                ),
            )
        }
    }

    @Test
    fun invalidUrlThrows() = runTest {
        val repo = repository { successApiKey() }

        assertFailsWith<InvalidServerConfiguration> {
            repo.register(
                ServerRegistration(
                    type = ServerType.RADARR,
                    name = "Movies",
                    baseUrl = "ftp://invalid", // unsupported scheme
                    credentials = CredentialInput.ApiKey("key"),
                ),
            )
        }
    }

    @Test
    fun connectivityFailureBubblesUp() = runTest {
        val repo = repository { ConnectivityResult.Failure("nope") }

        assertFailsWith<ConnectivityException> {
            repo.register(
                ServerRegistration(
                    type = ServerType.JELLYSEERR,
                    name = "Requests",
                    baseUrl = "https://requests.local",
                    credentials = CredentialInput.ApiKey("key"),
                ),
            )
        }
    }

    @Test
    fun removeDeletesServer() = runTest {
        val repo = repository { successApiKey() }
        val managed =
            repo.register(
                ServerRegistration(
                    type = ServerType.RADARR,
                    name = "Movies",
                    baseUrl = "https://radarr.local",
                    credentials = CredentialInput.ApiKey("abc"),
                ),
            )

        repo.remove(managed.id)
        assertTrue(repo.currentServers().isEmpty())
    }

    private fun repository(resultProvider: (ServerRegistration) -> ConnectivityResult): ServerRepository {
        val store = InMemoryServerStore()
        val connectivity = ServerConnectivity { registration -> resultProvider(registration) }
        return ServerRepository(store, connectivity, clock = FixedClock)
    }

    private fun successApiKey(): ConnectivityResult =
        ConnectivityResult.Success("ok", StoredCredential.ApiKey("abc"))
}

private object FixedClock : kotlinx.datetime.Clock {
    private val instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    override fun now(): Instant = instant
}

private class InMemoryServerStore : ServerStore {
    private val items = linkedMapOf<String, ServerRecord>()

    override suspend fun list(): List<ServerRecord> = items.values.sortedBy { it.name }

    override suspend fun findByTypeAndUrl(type: ServerType, baseUrl: String): ServerRecord? =
        items.values.firstOrNull { it.type == type && it.baseUrl == baseUrl }

    override suspend fun get(id: String): ServerRecord? = items[id]

    override suspend fun upsert(record: ServerRecord) {
        items[record.id] = record
    }

    override suspend fun delete(id: String) {
        items.remove(id)
    }
}
