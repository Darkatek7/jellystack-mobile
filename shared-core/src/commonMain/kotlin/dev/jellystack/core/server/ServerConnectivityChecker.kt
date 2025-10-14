package dev.jellystack.core.server

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.generated.jellyfin.AuthenticateByNameRequest
import dev.jellystack.network.generated.jellyfin.JellyfinAuthApi
import dev.jellystack.network.generated.jellyseerr.JellyseerrStatusApi
import dev.jellystack.network.generated.radarr.RadarrSystemApi
import dev.jellystack.network.generated.sonarr.SonarrSystemApi
import io.ktor.client.HttpClient

class ServerConnectivityChecker(
    private val clientFactory: (ClientConfig) -> HttpClient = { config -> NetworkClientFactory.create(config) },
    private val idGenerator: () -> String = { randomId() },
) : ServerConnectivity {
    override suspend fun test(registration: ServerRegistration): ConnectivityResult =
        when (registration.type) {
            ServerType.JELLYFIN -> authenticateJellyfin(registration)
            ServerType.SONARR -> pingSonarr(registration)
            ServerType.RADARR -> pingRadarr(registration)
            ServerType.JELLYSEERR -> pingJellyseerr(registration)
        }

    private suspend fun authenticateJellyfin(registration: ServerRegistration): ConnectivityResult {
        val creds =
            registration.credentials as? CredentialInput.Jellyfin
                ?: return ConnectivityResult.Failure("Jellyfin credentials are required")
        val client = clientFactory(ClientConfig(installLogging = false))
        return try {
            val api = JellyfinAuthApi(client, registration.baseUrl)
            val deviceId = creds.deviceId?.takeIf { it.isNotBlank() } ?: "jellystack-${idGenerator()}"
            val response =
                api.authenticateByName(
                    AuthenticateByNameRequest(
                        username = creds.username,
                        password = creds.password,
                        deviceId = deviceId,
                    ),
                )
            ConnectivityResult.Success(
                message = "Authenticated as ${response.user.name ?: response.user.id}",
                credentials =
                    StoredCredential.Jellyfin(
                        username = creds.username,
                        deviceId = deviceId,
                        accessToken = response.accessToken,
                        userId = response.user.id,
                    ),
            )
        } catch (t: Throwable) {
            ConnectivityResult.Failure("Jellyfin authentication failed", t)
        } finally {
            client.close()
        }
    }

    private suspend fun pingSonarr(registration: ServerRegistration): ConnectivityResult {
        val creds =
            registration.credentials as? CredentialInput.ApiKey
                ?: return ConnectivityResult.Failure("Sonarr API key is required")
        val client = clientFactory(ClientConfig(installLogging = false))
        return try {
            val api = SonarrSystemApi(client, registration.baseUrl, creds.apiKey)
            val status = api.fetchSystemStatus()
            ConnectivityResult.Success(
                message = "Connected to ${status.appName} ${status.version}",
                credentials = StoredCredential.ApiKey(creds.apiKey),
            )
        } catch (t: Throwable) {
            ConnectivityResult.Failure("Sonarr connectivity failed", t)
        } finally {
            client.close()
        }
    }

    private suspend fun pingRadarr(registration: ServerRegistration): ConnectivityResult {
        val creds =
            registration.credentials as? CredentialInput.ApiKey
                ?: return ConnectivityResult.Failure("Radarr API key is required")
        val client = clientFactory(ClientConfig(installLogging = false))
        return try {
            val api = RadarrSystemApi(client, registration.baseUrl, creds.apiKey)
            val status = api.fetchSystemStatus()
            ConnectivityResult.Success(
                message = "Connected to ${status.appName} ${status.version}",
                credentials = StoredCredential.ApiKey(creds.apiKey),
            )
        } catch (t: Throwable) {
            ConnectivityResult.Failure("Radarr connectivity failed", t)
        } finally {
            client.close()
        }
    }

    private suspend fun pingJellyseerr(registration: ServerRegistration): ConnectivityResult {
        val creds =
            registration.credentials as? CredentialInput.ApiKey
                ?: return ConnectivityResult.Failure("Jellyseerr API key is required")
        val client = clientFactory(ClientConfig(installLogging = false))
        return try {
            val api = JellyseerrStatusApi(client, registration.baseUrl, creds.apiKey)
            val status = api.fetchStatus()
            ConnectivityResult.Success(
                message = "Connected to Jellyseerr ${status.version}",
                credentials = StoredCredential.ApiKey(creds.apiKey),
            )
        } catch (t: Throwable) {
            ConnectivityResult.Failure("Jellyseerr connectivity failed", t)
        } finally {
            client.close()
        }
    }
}
