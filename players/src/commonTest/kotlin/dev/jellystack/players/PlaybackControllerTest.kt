package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackControllerTest {
    @Test
    fun transitionsBetweenStates() {
        val controller = PlaybackController()
        controller.play("123")
        val playing = controller.state.value as PlaybackState.Playing
        assertEquals("123", playing.mediaId)
        controller.stop()
        assertEquals(PlaybackState.Stopped, controller.state.value)
    }
}
