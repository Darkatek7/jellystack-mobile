package dev.jellystack.core.di

import android.content.Context
import dev.jellystack.core.security.SecureStoreEngine
import dev.jellystack.core.security.SecureStoreEngineFactory
import dev.jellystack.core.security.android.AndroidSecureStoreEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() =
    module {
        single<SecureStoreEngineFactory> {
            AndroidSecureStoreEngineFactory(androidContext())
        }
    }

private class AndroidSecureStoreEngineFactory(
    private val context: Context,
) : SecureStoreEngineFactory {
    override fun create(name: String): SecureStoreEngine = AndroidSecureStoreEngine(context, name)
}
