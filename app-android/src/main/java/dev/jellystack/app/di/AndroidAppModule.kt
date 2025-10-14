package dev.jellystack.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.jellystack.core.jellyfin.JellyfinItemDetailStore
import dev.jellystack.core.jellyfin.JellyfinItemStore
import dev.jellystack.core.jellyfin.JellyfinLibraryStore
import dev.jellystack.core.server.ServerStore
import dev.jellystack.database.JellystackDatabase
import dev.jellystack.database.jellyfinItemDetailStore
import dev.jellystack.database.jellyfinItemStore
import dev.jellystack.database.jellyfinLibraryStore
import dev.jellystack.database.serverStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidAppModule =
    module {
        single<SqlDriver> {
            AndroidSqliteDriver(
                schema = JellystackDatabase.Schema,
                context = androidContext(),
                name = "jellystack.db",
            )
        }
        single { JellystackDatabase(get()) }
        single<ServerStore> { get<JellystackDatabase>().serverStore() }
        single<JellyfinLibraryStore> { get<JellystackDatabase>().jellyfinLibraryStore() }
        single<JellyfinItemStore> { get<JellystackDatabase>().jellyfinItemStore() }
        single<JellyfinItemDetailStore> { get<JellystackDatabase>().jellyfinItemDetailStore() }
    }
