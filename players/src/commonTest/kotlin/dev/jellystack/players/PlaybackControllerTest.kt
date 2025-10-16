package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    @Test
    fun selectsDirectH264StreamForPlayback() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Playing
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals("h264", state.stream.videoCodec?.lowercase())
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
                // controller.release() cancels controllerScope internally
            }
        }

    @Test
    fun selectsDirectHevcStreamForPlayback() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true, directCodec = "hevc"))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Playing
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals("hevc", state.stream.videoCodec?.lowercase())
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
            }
        }

    @Test
    fun selectsDirectAv1StreamForPlayback() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true, directCodec = "av1"))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Playing
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals("av1", state.stream.videoCodec?.lowercase())
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
            }
        }

    @Test
    fun selectsDirectStreamWhenDirectPlayFlagMissing() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request =
                PlaybackRequest.from(
                    sampleItem(),
                    sampleDetail(
                        withDirect = true,
                        directSupportsDirectPlay = false,
                        directSupportsDirectStream = true,
                    ),
                )

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Playing
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
            }
        }

    @Test
    fun exposesSubtitleTracksAndAllowsSelection() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val detail = sampleDetail(includeSrt = true, includeVtt = true, includePgs = true)
            val request = PlaybackRequest.from(sampleItem(), detail)

            try {
                controller.play(request, testEnvironment())
                val state = controller.state.value as PlaybackState.Playing

                assertEquals(2, state.stream.subtitleTracks.size)
                val srt = state.stream.subtitleTracks.first { it.format == SubtitleFormat.SRT }
                controller.selectSubtitle(srt.id)
                val updated = controller.state.value as PlaybackState.Playing
                assertEquals(srt.id, updated.subtitleTrack?.id)
            } finally {
                controller.release()
            }
        }

    @Test
    fun persistsProgressAndRestoresOnNextSession() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            val request = PlaybackRequest.from(sampleItem(), sampleDetail())
            assertEquals(90_000L, ticksToMillis(request.durationTicks))

            val engineOne = NoopPlayerEngine()
            val firstScope = TestScope(UnconfinedTestDispatcher())
            val first =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engineOne,
                    scope = firstScope,
                )
            try {
                first.play(request, testEnvironment())
                first.updateProgress(45_000L)
                val currentSession = first.currentSession()
                assertNotNull(currentSession)
                assertEquals(45_000L, currentSession.positionMs)
                assertEquals(90_000L, currentSession.durationMs)
                val afterUpdate = store.read(request.mediaId)
                assertNotNull(afterUpdate)
                assertEquals(45_000L, afterUpdate.positionMs)
                first.stop()
                val afterFirstStop = store.read(request.mediaId)
                assertNotNull(afterFirstStop)
                assertEquals(45_000L, afterFirstStop.positionMs)
            } finally {
                first.release()
            }

            val secondScope = TestScope(UnconfinedTestDispatcher())
            val second =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    scope = secondScope,
                )
            try {
                second.play(request, testEnvironment())
                val restored = second.state.value as PlaybackState.Playing
                assertEquals(45_000L, restored.positionMs)

                second.updateProgress(100_000L)
                second.stop()
            } finally {
                second.release()
            }

            val cleared = store.read(request.mediaId)
            assertNull(cleared)
        }

    @Test
    fun clearsProgressWhenCompleted() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            val detail = sampleDetail(durationMs = 120_000L)
            val request = PlaybackRequest.from(sampleItem(), detail)
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(request, testEnvironment())
                controller.updateProgress(120_000L)
                controller.stop()

                assertNull(store.read(request.mediaId))
            } finally {
                controller.release()
            }
        }
}

private fun testEnvironment(): JellyfinEnvironment =
    JellyfinEnvironment(
        serverKey = "server-1",
        baseUrl = "https://demo.jellyfin.org",
        accessToken = "token",
        userId = "user",
        deviceId = "device-id",
        deviceName = "UnitTest",
    )

private class TestPlaybackSourceResolver : PlaybackSourceResolver {
    override fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
    ): ResolvedPlaybackSource =
        ResolvedPlaybackSource(
            url = "${environment.baseUrl}/videos/${request.mediaId}/${selection.sourceId}",
            headers = emptyMap(),
            mode = selection.mode,
        )
}

private fun sampleItem(
    id: String = "item-1",
    positionTicks: Long? = null,
): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = null,
        name = "Sample Item",
        sortName = null,
        overview = null,
        type = "Movie",
        mediaType = "Video",
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = positionTicks,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

private fun sampleDetail(
    withDirect: Boolean = false,
    directCodec: String = "h264",
    directSupportsDirectPlay: Boolean = true,
    directSupportsDirectStream: Boolean = false,
    includeSrt: Boolean = false,
    includeVtt: Boolean = false,
    includePgs: Boolean = false,
    durationMs: Long = 90_000L,
): JellyfinItemDetail {
    val baseStreams =
        mutableListOf(
            audioStream(index = 1, language = "en", isDefault = true),
            videoStream(displayTitle = "720p", codec = "h264"),
        )
    if (includeSrt) {
        baseStreams += subtitleStream(index = 2, codec = "srt", displayTitle = "English SRT")
    }
    if (includeVtt) {
        baseStreams += subtitleStream(index = 3, codec = "webvtt", displayTitle = "English VTT")
    }
    if (includePgs) {
        baseStreams += subtitleStream(index = 4, codec = "pgs", displayTitle = "Blu-ray PGS")
    }
    val sources =
        mutableListOf(
            mediaSource(
                id = "hls-source",
                supportsDirectPlay = false,
                supportsDirectStream = false,
                supportsTranscoding = true,
                streams = baseStreams.toList(),
            ),
        )
    if (withDirect) {
        sources +=
            mediaSource(
                id = "direct-source",
                supportsDirectPlay = directSupportsDirectPlay,
                supportsDirectStream = directSupportsDirectStream,
                supportsTranscoding = true,
                streams =
                    listOf(
                        videoStream(displayTitle = "1080p", codec = directCodec),
                        audioStream(index = 10, language = "en", isDefault = true),
                    ),
            )
    }
    return JellyfinItemDetail(
        id = "item-1",
        name = "Sample Detail",
        overview = null,
        taglines = emptyList(),
        runTimeTicks = durationMs * 10_000L,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        genres = emptyList(),
        studios = emptyList(),
        primaryImageTag = null,
        backdropImageTags = emptyList(),
        mediaSources = sources,
    )
}

private fun mediaSource(
    id: String,
    supportsDirectPlay: Boolean,
    supportsDirectStream: Boolean,
    supportsTranscoding: Boolean,
    streams: List<JellyfinMediaStream>,
): JellyfinMediaSource =
    JellyfinMediaSource(
        id = id,
        name = id,
        runTimeTicks = null,
        container = "mp4",
        videoBitrate = 8_000_000,
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = supportsDirectStream,
        supportsTranscoding = supportsTranscoding,
        streams = streams,
    )

private fun videoStream(
    index: Int = 0,
    displayTitle: String,
    codec: String,
): JellyfinMediaStream =
    JellyfinMediaStream(
        type = JellyfinMediaStreamType.VIDEO,
        index = index,
        displayTitle = displayTitle,
        codec = codec,
        language = null,
        isDefault = true,
        isForced = false,
    )

private fun audioStream(
    index: Int,
    language: String,
    isDefault: Boolean,
): JellyfinMediaStream =
    JellyfinMediaStream(
        type = JellyfinMediaStreamType.AUDIO,
        index = index,
        displayTitle = "$language Audio",
        codec = "aac",
        language = language,
        isDefault = isDefault,
        isForced = false,
    )

private fun subtitleStream(
    index: Int,
    codec: String,
    displayTitle: String,
): JellyfinMediaStream =
    JellyfinMediaStream(
        type = JellyfinMediaStreamType.SUBTITLE,
        index = index,
        displayTitle = displayTitle,
        codec = codec,
        language = "en",
        isDefault = index == 2,
        isForced = false,
    )
