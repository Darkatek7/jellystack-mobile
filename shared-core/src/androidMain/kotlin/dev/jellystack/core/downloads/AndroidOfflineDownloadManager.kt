package dev.jellystack.core.downloads

import android.content.Context
import android.os.Build
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AndroidOfflineDownloadManager(
    private val context: Context,
    private val mediaStore: OfflineMediaStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OfflineDownloadManager {
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, DownloadTask>()

    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()

    init {
        scope.launch {
            mediaStore.list().forEach { media ->
                val file = File(media.filePath)
                if (file.exists()) {
                    updateStatus(
                        media.mediaId,
                        DownloadStatus.Completed(
                            mediaId = media.mediaId,
                            filePath = media.filePath,
                            bytesDownloaded = file.length(),
                        ),
                    )
                } else {
                    mediaStore.remove(media.mediaId)
                }
            }
        }
    }

    override fun enqueue(request: DownloadRequest) {
        scope.launch {
            mutex.withLock {
                if (tasks.containsKey(request.mediaId)) {
                    return@withLock
                }
                val task =
                    DownloadTask(
                        request = request,
                        targetFile = targetFileFor(request),
                    )
                tasks[request.mediaId] = task
                updateStatus(request.mediaId, DownloadStatus.Queued(request.mediaId))
                startDownload(task)
            }
        }
    }

    override fun pause(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[mediaId] ?: return@withLock
                task.paused = true
            }
        }
    }

    override fun resume(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[mediaId] ?: return@withLock
                if (!task.paused) return@withLock
                task.paused = false
                startDownload(task)
            }
        }
    }

    override fun remove(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks.remove(mediaId)
                task?.job?.cancel()
                task?.targetFile?.delete()
                mediaStore.remove(mediaId)
                _statuses.emit(_statuses.value - mediaId)
            }
        }
    }

    fun release() {
        scope.cancel()
    }

    private fun startDownload(task: DownloadTask) {
        val existingBytes = task.targetFile.length()
        task.job =
            scope.launch {
                try {
                    val result =
                        download(task.request, task.targetFile, existingBytes) { progress ->
                            updateStatus(task.request.mediaId, progress)
                        }
                    when (result) {
                        is DownloadCompletion.Completed -> {
                            mediaStore.write(
                                OfflineMedia(
                                    mediaId = task.request.mediaId,
                                    filePath = task.targetFile.absolutePath,
                                    mimeType = task.request.mimeType,
                                    checksumSha256 = result.checksum,
                                    sizeBytes = task.targetFile.length(),
                                ),
                            )
                            updateStatus(
                                task.request.mediaId,
                                DownloadStatus.Completed(
                                    mediaId = task.request.mediaId,
                                    filePath = task.targetFile.absolutePath,
                                    bytesDownloaded = task.targetFile.length(),
                                ),
                            )
                        }
                        is DownloadCompletion.Paused -> {
                            updateStatus(
                                task.request.mediaId,
                                DownloadStatus.Paused(
                                    mediaId = task.request.mediaId,
                                    bytesDownloaded = result.bytesDownloaded,
                                    totalBytes = result.totalBytes,
                                ),
                            )
                        }
                    }
                } catch (throwable: Throwable) {
                    task.targetFile.delete()
                    mediaStore.remove(task.request.mediaId)
                    updateStatus(
                        task.request.mediaId,
                        DownloadStatus.Failed(task.request.mediaId, throwable),
                    )
                } finally {
                    mutex.withLock {
                        if (!task.paused) {
                            tasks.remove(task.request.mediaId)
                        }
                    }
                }
            }
    }

    private suspend fun download(
        request: DownloadRequest,
        target: File,
        resumeOffset: Long,
        onProgress: suspend (DownloadStatus.InProgress) -> Unit,
    ): DownloadCompletion =
        withContext(ioDispatcher) {
            val connection =
                (URL(request.downloadUrl).openConnection() as HttpURLConnection).apply {
                    request.headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }
                    if (resumeOffset > 0) {
                        setRequestProperty("Range", "bytes=$resumeOffset-")
                    }
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }

            val declaredLength =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    connection.contentLength.toLong()
                }
            val totalBytes =
                connection.headerFields["Content-Range"]
                    ?.firstOrNull()
                    ?.substringAfter("/")
                    ?.toLongOrNull()
                    ?: declaredLength.takeIf { it >= 0 }?.let { it + resumeOffset }

            val digest =
                if (request.checksumSha256 != null) {
                    MessageDigest.getInstance("SHA-256")
                } else {
                    null
                }

            val raf = RandomAccessFile(target, "rw")
            if (resumeOffset > 0) {
                raf.seek(resumeOffset)
            }

            var downloaded = resumeOffset
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            connection.inputStream.use { input ->
                while (true) {
                    if (!isActive) {
                        raf.close()
                        connection.disconnect()
                        return@withContext DownloadCompletion.Paused(downloaded, totalBytes)
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    digest?.update(buffer, 0, read)
                    downloaded += read
                    onProgress(
                        DownloadStatus.InProgress(
                            mediaId = request.mediaId,
                            bytesDownloaded = downloaded,
                            totalBytes = totalBytes,
                        ),
                    )
                    val task = mutex.withLock { tasks[request.mediaId] }
                    if (task?.paused == true) {
                        raf.close()
                        connection.disconnect()
                        return@withContext DownloadCompletion.Paused(downloaded, totalBytes)
                    }
                }
            }

            raf.fd.sync()
            raf.close()
            connection.disconnect()

            val finalSize = target.length()
            request.expectedSizeBytes?.let { expected ->
                if (expected != finalSize) {
                    throw IllegalStateException(
                        "Downloaded size mismatch. Expected=$expected, actual=$finalSize",
                    )
                }
            }

            val checksum =
                if (digest != null) {
                    val computed = digest.digest().joinToString("") { "%02x".format(it) }
                    val normalizedExpected = request.checksumSha256!!.lowercase()
                    if (computed != normalizedExpected) {
                        throw IllegalStateException("Checksum mismatch")
                    }
                    computed
                } else {
                    null
                }

            DownloadCompletion.Completed(checksum)
        }

    private fun targetFileFor(request: DownloadRequest): File {
        val downloadsDir = File(context.filesDir, "offline/downloads")
        downloadsDir.mkdirs()
        val extension =
            request.mimeType?.let { mime ->
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            } ?: "bin"
        val safeId = request.mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(downloadsDir, "$safeId.$extension")
    }

    private suspend fun updateStatus(
        mediaId: String,
        status: DownloadStatus,
    ) {
        _statuses.emit(_statuses.value + (mediaId to status))
    }

    private data class DownloadTask(
        val request: DownloadRequest,
        val targetFile: File,
        var job: Job? = null,
        var paused: Boolean = false,
    )

    private sealed class DownloadCompletion {
        data class Completed(
            val checksum: String?,
        ) : DownloadCompletion()

        data class Paused(
            val bytesDownloaded: Long,
            val totalBytes: Long?,
        ) : DownloadCompletion()
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
