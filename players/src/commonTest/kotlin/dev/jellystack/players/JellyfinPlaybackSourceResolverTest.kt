package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JellyfinPlaybackSourceResolverTest {
    private val resolver = JellyfinPlaybackSourceResolver()

    @Test
    fun resolvesMatroskaDirectStreamWithMatroskaMimeType() {
        val request =
            PlaybackRequest(
                mediaId = "item-123",
                mediaSources = emptyList(),
            )
        val selection =
            PlaybackStreamSelection(
                sourceId = "source-1",
                mode = PlaybackMode.DIRECT,
                container = "mkv",
                videoCodec = "av1",
                audioCodec = "aac",
                videoBitrate = null,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
            )
        val environment =
            JellyfinEnvironment(
                serverKey = "server",
                baseUrl = "https://demo.jellyfin.org",
                accessToken = "token",
                userId = "user",
                deviceId = "device-id",
                deviceName = "TestDevice",
            )

        val source = resolver.resolve(request, selection, environment, startPositionMs = 0)

        assertTrue(source.url.contains("stream.mkv"))
        assertEquals("video/x-matroska", source.mimeType)
    }
}
