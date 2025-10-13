package dev.jellystack.network

import dev.jellystack.network.generated.jellyfin.AuthenticateByNameRequest
import dev.jellystack.network.generated.jellyfin.JellyfinAuthApi
import dev.jellystack.network.generated.jellyseerr.JellyseerrStatusApi
import dev.jellystack.network.generated.radarr.RadarrSystemApi
import dev.jellystack.network.generated.sonarr.SonarrSystemApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedClientsTest {
    @Test
    fun jellyfinAuthSerializesAndParses() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                recorded += request
                respond(
                    content =
                        """
                        {"AccessToken":"token123","User":{"Id":"user42","Name":"Demo"},"ServerId":"srv"}
                        """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))

        val api = JellyfinAuthApi(client, "https://demo.jellyfin.org")
        val response =
            api.authenticateByName(
                AuthenticateByNameRequest(
                    username = "demo",
                    password = "secret",
                    deviceId = "device-1",
                ),
            )

        assertEquals("token123", response.accessToken)
        assertEquals("user42", response.user.id)

        val request = recorded.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/Users/AuthenticateByName", request.url.encodedPath)
        val payload = request.bodyText()
        assertTrue(payload.contains("\"Username\":\"demo\""))
        assertTrue(payload.contains("\"Password\":\"secret\""))

        client.close()
    }

    @Test
    fun sonarrStatusAddsApiKeyHeader() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                recorded += request
                respond(
                    content =
                        """{"appName":"Sonarr","version":"3.0.0"}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
        val api = SonarrSystemApi(client, "https://sonarr.local", "key123")

        val result = api.fetchSystemStatus()
        assertEquals("Sonarr", result.appName)

        val request = recorded.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v3/system/status", request.url.encodedPath)
        assertEquals("key123", request.headers["X-Api-Key"])

        client.close()
    }

    @Test
    fun radarrStatusUsesSameContract() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                recorded += request
                respond(
                    content =
                        """{"appName":"Radarr","version":"5.0.0"}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
        val api = RadarrSystemApi(client, "https://radarr.local", "secret")

        val result = api.fetchSystemStatus()
        assertEquals("Radarr", result.appName)

        val request = recorded.single()
        assertEquals("/api/v3/system/status", request.url.encodedPath)
        assertEquals("secret", request.headers["X-Api-Key"])

        client.close()
    }

    @Test
    fun jellyseerrStatusHitsV1Endpoint() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                recorded += request
                respond(
                    content =
                        """{"version":"1.8.0","commitTag":"abcdef"}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
        val api = JellyseerrStatusApi(client, "https://requests.local", "api-key")

        val result = api.fetchStatus()
        assertEquals("1.8.0", result.version)

        val request = recorded.single()
        assertEquals("/api/v1/status", request.url.encodedPath)
        assertEquals("api-key", request.headers["X-Api-Key"])

        client.close()
    }
}

private fun HttpRequestData.bodyText(): String {
    return when (val content = body) {
        is TextContent -> content.text
        is ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }
}
