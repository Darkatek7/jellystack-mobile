package dev.jellystack.core.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyseerrRequestsCoordinatorTest {
    private val environment =
        JellyseerrEnvironment(
            serverId = "srv-1",
            serverName = "Requests",
            baseUrl = "https://requests.test",
            apiKey = "secret",
        )

    @Test
    fun emitsReadyStateWhenServerPresent() =
        runTest {
            val client = mockClient()
            val repository = JellyseerrRepository(client)
            val provider = FakeEnvironmentProvider()
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = repository,
                    environmentProvider = provider,
                    scope = this,
                    pollIntervalMillis = 60_000,
                    enablePolling = false,
                    clock = FixedClock,
                )

            provider.update(environment)
            val ready = coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first()
            assertEquals(1, ready.requests.size)
            assertEquals(
                "Admin",
                ready.requests
                    .first()
                    .requestedBy
                    ?.displayName,
            )

            coordinator.shutdown()
        }

    private fun mockClient(): HttpClient =
        HttpClient(
            MockEngine {
                defaultResponses(it)
            },
        ) {
            install(ContentNegotiation) {
                json(NetworkJson.default)
            }
        }

    private suspend fun MockRequestHandleScope.defaultResponses(request: HttpRequestData) =
        when {
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/auth/me" ->
                respondJson("""{"id":1,"displayName":"Admin","permissions":18}""")
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request" ->
                respondJson(
                    """
                    {
                      "pageInfo":{"pages":1,"pageSize":20,"results":1,"page":1},
                      "results":[
                        {
                          "id":201,
                          "status":1,
                          "type":"movie",
                          "mediaId":90,
                          "createdAt":"2024-10-01T10:00:00.000Z",
                          "updatedAt":"2024-10-01T10:00:00.000Z",
                          "is4k":false,
                          "requestedBy":{"id":1,"displayName":"Admin","username":"admin","permissions":18},
                          "media":{"id":90,"tmdbId":777,"mediaType":"movie","status":2,"status4k":1,"title":"New Movie"},
                          "seasons":[]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request/count" ->
                respondJson(
                    """{"total":1,"movie":1,"pending":1,"approved":0,"processing":0,"available":0,"completed":0,"declined":0,"tv":0}""",
                )
            else -> respondJson("{}", HttpStatusCode.NotFound)
        }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers =
            headersOf(
                HttpHeaders.ContentType,
                "application/json",
            ),
    )

    private class FakeEnvironmentProvider(
        initial: JellyseerrEnvironment? = null,
    ) : JellyseerrEnvironmentProvider {
        private val state = MutableStateFlow(initial)

        override suspend fun current(): JellyseerrEnvironment? = state.value

        override fun observe(): Flow<JellyseerrEnvironment?> = state

        fun update(environment: JellyseerrEnvironment?) {
            state.value = environment
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }
}
