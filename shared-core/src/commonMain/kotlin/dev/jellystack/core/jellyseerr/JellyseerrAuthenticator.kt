package dev.jellystack.core.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyseerr.JellyseerrApi
import dev.jellystack.network.jellyseerr.JellyseerrLocalLoginPayload
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies

open class JellyseerrAuthenticator {
    open suspend fun authenticate(request: JellyseerrAuthRequest): JellyseerrAuthenticationResult {
        val client =
            NetworkClientFactory.create(
                ClientConfig(
                    installLogging = false,
                    configure = {
                        install(HttpCookies)
                    },
                ),
            )
        return client.use { httpClient ->
            performAuthentication(httpClient, request)
        }
    }

    private suspend fun performAuthentication(
        client: HttpClient,
        request: JellyseerrAuthRequest,
    ): JellyseerrAuthenticationResult {
        val api =
            JellyseerrApi.create(
                baseUrl = request.baseUrl,
                apiKey = null,
                client = client,
            )
        val user =
            api.loginWithCredentials(
                JellyseerrLocalLoginPayload(
                    email = request.email,
                    password = request.password,
                ),
            )
        val cookies = client.cookies(request.baseUrl)
        val cookieHeader =
            cookies
                .filter { it.name.isNotBlank() && it.value.isNotBlank() }
                .joinToString(separator = "; ") { cookie -> "${cookie.name}=${cookie.value}" }
                .takeIf { it.isNotBlank() }
        val userApiKey = user.apiKey?.takeIf { it.isNotBlank() }
        val resolvedCookie = cookieHeader
        if (userApiKey.isNullOrBlank() && resolvedCookie == null) {
            throw JellyseerrAuthenticationException("Jellyseerr server did not return a session cookie or API key.")
        }
        return JellyseerrAuthenticationResult(
            apiKey = userApiKey,
            userId = user.id,
            sessionCookie = resolvedCookie,
        )
    }
}

data class JellyseerrAuthRequest(
    val baseUrl: String,
    val email: String,
    val password: String,
)

data class JellyseerrAuthenticationResult(
    val apiKey: String?,
    val userId: Int?,
    val sessionCookie: String?,
)

class JellyseerrAuthenticationException(
    message: String,
    cause: Throwable? = null,
    val reason: Reason = Reason.UNKNOWN,
) : IllegalStateException(message, cause) {
    enum class Reason {
        UNKNOWN,
        SERVER_NOT_FOUND,
        INVALID_LINKED_SERVER,
        MISSING_JELLYFIN_PASSWORD,
    }
}

private inline fun <T> HttpClient.use(block: (HttpClient) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
