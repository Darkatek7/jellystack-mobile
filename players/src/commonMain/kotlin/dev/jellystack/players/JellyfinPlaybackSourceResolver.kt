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
        val (url, mimeType) =
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
                    } to containerMimeType(selection.container)

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
                    } to HLS_MIME_TYPE
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
            mimeType = mimeType,
        )
    }

    private fun containerMimeType(container: String?): String =
        when (container?.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "ts", "mpegts", "m2ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            else -> "video/mp4"
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

    private companion object {
        private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
    }
}
