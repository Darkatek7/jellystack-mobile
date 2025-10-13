package dev.jellystack.app

import android.app.Application
import dev.jellystack.core.di.JellystackDI
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext

class JellystackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        JellystackDI.start {
            androidContext(this@JellystackApplication)
        }
    }
}
