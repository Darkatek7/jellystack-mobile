package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType

private val RESOLUTION_REGEX = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)

class PlaybackStreamSelector {
    fun select(mediaSources: List<JellyfinMediaSource>): PlaybackStreamSelection {
        require(mediaSources.isNotEmpty()) { "Playback requires at least one media source." }

        val directCandidate =
            mediaSources
                .asSequence()
                .filter { it.supportsDirectPlay }
                .mapNotNull { source ->
                    val videoStream = source.streams.firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
                    if (videoStream?.codec?.equals("h264", ignoreCase = true) == true) {
                        Triple(source, videoStream, resolutionScore(videoStream))
                    } else {
                        null
                    }
                }.maxWithOrNull(
                    compareBy<Triple<JellyfinMediaSource, JellyfinMediaStream, Int>> { it.third }
                        .thenBy { it.first.videoBitrate ?: 0 },
                )?.first

        if (directCandidate != null) {
            return buildSelection(directCandidate, PlaybackMode.DIRECT)
        }

        val hlsCandidate =
            mediaSources.firstOrNull { it.supportsTranscoding }
                ?: mediaSources.first()

        return buildSelection(hlsCandidate, PlaybackMode.HLS)
    }

    private fun buildSelection(
        source: JellyfinMediaSource,
        mode: PlaybackMode,
    ): PlaybackStreamSelection {
        val audioTracks = source.streams.mapNotNull { it.toAudioTrack() }
        val subtitleTracks = source.streams.mapNotNull { it.toSubtitleTrack() }
        val defaultAudioCodec =
            audioTracks.firstOrNull { it.isDefault }?.codec
                ?: audioTracks.firstOrNull()?.codec
        val videoCodec =
            source.streams
                .firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
                ?.codec
        return PlaybackStreamSelection(
            sourceId = source.id,
            mode = mode,
            container = source.container,
            videoCodec = videoCodec,
            audioCodec = defaultAudioCodec,
            videoBitrate = source.videoBitrate,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
        )
    }

    private fun resolutionScore(stream: JellyfinMediaStream): Int {
        val resolution =
            stream.displayTitle?.let { title ->
                RESOLUTION_REGEX
                    .find(title)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
        return resolution ?: 0
    }
}
