package dev.jellystack.core.downloads

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class DownloadRequest(
    val mediaId: String,
    val downloadUrl: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val expectedSizeBytes: Long?,
    val checksumSha256: String?,
    val kind: OfflineMediaKind = OfflineMediaKind.VIDEO,
    val language: String? = null,
    val relativePath: String? = null,
)

sealed class DownloadStatus {
    data class Queued(
        val mediaId: String,
    ) : DownloadStatus()

    data class InProgress(
        val mediaId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : DownloadStatus()

    data class Paused(
        val mediaId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : DownloadStatus()

    data class Completed(
        val mediaId: String,
        val filePath: String,
        val bytesDownloaded: Long,
    ) : DownloadStatus()

    data class Failed(
        val mediaId: String,
        val cause: Throwable,
    ) : DownloadStatus()
}

interface OfflineDownloadManager {
    val statuses: StateFlow<Map<String, DownloadStatus>>

    fun enqueue(request: DownloadRequest)

    fun pause(mediaId: String)

    fun resume(mediaId: String)

    fun remove(mediaId: String)
}

@Serializable
enum class OfflineMediaKind {
    VIDEO,
    SUBTITLE,
}

@Serializable
data class OfflineMedia(
    val mediaId: String,
    val filePath: String,
    val mimeType: String?,
    val checksumSha256: String?,
    val sizeBytes: Long?,
    val kind: OfflineMediaKind = OfflineMediaKind.VIDEO,
    val language: String? = null,
    val relativePath: String? = null,
) {
    fun isValid(): Boolean = filePath.isNotBlank()
}

interface OfflineMediaStore {
    fun read(mediaId: String): OfflineMedia?

    fun write(media: OfflineMedia)

    fun writeAll(media: List<OfflineMedia>)

    fun remove(mediaId: String)

    fun list(): List<OfflineMedia>
}

class InMemoryOfflineMediaStore : OfflineMediaStore {
    private val backing = mutableMapOf<String, OfflineMedia>()

    override fun read(mediaId: String): OfflineMedia? = backing[mediaId]

    override fun write(media: OfflineMedia) {
        backing[media.mediaId] = media
    }

    override fun writeAll(media: List<OfflineMedia>) {
        media.forEach { write(it) }
    }

    override fun remove(mediaId: String) {
        backing.remove(mediaId)
    }

    override fun list(): List<OfflineMedia> = backing.values.toList()
}
