package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment

data class ResolvedPlaybackSource(
    val url: String,
    val headers: Map<String, String>,
    val mode: PlaybackMode,
    val mimeType: String?,
)

fun interface PlaybackSourceResolver {
    fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
    ): ResolvedPlaybackSource
}
