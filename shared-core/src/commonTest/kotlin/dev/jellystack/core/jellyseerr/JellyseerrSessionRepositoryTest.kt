package dev.jellystack.core.jellyseerr

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.security.SecureStoreKeys
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyseerrSessionRepositoryTest {
    private val secureStore = FakeSecureStore()
    private val repository = JellyseerrSessionRepository(secureStore)

    @Test
    fun saveAndLoadSession() =
        runTest {
            val secrets =
                JellyseerrSessionSecrets(
                    baseUrl = "https://requests.local",
                    jellyfinServerId = "server-1",
                    sessionCookie = "connect.sid=abc",
                )
            repository.save("jellyseerr-1", secrets)

            val restored = repository.read("jellyseerr-1")
            assertEquals(secrets, restored)
        }

    @Test
    fun clearingSessionRemovesEntry() =
        runTest {
            repository.save(
                serverId = "jellyseerr-2",
                secrets =
                    JellyseerrSessionSecrets(
                        baseUrl = "https://example",
                        jellyfinServerId = "server-2",
                        sessionCookie = null,
                    ),
            )

            repository.clear("jellyseerr-2")
            assertNull(repository.read("jellyseerr-2"))
            assertNull(secureStore.peek(SecureStoreKeys.Jellyseerr.session("jellyseerr-2")))
        }
}
