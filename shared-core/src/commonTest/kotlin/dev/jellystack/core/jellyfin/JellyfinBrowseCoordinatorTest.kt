package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinBrowseCoordinatorTest {
    private val environment =
        JellyfinEnvironment(
            serverKey = "srv-1",
            baseUrl = "https://demo.jellyfin.org",
            accessToken = "token",
            userId = "user-123",
            deviceId = "device-1",
            deviceName = "Test Device",
        )
    private val environmentProvider = JellyfinEnvironmentProvider { environment }
    private var itemPageCallCount = 0
    private val engine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val body =
                when {
                    path.endsWith("/Views") -> LIBRARIES_JSON
                    path.endsWith("/Items/Resume") -> RESUME_JSON
                    path.endsWith("/Items/Latest") ->
                        when (request.url.parameters["includeItemTypes"]) {
                            "Series,Episode" -> LATEST_SHOWS_JSON
                            "Movie" -> LATEST_MOVIES_JSON
                            else -> error("Unexpected includeItemTypes: ${request.url.parameters}")
                        }
                    path.endsWith("/Items") -> {
                        val response =
                            when (itemPageCallCount++) {
                                0 -> ITEMS_PAGE_1_JSON
                                else -> ITEMS_PAGE_2_JSON
                            }
                        response
                    }
                    else -> error("Unexpected request path: $path")
                }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    private val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
    private val apiFactory: JellyfinBrowseApiFactory = { env ->
        JellyfinBrowseApi(
            client = client,
            baseUrl = env.baseUrl,
            accessToken = env.accessToken,
            deviceId = env.deviceId,
            clientName = "Test",
            deviceName = env.deviceName,
            clientVersion = "1.0",
        )
    }

    @Test
    fun bootstrapLoadsLibrariesAndFirstPage() =
        runTest {
            itemPageCallCount = 0
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactory,
                )
            val coordinator = JellyfinBrowseCoordinator(repository, this, pageSize = 2)

            val state = coordinator.state.first { !it.isInitialLoading }
            assertEquals(listOf("lib-1", "lib-2"), state.libraries.map { it.id }, "state=$state")
            assertEquals("lib-1", state.selectedLibraryId, "state=$state")
            assertEquals(2, state.libraryItems.size, "state=$state")
            assertFalse(state.endReached, "state=$state")
            assertEquals("resume-1", state.continueWatching.first().id, "state=$state")
        }

    @Test
    fun loadNextPageAppendsItemsAndSetsEndReached() =
        runTest {
            itemPageCallCount = 0
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactory,
                )
            val coordinator = JellyfinBrowseCoordinator(repository, this, pageSize = 2)

            coordinator.state.first { !it.isInitialLoading }

            coordinator.loadNextPage()

            val state = coordinator.state.first { it.currentPage == 1 && !it.isPageLoading }
            assertEquals(3, state.libraryItems.size, "state=$state")
            assertTrue(state.endReached, "state=$state")
            assertEquals("item-3", state.libraryItems.last().id, "state=$state")
        }

    private class InMemoryLibraryStore : JellyfinLibraryStore {
        private val records = mutableListOf<JellyfinLibraryRecord>()

        override suspend fun replaceAll(
            serverId: String,
            libraries: List<JellyfinLibraryRecord>,
        ) {
            records.removeAll { it.serverId == serverId }
            records.addAll(libraries)
        }

        override suspend fun list(serverId: String): List<JellyfinLibraryRecord> = records.filter { it.serverId == serverId }
    }

    private class InMemoryItemStore : JellyfinItemStore {
        private val records = mutableMapOf<String, MutableMap<String, JellyfinItemRecord>>()

        override suspend fun replaceForLibrary(
            serverId: String,
            libraryId: String,
            items: List<JellyfinItemRecord>,
        ) {
            val serverRecords = records.getOrPut(serverId) { mutableMapOf() }
            serverRecords.entries.removeIf { it.value.libraryId == libraryId }
            items.forEach { serverRecords[it.id] = it }
        }

        override suspend fun upsert(items: List<JellyfinItemRecord>) {
            items.forEach { item ->
                val serverRecords = records.getOrPut(item.serverId) { mutableMapOf() }
                serverRecords[item.id] = item
            }
        }

        override suspend fun listByLibrary(
            serverId: String,
            libraryId: String,
            limit: Long,
            offset: Long,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { it.libraryId == libraryId }
                ?.sortedBy { it.sortName ?: it.name }
                ?.drop(offset.toInt())
                ?.take(limit.toInt())
                ?: emptyList()

        override suspend fun listRecentShows(
            serverId: String,
            libraryId: String?,
            limit: Long,
        ): List<JellyfinItemRecord> = emptyList()

        override suspend fun listRecentMovies(
            serverId: String,
            libraryId: String?,
            limit: Long,
        ): List<JellyfinItemRecord> = emptyList()

        override suspend fun listContinueWatching(
            serverId: String,
            limit: Long,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { (it.positionTicks ?: 0L) > 0L }
                ?.sortedByDescending { it.updatedAt }
                ?.take(limit.toInt())
                ?: emptyList()

        override suspend fun clearContinueWatching(
            serverId: String,
            keepIds: Set<String>,
        ) {
            val serverRecords = records[serverId] ?: return
            if (keepIds.isEmpty()) {
                serverRecords.keys.forEach { id ->
                    val record = serverRecords[id] ?: return@forEach
                    if ((record.positionTicks ?: 0L) > 0L) {
                        serverRecords[id] =
                            record.copy(positionTicks = null, playedPercentage = null, lastPlayed = null)
                    }
                }
            } else {
                serverRecords.keys.forEach { id ->
                    if (id in keepIds) return@forEach
                    val record = serverRecords[id] ?: return@forEach
                    if ((record.positionTicks ?: 0L) > 0L) {
                        serverRecords[id] =
                            record.copy(positionTicks = null, playedPercentage = null, lastPlayed = null)
                    }
                }
            }
        }

        override suspend fun listEpisodesForSeries(
            serverId: String,
            seriesId: String,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { it.seriesId == seriesId }
                ?.sortedBy { it.indexNumber ?: Long.MAX_VALUE }
                ?: emptyList()

        override suspend fun listEpisodesForSeason(
            serverId: String,
            seasonId: String,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { it.seasonId == seasonId }
                ?.sortedBy { it.indexNumber ?: Long.MAX_VALUE }
                ?: emptyList()

        override suspend fun get(itemId: String): JellyfinItemRecord? = records.values.firstNotNullOfOrNull { it[itemId] }
    }

    private class InMemoryDetailStore : JellyfinItemDetailStore {
        private val records = mutableMapOf<String, JellyfinItemDetailRecord>()

        override suspend fun get(itemId: String): JellyfinItemDetailRecord? = records[itemId]

        override suspend fun upsert(record: JellyfinItemDetailRecord) {
            records[record.itemId] = record
        }
    }

    companion object {
        private const val LIBRARIES_JSON = """
            {
              "Items": [
                {"Id": "lib-1", "Name": "Movies", "CollectionType": "movies"},
                {"Id": "lib-2", "Name": "Shows", "CollectionType": "tvshows"}
              ]
            }
        """

        private const val RESUME_JSON = """
            {
              "Items": [
                {
                  "Id": "resume-1",
                  "Name": "Resume Item",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "RunTimeTicks": 36000000000,
                  "UserData": {"PlaybackPositionTicks": 18000000000},
                  "ImageTags": {"Primary": "resume-tag"}
                }
              ]
            }
        """

        private const val ITEMS_PAGE_1_JSON = """
            {
              "Items": [
                {
                  "Id": "item-1",
                  "Name": "Alpha Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "A sample overview",
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-primary"}
                },
                {
                  "Id": "item-2",
                  "Name": "Beta Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "Another overview",
                  "RunTimeTicks": 30000000000,
                  "ImageTags": {"Primary": "tag-secondary"}
                }
              ]
            }
        """

        private const val ITEMS_PAGE_2_JSON = """
            {
              "Items": [
                {
                  "Id": "item-3",
                  "Name": "Gamma Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "Final overview",
                  "RunTimeTicks": 42000000000,
                  "ImageTags": {"Primary": "tag-third"}
                }
              ]
            }
        """

        private const val LATEST_SHOWS_JSON = "[]"

        private const val LATEST_MOVIES_JSON = "[]"
    }
}
