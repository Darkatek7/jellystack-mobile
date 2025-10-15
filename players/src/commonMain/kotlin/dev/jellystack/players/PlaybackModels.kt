package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType

private const val TICKS_PER_MILLISECOND = 10_000L

internal fun Long.toMillisFromTicks(): Long = this / TICKS_PER_MILLISECOND

internal fun ticksToMillis(value: Long?): Long? = value?.toMillisFromTicks()

data class PlaybackRequest(
    val mediaId: String,
    val mediaSources: List<JellyfinMediaSource>,
    val resumePositionTicks: Long? = null,
    val durationTicks: Long? = null,
) {
    companion object {
        fun from(item: JellyfinItem, detail: JellyfinItemDetail): PlaybackRequest =
            PlaybackRequest(
                mediaId = item.id,
                mediaSources = detail.mediaSources,
                resumePositionTicks = item.positionTicks,
                durationTicks =
                    detail.runTimeTicks
                        ?: detail.mediaSources
                            .asSequence()
                            .mapNotNull { it.runTimeTicks }
                            .maxOrNull(),
            )
    }
}

enum class PlaybackMode {
    DIRECT,
    HLS,
}

data class AudioTrack(
    val id: String,
    val language: String?,
    val title: String?,
    val codec: String?,
    val isDefault: Boolean,
)

enum class SubtitleFormat {
    SRT,
    VTT,
}

data class SubtitleTrack(
    val id: String,
    val language: String?,
    val title: String?,
    val format: SubtitleFormat,
    val isDefault: Boolean,
    val isForced: Boolean,
)

data class PlaybackStreamSelection(
    val sourceId: String,
    val mode: PlaybackMode,
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val videoBitrate: Int?,
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
)

data class PlaybackSession(
    val mediaId: String,
    val stream: PlaybackStreamSelection,
    val positionMs: Long,
    val durationMs: Long?,
    val audioTrack: AudioTrack?,
    val subtitleTrack: SubtitleTrack?,
    val isPaused: Boolean,
)

data class PlaybackProgress(
    val mediaId: String,
    val positionMs: Long,
)

internal fun JellyfinMediaStream.toAudioTrack(): AudioTrack? =
    if (type != JellyfinMediaStreamType.AUDIO) {
        null
    } else {
        val id = index?.toString() ?: displayTitle ?: language ?: "audio-${hashCode()}"
        AudioTrack(
            id = id,
            language = language,
            title = displayTitle,
            codec = codec,
            isDefault = isDefault,
        )
    }

internal fun JellyfinMediaStream.toSubtitleTrack(): SubtitleTrack? {
    if (type != JellyfinMediaStreamType.SUBTITLE) {
        return null
    }
    val format =
        when (codec?.lowercase()) {
            "srt", "subrip" -> SubtitleFormat.SRT
            "webvtt", "vtt" -> SubtitleFormat.VTT
            else -> return null
        }
    val id = index?.toString() ?: displayTitle ?: language ?: "${format.name.lowercase()}-${hashCode()}"
    return SubtitleTrack(
        id = id,
        language = language,
        title = displayTitle ?: language ?: format.name,
        format = format,
        isDefault = isDefault,
        isForced = isForced,
    )
}

internal fun PlaybackStreamSelection.defaultAudioTrack(): AudioTrack? =
    audioTracks.firstOrNull { it.isDefault } ?: audioTracks.firstOrNull()

internal fun PlaybackStreamSelection.defaultSubtitleTrack(): SubtitleTrack? =
    subtitleTracks.firstOrNull { it.isDefault } ?: subtitleTracks.firstOrNull { !it.isForced }
