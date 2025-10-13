package dev.jellystack.ios

import androidx.compose.runtime.Composable
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.design.JellystackRoot
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
fun ComposeEntry() {
    configureLogging()
    if (!JellystackDI.isStarted()) {
        startKoin {
            modules(JellystackDI.modules)
        }
    }
    JellystackRoot()
}

private var napierConfigured = false

private fun configureLogging() {
    if (!napierConfigured) {
        Napier.base(DebugAntilog())
        napierConfigured = true
    }
}
