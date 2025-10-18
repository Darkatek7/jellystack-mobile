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
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.downloads.AndroidOfflineDownloadManager
import dev.jellystack.core.downloads.SettingsOfflineDownloadQueueStore
import dev.jellystack.core.downloads.SettingsOfflineMediaStore
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.playback.JellyfinOfflineProgressSyncer
import dev.jellystack.core.playback.SettingsOfflinePlaybackEventStore
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
    val environment = rememberAndroidPlaybackEnvironment(appContext)
    DisposableEffect(environment.controller, environment.downloadManager) {
        onDispose {
            environment.controller.release()
            environment.downloadManager.release()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        JellystackRoot(
            controller = environment.controller,
            downloadManager = environment.downloadManager,
        )
        AndroidPlaybackSurface(
            controller = environment.controller,
            playerEngine = environment.playerEngine,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberAndroidPlaybackEnvironment(appContext: Context): AndroidPlaybackEnvironment {
    val playerEngine = remember { AndroidPlayerEngine(appContext) }
    val progressStore = rememberPlaybackProgressStore(appContext)
    val offlineStores = rememberOfflineStores(appContext)
    val koin = remember { JellystackDI.koin }
    val browseRepository = remember(koin) { koin.get<JellyfinBrowseRepository>() }
    val progressSyncer =
        remember(browseRepository, offlineStores.progress) {
            JellyfinOfflineProgressSyncer(
                repository = browseRepository,
                store = offlineStores.progress,
            )
        }
    val downloadManager =
        remember(offlineStores) {
            AndroidOfflineDownloadManager(
                context = appContext,
                mediaStore = offlineStores.media,
                queueStore = offlineStores.queue,
            )
        }
    val controller =
        remember(playerEngine, progressStore, offlineStores, progressSyncer) {
            PlaybackController(
                progressStore = progressStore,
                playerEngine = playerEngine,
                offlineMediaStore = offlineStores.media,
                offlineSourceResolver = AndroidOfflinePlaybackSourceResolver(),
                offlineProgressSyncer = progressSyncer,
            )
        }
    return remember(playerEngine, controller, downloadManager) {
        AndroidPlaybackEnvironment(
            playerEngine = playerEngine,
            controller = controller,
            downloadManager = downloadManager,
        )
    }
}

@Composable
private fun rememberPlaybackProgressStore(appContext: Context): SettingsPlaybackProgressStore =
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

@Composable
private fun rememberOfflineStores(appContext: Context): OfflineStores =
    remember {
        val preferences =
            appContext.getSharedPreferences(
                "jellystack_downloads",
                MODE_PRIVATE,
            )
        val settings = SharedPreferencesSettings(preferences)
        OfflineStores(
            media = SettingsOfflineMediaStore(settings),
            queue = SettingsOfflineDownloadQueueStore(settings),
            progress = SettingsOfflinePlaybackEventStore(settings),
        )
    }

@OptIn(UnstableApi::class)
private data class AndroidPlaybackEnvironment(
    val playerEngine: AndroidPlayerEngine,
    val controller: PlaybackController,
    val downloadManager: AndroidOfflineDownloadManager,
)

private data class OfflineStores(
    val media: SettingsOfflineMediaStore,
    val queue: SettingsOfflineDownloadQueueStore,
    val progress: SettingsOfflinePlaybackEventStore,
)
