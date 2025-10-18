package dev.jellystack.players

import dev.jellystack.core.downloads.OfflineMedia
import java.io.File

class AndroidOfflinePlaybackSourceResolver : OfflinePlaybackSourceResolver {
    override fun resolve(media: OfflineMedia): ResolvedPlaybackSource {
        val file = File(media.filePath)
        require(file.exists()) { "Offline media file missing at ${media.filePath}" }
        return ResolvedPlaybackSource(
            url = file.toURI().toString(),
            headers = emptyMap(),
            mode = PlaybackMode.LOCAL,
            mimeType = media.mimeType,
        )
    }
}
