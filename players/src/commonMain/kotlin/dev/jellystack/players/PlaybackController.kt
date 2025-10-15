package dev.jellystack.players

import dev.jellystack.core.currentPlatform
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlaybackController(
    private val progressStore: PlaybackProgressStore = InMemoryPlaybackProgressStore(),
    private val streamSelector: PlaybackStreamSelector = PlaybackStreamSelector(),
    private val playbackSourceResolver: PlaybackSourceResolver = JellyfinPlaybackSourceResolver(),
    private val playerEngine: PlayerEngine = NoopPlayerEngine(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Stopped)
    val state: StateFlow<PlaybackState> = _state

    private val deviceName = currentPlatform().name
    private var session: PlaybackSession? = null
    private var lastPersisted: PlaybackProgress? = null
    private var progressJob: Job? = null
    private var eventsJob: Job? = null

    suspend fun play(
        request: PlaybackRequest,
        environment: JellyfinEnvironment,
    ) {
        stopInternal(saveProgress = true)
        val selection = streamSelector.select(request.mediaSources)
        val startingPosition =
            progressStore.read(request.mediaId)?.positionMs
                ?: ticksToMillis(request.resumePositionTicks)
                ?: 0L
        val durationMs = ticksToMillis(request.durationTicks)
        val source =
            playbackSourceResolver.resolve(
                request = request,
                selection = selection,
                environment = environment,
                startPositionMs = startingPosition,
            )
        playerEngine.prepare(
            source = source,
            startPositionMs = startingPosition,
            audioTrack = selection.defaultAudioTrack(),
            subtitleTrack = selection.defaultSubtitleTrack(),
        )
        val newSession =
            PlaybackSession(
                mediaId = request.mediaId,
                stream = selection,
                positionMs = startingPosition,
                durationMs = durationMs,
                audioTrack = selection.defaultAudioTrack(),
                subtitleTrack = selection.defaultSubtitleTrack(),
                isPaused = false,
                source = source,
            )
        session = newSession
        publish(newSession)
        playerEngine.play()
        startCollectors()
    }

    fun pause() {
        session?.let {
            playerEngine.pause()
            val updated = it.copy(isPaused = true)
            session = updated
            publish(updated)
        }
    }

    fun resume() {
        session?.let {
            playerEngine.play()
            val updated = it.copy(isPaused = false)
            session = updated
            publish(updated)
        }
    }

    fun stop(saveProgress: Boolean = true) {
        stopInternal(saveProgress)
    }

    private fun stopInternal(saveProgress: Boolean) {
        cancelCollectors()
        playerEngine.stop()
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
            playerEngine.seekTo(positionMs)
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
            playerEngine.setSubtitleTrack(subtitle)
        }
    }

    fun selectAudioTrack(trackId: String) {
        session?.let { current ->
            val audio = current.stream.audioTracks.find { it.id == trackId } ?: return
            val updated = current.copy(audioTrack = audio)
            session = updated
            publish(updated)
            playerEngine.setAudioTrack(audio)
        }
    }

    fun clearProgress(mediaId: String) {
        progressStore.clear(mediaId)
        if (session?.mediaId == mediaId) {
            lastPersisted = null
        }
    }

    fun currentSession(): PlaybackSession? = session
    fun release() {
        stopInternal(saveProgress = false)
        _state.value = PlaybackState.Stopped
        playerEngine.release()
        scope.cancel()
    }

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
                source = session.source,
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

    private fun startCollectors() {
        cancelCollectors()
        progressJob =
            scope.launch {
                playerEngine.positionUpdates.collect { positionMs ->
                    updateProgress(positionMs)
                }
            }
        eventsJob =
            scope.launch {
                playerEngine.events.collect { event ->
                    when (event) {
                        PlayerEvent.Completed ->
                            session?.let {
                                progressStore.clear(it.mediaId)
                                stopInternal(saveProgress = false)
                            }
                        is PlayerEvent.Error -> {
                            // Preserve last progress but surface stop so UI can react
                            stopInternal(saveProgress = true)
                        }
                    }
                }
            }
    }

    private fun cancelCollectors() {
        progressJob?.cancel()
        eventsJob?.cancel()
        progressJob = null
        eventsJob = null
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
        val source: ResolvedPlaybackSource,
    ) : PlaybackState

    data object Stopped : PlaybackState
}
