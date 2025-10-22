package dev.jellystack.core.di

import dev.jellystack.core.config.ServerConfigRepository
import dev.jellystack.core.jellyfin.JellyfinBrowseApiFactory
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.ServerRepositoryEnvironmentProvider
import dev.jellystack.core.jellyfin.defaultJellyfinBrowseApiFactory
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticator
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.ServerRepositoryJellyseerrEnvironmentProvider
import dev.jellystack.core.preferences.ThemePreferenceRepository
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.SecureStoreFactory
import dev.jellystack.core.security.SecureStoreFactory.Companion.DEFAULT_SECURE_STORE_NAME
import dev.jellystack.core.security.SecureStoreFactoryImpl
import dev.jellystack.core.security.SecureStoreLogger
import dev.jellystack.core.security.SecureStoreNapierLogger
import dev.jellystack.core.server.ServerConnectivity
import dev.jellystack.core.server.ServerConnectivityChecker
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

fun coreModule(): Module =
    module {
        single<SecureStoreLogger> { SecureStoreNapierLogger() }
        single<CoroutineDispatcher> { Dispatchers.Default }
        single<SecureStoreFactory> {
            SecureStoreFactoryImpl(
                engineFactory = get(),
                logger = get(),
                dispatcher = get(),
            )
        }
        single<SecureStore> {
            get<SecureStoreFactory>().create(DEFAULT_SECURE_STORE_NAME)
        }
        single { ServerConfigRepository(secureStore = get()) }
        single<ServerConnectivity> { ServerConnectivityChecker() }
        single { ServerCredentialVault(get()) }
        single { ServerRepository(store = get(), connectivity = get(), credentialVault = get()) }
        single<JellyfinEnvironmentProvider> { ServerRepositoryEnvironmentProvider(get()) }
        single<JellyseerrEnvironmentProvider> { ServerRepositoryJellyseerrEnvironmentProvider(get()) }
        single<JellyfinBrowseApiFactory> { defaultJellyfinBrowseApiFactory() }
        single { JellyfinBrowseRepository(get(), get(), get(), get(), get()) }
        single { JellyseerrRepository() }
        single { JellyseerrAuthenticator() }
        single { ThemePreferenceRepository(get()) }
    }

expect fun platformModule(): Module

fun sharedModules(): List<Module> = listOf(coreModule(), platformModule())

object JellystackDI {
    val modules: List<Module> get() = sharedModules()
    val koin by lazy { KoinPlatformTools.defaultContext().get() }

    fun isStarted(): Boolean = KoinPlatformTools.defaultContext().getOrNull() != null
}
