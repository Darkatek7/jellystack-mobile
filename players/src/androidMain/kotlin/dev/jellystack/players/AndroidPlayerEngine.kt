package dev.jellystack.players

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidPlayerEngine(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PlayerEngine {
    private val player =
        ExoPlayer.Builder(context)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            scope.launch { _events.emit(PlayerEvent.Completed) }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        scope.launch { _events.emit(PlayerEvent.Error(error)) }
                    }
                })
            }

    private val _positions = MutableSharedFlow<Long>(replay = 1)
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    private var positionJob = scope.launch {
        while (isActive) {
            _positions.emit(player.currentPosition)
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    override val positionUpdates: SharedFlow<Long> = _positions.asSharedFlow()
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        withContext(Dispatchers.Main) {
            val dataSourceFactory =
                DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(source.headers)

            val mediaItem =
                MediaItem.Builder()
                    .setUri(Uri.parse(source.url))
                    .apply {
                        when (source.mode) {
                            PlaybackMode.DIRECT ->
                                setMimeType(MimeTypes.VIDEO_MP4)
                            PlaybackMode.HLS ->
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                        }
                    }.build()

            val mediaSource =
                when (source.mode) {
                    PlaybackMode.DIRECT ->
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)

                    PlaybackMode.HLS ->
                        HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                }

            player.stop()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.seekTo(startPositionMs)
        }
    }

    override fun play() {
        player.playWhenReady = true
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setAudioTrack(track: AudioTrack?) {
        // Track selection will be enhanced in future iterations.
    }

    override fun setSubtitleTrack(track: SubtitleTrack?) {
        // Subtitle track switching placeholder.
    }

    override fun release() {
        positionJob.cancel()
        player.release()
        scope.cancel()
    }

    private companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }
}
