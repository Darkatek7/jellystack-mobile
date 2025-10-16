package dev.jellystack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.SubtitleTrack

@Suppress("FunctionName")
@UnstableApi
@Composable
fun AndroidPlaybackSurface(
    controller: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
) {
    val playbackState by controller.state.collectAsState()

    val playingState = playbackState as? PlaybackState.Playing ?: return

    androidPlaybackContent(
        playingState = playingState,
        controller = controller,
        playerEngine = playerEngine,
        modifier = modifier,
        showControls = showControls,
    )
}

@UnstableApi
@Composable
private fun androidPlaybackContent(
    playingState: PlaybackState.Playing,
    controller: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    modifier: Modifier,
    showControls: Boolean,
) {
    val audioMenuState = remember { mutableStateOf(false) }
    val subtitleMenuState = remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        androidVideoSurface(
            playerEngine = playerEngine,
            showControls = showControls,
        )

        playbackControls(
            playingState = playingState,
            controller = controller,
            audioMenuState = audioMenuState,
            subtitleMenuState = subtitleMenuState,
        )
    }
}

@UnstableApi
@Composable
private fun androidVideoSurface(
    playerEngine: AndroidPlayerEngine,
    showControls: Boolean,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun BoxScope.playbackControls(
    playingState: PlaybackState.Playing,
    controller: PlaybackController,
    audioMenuState: MutableState<Boolean>,
    subtitleMenuState: MutableState<Boolean>,
) {
    Row(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { controller.stop() }) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Exit playback",
                tint = Color.White,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        audioMenu(
            tracks = playingState.stream.audioTracks,
            controller = controller,
            menuState = audioMenuState,
        )

        subtitleMenu(
            tracks = playingState.stream.subtitleTracks,
            controller = controller,
            menuState = subtitleMenuState,
        )
    }
}

@Composable
private fun audioMenu(
    tracks: List<AudioTrack>,
    controller: PlaybackController,
    menuState: MutableState<Boolean>,
) {
    if (tracks.isEmpty()) return

    Box(modifier = Modifier.background(Color.Transparent)) {
        TextButton(
            onClick = { menuState.value = true },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(
                text = "Audio",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = menuState.value, onDismissRequest = { menuState.value = false }) {
            Text(
                text = "Audio",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            tracks.forEach { track ->
                DropdownMenuItem(
                    onClick = {
                        controller.selectAudioTrack(track.id)
                        menuState.value = false
                    },
                    text = {
                        Text(trackLabel(track))
                    },
                )
            }
        }
    }
}

@Composable
private fun subtitleMenu(
    tracks: List<SubtitleTrack>,
    controller: PlaybackController,
    menuState: MutableState<Boolean>,
) {
    if (tracks.isEmpty()) return

    Box(modifier = Modifier.background(Color.Transparent)) {
        TextButton(
            onClick = { menuState.value = true },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = menuState.value, onDismissRequest = { menuState.value = false }) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DropdownMenuItem(
                onClick = {
                    controller.selectSubtitle(null)
                    menuState.value = false
                },
                text = {
                    Text("Off")
                },
            )
            tracks.forEach { track ->
                DropdownMenuItem(
                    onClick = {
                        controller.selectSubtitle(track.id)
                        menuState.value = false
                    },
                    text = {
                        Text(trackLabel(track))
                    },
                )
            }
        }
    }
}

private fun trackLabel(track: AudioTrack?): String =
    track?.let {
        buildList {
            val title = it.title
            val language = it.language
            val codec = it.codec
            if (!title.isNullOrBlank()) add(title)
            if (!language.isNullOrBlank()) add(language.uppercase())
            if (!codec.isNullOrBlank()) add(codec.uppercase())
        }.joinToString(separator = " · ").ifBlank { "Track" }
    } ?: "Track"

private fun trackLabel(track: SubtitleTrack?): String =
    track?.let {
        buildList {
            val title = it.title
            val language = it.language
            if (!title.isNullOrBlank()) add(title)
            if (!language.isNullOrBlank()) add(language.uppercase())
            add(it.format.name)
        }.joinToString(separator = " · ").ifBlank { "Subtitles" }
    } ?: "Subtitles"
