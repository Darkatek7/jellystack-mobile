package dev.jellystack.players

import dev.jellystack.core.currentPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaybackController {
    private val _state = MutableStateFlow(PlaybackState.Stopped)
    val state: StateFlow<PlaybackState> = _state

    fun play(mediaId: String) {
        _state.value = PlaybackState.Playing(mediaId, currentPlatform().name)
    }

    fun stop() {
        _state.value = PlaybackState.Stopped
    }
}

sealed interface PlaybackState {
    data class Playing(val mediaId: String, val deviceName: String) : PlaybackState
    data object Stopped : PlaybackState
}
