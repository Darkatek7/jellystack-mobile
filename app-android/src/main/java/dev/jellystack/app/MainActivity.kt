package dev.jellystack.app

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.russhwolf.settings.SharedPreferencesSettings
import dev.jellystack.app.ui.AndroidPlaybackSurface
import dev.jellystack.design.JellystackRoot
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.SettingsPlaybackProgressStore

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val playerEngine = remember { AndroidPlayerEngine(applicationContext) }
            val progressStore =
                remember {
                    val preferences =
                        applicationContext.getSharedPreferences(
                            "jellystack_playback",
                            MODE_PRIVATE,
                        )
                    SettingsPlaybackProgressStore(
                        settings = SharedPreferencesSettings(preferences),
                    )
                }
            val controller =
                remember(playerEngine) {
                    PlaybackController(
                        progressStore = progressStore,
                        playerEngine = playerEngine,
                    )
                }
            DisposableEffect(Unit) {
                onDispose { controller.release() }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                JellystackRoot(controller = controller)
                AndroidPlaybackSurface(
                    controller = controller,
                    playerEngine = playerEngine,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
