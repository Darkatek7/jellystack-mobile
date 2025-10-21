package dev.jellystack.network.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class JellyseerrApiTest {
    @Test
    fun refreshesSessionCookieOnUnauthorized() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            var callCount = 0
            val engine =
                MockEngine { request ->
                    captured += request
                    callCount += 1
                    if (callCount == 1) {
                        respond(
                            content = ByteReadChannel.Empty,
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respondSuccessfulProfile()
                    }
                }
            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            var currentCookie = "connect.sid=initial"
            var refreshCount = 0
            val handler =
                object : JellyseerrSessionCookieHandler {
                    override suspend fun currentCookie(): String? = currentCookie

                    override suspend fun refreshCookie(): String? {
                        refreshCount += 1
                        currentCookie = "connect.sid=refreshed"
                        return currentCookie
                    }
                }
            val api =
                JellyseerrApi.create(
                    baseUrl = "https://requests.local",
                    apiKey = null,
                    sessionCookie = currentCookie,
                    sessionHandler = handler,
                    client = client,
                )

            val profile = api.getProfile()

            assertEquals("Test User", profile.displayName)
            assertEquals(2, captured.size)
            assertEquals("connect.sid=initial", captured.first().headers[HttpHeaders.Cookie])
            assertEquals("connect.sid=refreshed", captured.last().headers[HttpHeaders.Cookie])
            assertEquals(1, refreshCount)
        }

    private fun MockRequestHandleScope.respondSuccessfulProfile() =
        respond(
            content =
                ByteReadChannel(
                    """{"id":1,"displayName":"Test User","permissions":1}""",
                ),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
}
