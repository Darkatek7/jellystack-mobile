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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JellyfinBrowseRepositoryTest {
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
    private val libraryStore = InMemoryLibraryStore()
    private val itemStore = InMemoryItemStore()
    private val detailStore = InMemoryDetailStore()
    private val engine =
        MockEngine { request ->
            val body =
                when (val path = request.url.encodedPath) {
                    "/Users/user-123/Views" -> LIBRARIES_JSON
                    "/Users/user-123/Items/Resume" -> RESUME_JSON
                    "/Users/user-123/Items/item-1" -> DETAIL_JSON
                    "/Users/user-123/Items" -> ITEMS_JSON
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
            client,
            env.baseUrl,
            env.accessToken,
            env.deviceId,
            clientName = "Test",
            deviceName = env.deviceName,
            clientVersion = "1.0",
        )
    }
    private val repository =
        JellyfinBrowseRepository(environmentProvider, libraryStore, itemStore, detailStore, apiFactory, clock = FixedClock)

    @Test
    fun refreshLibrariesStoresRecords() =
        runTest {
            val libraries = repository.refreshLibraries()

            assertEquals(2, libraries.size)
            assertEquals("Movies", libraries.first().name)
            val stored = libraryStore.list(environment.serverKey)
            assertEquals(libraries.size, stored.size)
        }

    @Test
    fun loadLibraryPageCachesAndReturnsItems() =
        runTest {
            repository.refreshLibraries()

            val items = repository.loadLibraryPage(libraryId = "lib-1", page = 0, pageSize = 2, refresh = true)

            assertEquals(2, items.size)
            val stored = itemStore.listByLibrary(environment.serverKey, "lib-1", limit = 10, offset = 0)
            assertEquals(2, stored.size)
        }

    @Test
    fun getItemDetailCachesMediaSources() =
        runTest {
            repository.refreshLibraries()
            repository.loadLibraryPage(libraryId = "lib-1", page = 0, pageSize = 2, refresh = true)

            val detail = repository.getItemDetail("item-1")

            assertNotNull(detail)
            assertEquals("Sample Movie", detail.name)
            assertEquals(1, detail.mediaSources.size)
            assertNotNull(detailStore.get("item-1"))
        }

    private object FixedClock : Clock {
        private val instant = Instant.parse("2024-01-01T00:00:00Z")

        override fun now(): Instant = instant
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

        private const val ITEMS_JSON = """
            {
              "Items": [
                {
                  "Id": "item-1",
                  "Name": "Sample Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "A sample overview",
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-primary"},
                  "UserData": {
                    "PlaybackPositionTicks": 12000000000,
                    "PlayedPercentage": 33.3
                  }
                },
                {
                  "Id": "item-2",
                  "Name": "Sample Episode",
                  "Type": "Episode",
                  "MediaType": "Video",
                  "Overview": "Episode overview",
                  "RunTimeTicks": 18000000000,
                  "SeriesName": "Sample Series",
                  "EpisodeTitle": "Pilot",
                  "ImageTags": {"Primary": "tag-episode"}
                }
              ],
              "TotalRecordCount": 2
            }
        """

        private const val RESUME_JSON = ITEMS_JSON

        private const val DETAIL_JSON = """
            {
              "Id": "item-1",
              "Name": "Sample Movie",
              "Overview": "Detailed overview",
              "Taglines": ["An epic journey"],
              "RunTimeTicks": 36000000000,
              "Genres": ["Adventure"],
              "Studios": [{"Name": "Sample Studio"}],
              "MediaSources": [
                {
                  "Id": "source-1",
                  "Name": "Main",
                  "RunTimeTicks": 36000000000,
                  "Container": "mp4",
                  "SupportsDirectPlay": true,
                  "SupportsTranscoding": true,
                  "MediaStreams": [
                    {"Type": "Video", "Index": 0, "DisplayTitle": "1080p", "Codec": "h264"},
                    {"Type": "Audio", "Index": 1, "DisplayTitle": "English", "Codec": "aac"}
                  ]
                }
              ],
              "ImageTags": {"Primary": "tag-primary"}
            }
        """
    }
}
