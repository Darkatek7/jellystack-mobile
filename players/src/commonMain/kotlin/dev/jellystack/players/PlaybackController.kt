package dev.jellystack.players

import dev.jellystack.core.currentPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

class PlaybackController(
    private val progressStore: PlaybackProgressStore = InMemoryPlaybackProgressStore(),
    private val streamSelector: PlaybackStreamSelector = PlaybackStreamSelector(),
) {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Stopped)
    val state: StateFlow<PlaybackState> = _state

    private val deviceName = currentPlatform().name
    private var session: PlaybackSession? = null
    private var lastPersisted: PlaybackProgress? = null

    fun play(request: PlaybackRequest) {
        val selection = streamSelector.select(request.mediaSources)
        val startingPosition =
            progressStore.read(request.mediaId)?.positionMs
                ?: ticksToMillis(request.resumePositionTicks)
                ?: 0L
        val durationMs = ticksToMillis(request.durationTicks)
        val newSession =
            PlaybackSession(
                mediaId = request.mediaId,
                stream = selection,
                positionMs = startingPosition,
                durationMs = durationMs,
                audioTrack = selection.defaultAudioTrack(),
                subtitleTrack = selection.defaultSubtitleTrack(),
                isPaused = false,
            )
        session = newSession
        publish(newSession)
    }

    fun pause() {
        session?.let {
            val updated = it.copy(isPaused = true)
            session = updated
            publish(updated)
        }
    }

    fun resume() {
        session?.let {
            val updated = it.copy(isPaused = false)
            session = updated
            publish(updated)
        }
    }

    fun stop(saveProgress: Boolean = true) {
        val current = session ?: return
        if (saveProgress) {
            if (isNearCompletion(current)) {
                progressStore.clear(current.mediaId)
            } else {
                persistProgress(current.mediaId, current.positionMs)
            }
        }
        session = null
        lastPersisted = null
        _state.value = PlaybackState.Stopped
    }

    fun seekTo(positionMs: Long) {
        if (positionMs < 0) return
        session?.let {
            val updated = it.copy(positionMs = positionMs)
            session = updated
            publish(updated)
            persistProgressIfNeeded(updated)
        }
    }

    fun updateProgress(positionMs: Long) {
        if (positionMs < 0) return
        session?.let {
            val updated = it.copy(positionMs = positionMs)
            session = updated
            publish(updated)
            persistProgressIfNeeded(updated)
        }
    }

    fun selectSubtitle(trackId: String?) {
        session?.let { current ->
            val subtitle = trackId?.let { id -> current.stream.subtitleTracks.find { it.id == id } }
            val updated = current.copy(subtitleTrack = subtitle)
            session = updated
            publish(updated)
        }
    }

    fun selectAudioTrack(trackId: String) {
        session?.let { current ->
            val audio = current.stream.audioTracks.find { it.id == trackId } ?: return
            val updated = current.copy(audioTrack = audio)
            session = updated
            publish(updated)
        }
    }

    fun clearProgress(mediaId: String) {
        progressStore.clear(mediaId)
        if (session?.mediaId == mediaId) {
            lastPersisted = null
        }
    }

    fun currentSession(): PlaybackSession? = session

    private fun publish(session: PlaybackSession) {
        _state.value =
            PlaybackState.Playing(
                mediaId = session.mediaId,
                deviceName = deviceName,
                stream = session.stream,
                positionMs = session.positionMs,
                durationMs = session.durationMs,
                audioTrack = session.audioTrack,
                subtitleTrack = session.subtitleTrack,
                isPaused = session.isPaused,
            )
    }

    private fun persistProgressIfNeeded(session: PlaybackSession) {
        val progress = PlaybackProgress(session.mediaId, session.positionMs)
        val last = lastPersisted
        if (isNearCompletion(session)) {
            progressStore.clear(session.mediaId)
            lastPersisted = null
            return
        }
        if (last == null || abs(progress.positionMs - last.positionMs) >= PROGRESS_WRITE_INTERVAL_MS) {
            persist(progress)
        }
    }

    private fun persistProgress(mediaId: String, positionMs: Long) {
        persist(PlaybackProgress(mediaId, positionMs))
    }

    private fun persist(progress: PlaybackProgress) {
        progressStore.write(progress)
        lastPersisted = progress
    }

    private fun isNearCompletion(session: PlaybackSession): Boolean {
        val duration = session.durationMs ?: return false
        if (duration <= 0) return false
        return session.positionMs >= (duration * COMPLETION_THRESHOLD_PERCENT).toLong()
    }

    companion object {
        private const val PROGRESS_WRITE_INTERVAL_MS = 5_000L
        private const val COMPLETION_THRESHOLD_PERCENT = 0.97
    }
}

sealed interface PlaybackState {
    data class Playing(
        val mediaId: String,
        val deviceName: String,
        val stream: PlaybackStreamSelection,
        val positionMs: Long,
        val durationMs: Long?,
        val audioTrack: AudioTrack?,
        val subtitleTrack: SubtitleTrack?,
        val isPaused: Boolean,
    ) : PlaybackState

    data object Stopped : PlaybackState
}
