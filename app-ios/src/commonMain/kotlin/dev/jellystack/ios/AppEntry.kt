package dev.jellystack.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.downloads.IosOfflineDownloadManager
import dev.jellystack.core.downloads.SettingsOfflineDownloadQueueStore
import dev.jellystack.core.downloads.SettingsOfflineMediaStore
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.playback.JellyfinOfflineProgressSyncer
import dev.jellystack.core.playback.SettingsOfflinePlaybackEventStore
import dev.jellystack.design.JellystackRoot
import dev.jellystack.ios.di.iosAppModule
import dev.jellystack.players.IosOfflinePlaybackSourceResolver
import dev.jellystack.players.IosPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.SettingsPlaybackProgressStore
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin
import platform.Foundation.NSUserDefaults

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
fun ComposeEntry() {
    configureLogging()
    if (!JellystackDI.isStarted()) {
        startKoin {
            modules(JellystackDI.modules + iosAppModule)
        }
    }
    val playerEngine = remember { IosPlayerEngine() }
    val playbackDefaults = remember { NSUserDefaults(suiteName = "dev.jellystack.playback") ?: NSUserDefaults.standardUserDefaults() }
    val downloadDefaults = remember { NSUserDefaults(suiteName = "dev.jellystack.downloads") ?: NSUserDefaults.standardUserDefaults() }
    val playbackSettings = remember(playbackDefaults) { NSUserDefaultsSettings(playbackDefaults) }
    val downloadSettings = remember(downloadDefaults) { NSUserDefaultsSettings(downloadDefaults) }
    val progressStore = remember(playbackSettings) { SettingsPlaybackProgressStore(playbackSettings) }
    val mediaStore = remember(downloadSettings) { SettingsOfflineMediaStore(downloadSettings) }
    val queueStore = remember(downloadSettings) { SettingsOfflineDownloadQueueStore(downloadSettings) }
    val eventStore = remember(downloadSettings) { SettingsOfflinePlaybackEventStore(downloadSettings) }
    val koin = remember { JellystackDI.koin }
    val browseRepository = remember(koin) { koin.get<JellyfinBrowseRepository>() }
    val progressSyncer =
        remember(browseRepository, eventStore) {
            JellyfinOfflineProgressSyncer(
                repository = browseRepository,
                store = eventStore,
            )
        }
    val downloadManager =
        remember(mediaStore, queueStore) {
            IosOfflineDownloadManager(
                mediaStore = mediaStore,
                queueStore = queueStore,
            )
        }
    val controller =
        remember(playerEngine, progressStore, mediaStore, progressSyncer) {
            PlaybackController(
                progressStore = progressStore,
                playerEngine = playerEngine,
                offlineMediaStore = mediaStore,
                offlineSourceResolver = IosOfflinePlaybackSourceResolver(),
                offlineProgressSyncer = progressSyncer,
            )
        }
    DisposableEffect(controller, downloadManager) {
        onDispose {
            controller.release()
            downloadManager.release()
        }
    }
    JellystackRoot(
        controller = controller,
        downloadManager = downloadManager,
    )
}

private var napierConfigured = false

private fun configureLogging() {
    if (!napierConfigured) {
        Napier.base(DebugAntilog())
        napierConfigured = true
    }
}
