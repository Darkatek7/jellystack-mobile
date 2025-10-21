package dev.jellystack.core.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JellyseerrRepositoryTest {
    private val environment =
        JellyseerrEnvironment(
            serverId = "srv-1",
            serverName = "Requests",
            baseUrl = "https://requests.test",
            apiKey = "secret",
            sessionCookie = null,
        )

    @Test
    fun duplicateRequestsReturnFriendlyResult() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/request") {
                            respondJson(
                                status = HttpStatusCode.Conflict,
                                body = """{"status":409,"message":"This title has already been requested."}""",
                            )
                        } else {
                            error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val result =
                repository.createRequest(
                    environment,
                    JellyseerrCreateRequest(
                        mediaId = 100,
                        tvdbId = null,
                        mediaType = JellyseerrMediaType.MOVIE,
                    ),
                )

            assertIs<JellyseerrCreateResult.Duplicate>(result)
            assertTrue(result.message.contains("already", ignoreCase = true))
        }

    @Test
    fun fetchRequestsMapsStatuses() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request" ->
                                respondJson(
                                    body =
                                        """
                                        {
                                          "pageInfo":{"pages":1,"pageSize":20,"results":1,"page":1},
                                          "results":[
                                            {
                                              "id":101,
                                              "status":2,
                                              "type":"movie",
                                              "mediaId":77,
                                              "createdAt":"2024-09-01T10:00:00.000Z",
                                              "updatedAt":"2024-09-01T12:00:00.000Z",
                                              "is4k":false,
                                              "canRemove":true,
                                              "profileName":"HD-1080p",
                                              "requestedBy":{"id":4,"displayName":"Alice","username":"alice","permissions":18},
                                              "media":{
                                                "id":77,
                                                "tmdbId":555,
                                                "mediaType":"movie",
                                                "status":5,
                                                "status4k":2,
                                                "title":"Dune Part Two",
                                                "requests":[]
                                              },
                                              "seasons":[]
                                            }
                                          ]
                                        }
                                        """.trimIndent(),
                                )
                            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request/count" ->
                                respondJson(
                                    """{"total":1,"movie":1,"pending":0,"approved":1,"processing":0,"available":1,"completed":0,"declined":0,"tv":0}""",
                                )
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val page = repository.fetchRequests(environment, JellyseerrRequestFilter.ALL)

            assertEquals(1, page.results.size)
            val request = page.results.first()
            assertEquals(JellyseerrRequestStatus.APPROVED, request.requestStatus)
            assertEquals(JellyseerrMediaStatus.AVAILABLE, request.availability.standard)
            assertEquals("Alice", request.requestedBy?.displayName)
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
}
