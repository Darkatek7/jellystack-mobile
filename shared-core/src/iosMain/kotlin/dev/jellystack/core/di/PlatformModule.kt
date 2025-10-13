package dev.jellystack.core.di

import dev.jellystack.core.security.SecureStoreEngine
import dev.jellystack.core.security.SecureStoreEngineFactory
import dev.jellystack.core.security.ios.IosSecureStoreEngine
import org.koin.dsl.module

actual fun platformModule() =
    module {
        single<SecureStoreEngineFactory> { IosSecureStoreEngineFactory() }
    }

private class IosSecureStoreEngineFactory : SecureStoreEngineFactory {
    override fun create(name: String): SecureStoreEngine = IosSecureStoreEngine(name)
}
