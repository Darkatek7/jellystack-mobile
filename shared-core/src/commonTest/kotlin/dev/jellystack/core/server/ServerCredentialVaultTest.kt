package dev.jellystack.core.server

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerCredentialVaultTest {
    @Test
    fun saveWritesPrimaryKeyAndClearsLegacyEntries() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            secureStore.write("servers.demo.password", secretValue("legacy"))

            vault.saveJellyfinPassword("demo", "new-secret")

            assertEquals(
                "new-secret",
                secureStore.peek("servers.demo.jellyfin.password")?.reveal(),
            )
            assertNull(secureStore.peek("servers.demo.password"))
            assertNull(secureStore.peek("servers.demo.jellyfinPassword"))
        }

    @Test
    fun readReturnsPrimarySecretWhenPresent() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            secureStore.write("servers.demo.jellyfin.password", secretValue("stored"))

            val secret = vault.readJellyfinPassword("demo")

            assertEquals("stored", secret?.reveal())
        }

    @Test
    fun readMigratesLegacySecretToPrimaryKey() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            secureStore.write("servers.demo.jellyfinPassword", secretValue("legacy"))

            val secret = vault.readJellyfinPassword("demo")

            assertEquals("legacy", secret?.reveal())
            assertEquals(
                "legacy",
                secureStore.peek("servers.demo.jellyfin.password")?.reveal(),
            )
            assertNull(secureStore.peek("servers.demo.jellyfinPassword"))
        }
}

private class FakeSecureStore : SecureStore {
    private val items = mutableMapOf<String, SecretValue>()

    override suspend fun write(
        key: String,
        value: SecretValue,
    ) {
        items[key] = value
    }

    override suspend fun read(key: String): SecretValue? = items[key]

    override suspend fun remove(key: String) {
        items.remove(key)
    }

    suspend fun peek(key: String): SecretValue? = items[key]
}
