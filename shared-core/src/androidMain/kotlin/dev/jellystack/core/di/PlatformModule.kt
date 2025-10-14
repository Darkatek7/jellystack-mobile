package dev.jellystack.core.di

import android.content.Context
import dev.jellystack.core.security.SecureStoreEngineFactory
import dev.jellystack.core.security.android.AndroidSecureStoreEngine
import dev.jellystack.core.security.android.PlaintextSecureStoreEngine
import io.github.aakira.napier.Napier
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
    override fun create(name: String) =
        runCatching { AndroidSecureStoreEngine(context, name) }
            .getOrElse { error ->
                Napier.e(
                    tag = "SecureStore",
                    throwable = error,
                ) { "Encrypted storage initialization failed, falling back to plaintext store." }
                PlaintextSecureStoreEngine(context, name)
            }
}
