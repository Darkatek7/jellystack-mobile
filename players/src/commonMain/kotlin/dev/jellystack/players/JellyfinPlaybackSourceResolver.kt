package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import kotlin.math.max

class JellyfinPlaybackSourceResolver(
    private val clientName: String = "Jellystack",
    private val clientVersion: String = "0.1.0",
) : PlaybackSourceResolver {
    override fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
    ): ResolvedPlaybackSource {
        val baseUrl = environment.baseUrl.trimEnd('/')
        val url =
            when (selection.mode) {
                PlaybackMode.DIRECT ->
                    buildString {
                        append(baseUrl)
                        append("/Videos/")
                        append(request.mediaId)
                        append("/stream.")
                        append(selection.container ?: "mp4")
                        append("?Static=true")
                        append("&api_key=")
                        append(environment.accessToken)
                        append("&MediaSourceId=")
                        append(selection.sourceId)
                        append("&DeviceId=")
                        append(environment.deviceId)
                        append("&UserId=")
                        append(environment.userId)
                        val startTicks = max(0, startPositionMs).toTicks()
                        if (startTicks > 0) {
                            append("&StartTimeTicks=")
                            append(startTicks)
                        }
                    }

                PlaybackMode.HLS ->
                    buildString {
                        append(baseUrl)
                        append("/Videos/")
                        append(request.mediaId)
                        append("/master.m3u8")
                        append("?api_key=")
                        append(environment.accessToken)
                        append("&MediaSourceId=")
                        append(selection.sourceId)
                        append("&DeviceId=")
                        append(environment.deviceId)
                        append("&UserId=")
                        append(environment.userId)
                        val startTicks = max(0, startPositionMs).toTicks()
                        if (startTicks > 0) {
                            append("&StartTimeTicks=")
                            append(startTicks)
                        }
                    }
            }

        val headers =
            buildMap {
                put("X-Emby-Authorization", authorizationHeader(environment))
                put("User-Agent", "$clientName/${environment.deviceName}")
            }

        return ResolvedPlaybackSource(
            url = url,
            headers = headers,
            mode = selection.mode,
        )
    }

    private fun authorizationHeader(environment: JellyfinEnvironment): String {
        val builder = StringBuilder()
        builder.append("MediaBrowser ")
        builder.append("""Client="$clientName"""")
        builder.append(""", Device="${environment.deviceName}"""")
        builder.append(""", DeviceId="${environment.deviceId}"""")
        builder.append(""", Version="$clientVersion"""")
        if (environment.accessToken.isNotEmpty()) {
            builder.append(""", Token="${environment.accessToken}"""")
        }
        return builder.toString()
    }
}
