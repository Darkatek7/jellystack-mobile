package dev.jellystack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import dev.jellystack.design.JellystackRoot
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember {
                PlaybackController(playerEngine = AndroidPlayerEngine(applicationContext))
            }
            DisposableEffect(Unit) {
                onDispose { controller.release() }
            }
            JellystackRoot(controller = controller)
        }
    }
}
