package dev.jellystack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackState

@Suppress("FunctionName")
@Composable
fun AndroidPlaybackSurface(
    controller: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
) {
    val playbackState by controller.state.collectAsState()

    if (playbackState is PlaybackState.Playing) {
        AndroidView(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black),
            factory = { context ->
                playerEngine.createVideoSurface(
                    context = context,
                    showControls = showControls,
                )
            },
            update = { surface ->
                playerEngine.updateVideoSurface(
                    view = surface,
                    showControls = showControls,
                )
            },
            onRelease = { surface ->
                playerEngine.releaseVideoSurface(surface)
            },
        )
    }
}
