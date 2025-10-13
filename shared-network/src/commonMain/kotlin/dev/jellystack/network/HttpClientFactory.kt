package dev.jellystack.network

import dev.jellystack.core.currentPlatform
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun platformEngine(): HttpClientEngineFactory<*>

fun buildHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient {
    val platform = currentPlatform()
    return HttpClient(platformEngine()) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
        println("Creating HTTP client for ${platform.name}")
        configure()
    }
}
