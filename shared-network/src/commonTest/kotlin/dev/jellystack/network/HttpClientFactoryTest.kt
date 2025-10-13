package dev.jellystack.network

import kotlin.test.Test
import kotlin.test.assertNotNull

class HttpClientFactoryTest {
    @Test
    fun buildsClient() {
        val client = buildHttpClient()
        assertNotNull(client)
        client.close()
    }
}
