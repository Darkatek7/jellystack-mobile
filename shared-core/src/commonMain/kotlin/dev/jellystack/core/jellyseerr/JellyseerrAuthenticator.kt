package dev.jellystack.core.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyseerr.JellyseerrApi
import dev.jellystack.network.jellyseerr.JellyseerrJellyfinLoginPayload
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
            when (request.method) {
                JellyseerrAuthRequest.Method.LOCAL -> {
                    val email =
                        request.email?.takeIf { it.isNotBlank() }
                            ?: throw JellyseerrAuthenticationException(
                                message = "Email is required for Jellyseerr account login.",
                                reason = JellyseerrAuthenticationException.Reason.MISSING_EMAIL,
                            )
                    api.loginWithCredentials(
                        JellyseerrLocalLoginPayload(
                            email = email,
                            password = request.password,
                        ),
                    )
                }
                JellyseerrAuthRequest.Method.JELLYFIN -> {
                    val username =
                        request.username?.takeIf { it.isNotBlank() }
                            ?: throw JellyseerrAuthenticationException(
                                message = "Username is required for Jellyfin login.",
                                reason = JellyseerrAuthenticationException.Reason.MISSING_JELLYFIN_USERNAME,
                            )
                    api.loginWithJellyfin(
                        JellyseerrJellyfinLoginPayload(
                            username = username,
                            password = request.password,
                        ),
                    )
                }
            }
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
    val method: Method,
    val email: String? = null,
    val username: String? = null,
    val password: String,
) {
    enum class Method {
        LOCAL,
        JELLYFIN,
    }
}

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
        MISSING_EMAIL,
        MISSING_JELLYFIN_USERNAME,
    }
}

private inline fun <T> HttpClient.use(block: (HttpClient) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
