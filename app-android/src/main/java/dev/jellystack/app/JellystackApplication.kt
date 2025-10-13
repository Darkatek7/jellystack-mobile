package dev.jellystack.app

import android.app.Application
import dev.jellystack.core.di.JellystackDI
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JellystackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        if (!JellystackDI.isStarted()) {
            startKoin {
                androidContext(this@JellystackApplication)
                modules(JellystackDI.modules)
            }
        }
    }
}
