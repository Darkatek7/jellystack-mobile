package dev.jellystack.app

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.russhwolf.settings.SharedPreferencesSettings
import dev.jellystack.app.ui.AndroidPlaybackSurface
import dev.jellystack.core.downloads.AndroidOfflineDownloadManager
import dev.jellystack.core.downloads.SettingsOfflineMediaStore
import dev.jellystack.design.JellystackRoot
import dev.jellystack.players.AndroidOfflinePlaybackSourceResolver
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.SettingsPlaybackProgressStore

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JellystackApp(applicationContext)
        }
    }
}

@OptIn(UnstableApi::class)
@Suppress("FunctionName")
@Composable
private fun JellystackApp(appContext: Context) {
    val playerEngine = remember { AndroidPlayerEngine(appContext) }
    val progressStore =
        remember {
            val preferences =
                appContext.getSharedPreferences(
                    "jellystack_playback",
                    MODE_PRIVATE,
                )
            SettingsPlaybackProgressStore(
                settings = SharedPreferencesSettings(preferences),
            )
        }
    val offlineMediaStore =
        remember {
            val preferences =
                appContext.getSharedPreferences(
                    "jellystack_downloads",
                    MODE_PRIVATE,
                )
            SettingsOfflineMediaStore(
                settings = SharedPreferencesSettings(preferences),
            )
        }
    val downloadManager =
        remember(offlineMediaStore) {
            AndroidOfflineDownloadManager(
                context = appContext,
                mediaStore = offlineMediaStore,
            )
        }
    val controller =
        remember(playerEngine, progressStore, offlineMediaStore) {
            PlaybackController(
                progressStore = progressStore,
                playerEngine = playerEngine,
                offlineMediaStore = offlineMediaStore,
                offlineSourceResolver = AndroidOfflinePlaybackSourceResolver(),
            )
        }
    DisposableEffect(controller, downloadManager) {
        onDispose {
            controller.release()
            downloadManager.release()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        JellystackRoot(
            controller = controller,
            downloadManager = downloadManager,
        )
        AndroidPlaybackSurface(
            controller = controller,
            playerEngine = playerEngine,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
