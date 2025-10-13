package dev.jellystack.core.di

import dev.jellystack.core.config.ServerConfigRepository
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.SecureStoreFactory
import dev.jellystack.core.security.SecureStoreFactory.Companion.DEFAULT_SECURE_STORE_NAME
import dev.jellystack.core.security.SecureStoreFactoryImpl
import dev.jellystack.core.security.SecureStoreLogger
import dev.jellystack.core.security.SecureStoreNapierLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.dsl.module

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
    }

expect fun platformModule(): Module

fun sharedModules(): List<Module> = listOf(coreModule(), platformModule())

object JellystackDI {
    val modules: List<Module> get() = sharedModules()
    val koin by lazy { GlobalContext.get() }
    fun isStarted(): Boolean = GlobalContext.getOrNull() != null
}
