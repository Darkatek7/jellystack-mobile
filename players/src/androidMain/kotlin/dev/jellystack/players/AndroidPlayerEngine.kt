package dev.jellystack.players

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
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

@UnstableApi
class AndroidPlayerEngine(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PlayerEngine {
    private val exoPlayer =
        ExoPlayer
            .Builder(context)
            .build()
            .apply {
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                scope.launch { eventFlow.emit(PlayerEvent.Completed) }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            scope.launch { eventFlow.emit(PlayerEvent.Error(error)) }
                        }
                    },
                )
            }

    private val positionFlow = MutableSharedFlow<Long>(replay = 1)
    private val eventFlow = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    private var positionJob =
        scope.launch {
            while (isActive) {
                positionFlow.emit(exoPlayer.currentPosition)
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }

    override val positionUpdates: SharedFlow<Long> = positionFlow.asSharedFlow()
    override val events: SharedFlow<PlayerEvent> = eventFlow.asSharedFlow()

    fun createVideoSurface(
        context: Context,
        showControls: Boolean,
    ): View =
        PlayerView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            updateVideoSurface(
                view = this,
                showControls = showControls,
            )
        }

    fun updateVideoSurface(
        view: View,
        showControls: Boolean,
    ) {
        val playerView = view as? PlayerView ?: return
        playerView.useController = showControls
        if (playerView.player !== exoPlayer) {
            playerView.player = exoPlayer
        }
    }

    fun releaseVideoSurface(view: View) {
        val playerView = view as? PlayerView ?: return
        if (playerView.player === exoPlayer) {
            playerView.player = null
        }
    }

    @UnstableApi
    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        withContext(Dispatchers.Main) {
            val dataSourceFactory =
                DefaultHttpDataSource
                    .Factory()
                    .setDefaultRequestProperties(source.headers)

            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(Uri.parse(source.url))
                    .apply {
                        val mimeType =
                            source.mimeType
                                ?: when (source.mode) {
                                    PlaybackMode.DIRECT -> MimeTypes.VIDEO_MP4
                                    PlaybackMode.HLS -> MimeTypes.APPLICATION_M3U8
                                }
                        setMimeType(mimeType)
                    }.build()

            val mediaSource =
                when (source.mode) {
                    PlaybackMode.DIRECT ->
                        ProgressiveMediaSource
                            .Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)

                    PlaybackMode.HLS ->
                        HlsMediaSource
                            .Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                }

            exoPlayer.stop()
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.seekTo(startPositionMs)
            applyTrackPreferences(
                audioTrack = audioTrack,
                subtitleTrack = subtitleTrack,
            )
        }
    }

    override fun play() {
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun setAudioTrack(track: AudioTrack?) {
        val builder =
            exoPlayer
                .trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)

        if (track?.language != null) {
            builder.setPreferredAudioLanguages(track.language)
        } else {
            builder.setPreferredAudioLanguages()
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    override fun setSubtitleTrack(track: SubtitleTrack?) {
        val builder =
            exoPlayer
                .trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        if (track == null) {
            builder.setPreferredTextLanguages()
            builder.setPreferredTextRoleFlags(0)
            builder.setSelectUndeterminedTextLanguage(false)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            if (track.language != null) {
                builder.setPreferredTextLanguages(track.language)
                builder.setSelectUndeterminedTextLanguage(false)
            } else {
                builder.setPreferredTextLanguages()
                builder.setSelectUndeterminedTextLanguage(true)
            }
            val roleFlags =
                if (track.isForced) {
                    C.ROLE_FLAG_SUBTITLE or C.ROLE_FLAG_CAPTION
                } else {
                    C.ROLE_FLAG_SUBTITLE
                }
            builder.setPreferredTextRoleFlags(roleFlags)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    override fun release() {
        positionJob.cancel()
        exoPlayer.release()
        scope.cancel()
    }

    private fun applyTrackPreferences(
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        setAudioTrack(audioTrack)
        setSubtitleTrack(subtitleTrack)
    }

    private companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }
}
