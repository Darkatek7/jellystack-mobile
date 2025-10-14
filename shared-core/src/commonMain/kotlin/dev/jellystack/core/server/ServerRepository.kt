package dev.jellystack.core.server

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ServerRepository(
    private val store: ServerStore,
    private val connectivity: ServerConnectivity,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { randomId(16) },
) {
    private val mutex = Mutex()
    private val servers = MutableStateFlow<List<ManagedServer>>(emptyList())

    init {
        servers.value = runBlocking { loadServers() }
    }

    fun observeServers(): StateFlow<List<ManagedServer>> = servers.asStateFlow()

    suspend fun register(request: ServerRegistration): ManagedServer =
        mutex.withLock {
            validate(request)
            val normalizedUrl = normalizeBaseUrl(request.baseUrl)
            val existing = store.findByTypeAndUrl(request.type, normalizedUrl)
            if (existing != null && existing.id != request.id) {
                throw DuplicateServerException(existing.id, request.type, normalizedUrl)
            }

            val normalizedRequest = request.copy(baseUrl = normalizedUrl)
            when (val result = connectivity.test(normalizedRequest)) {
                is ConnectivityResult.Failure ->
                    throw ConnectivityException(result.message, result.cause)
                is ConnectivityResult.Success -> {
                    val now = clock.now()
                    val id = request.id ?: existing?.id ?: idGenerator()
                    val record =
                        toRecord(
                            id = id,
                            registration = normalizedRequest,
                            credential = result.credentials,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                        )
                    store.upsert(record)
                    refreshServers()
                    record.toManagedServer()
                }
            }
        }

    suspend fun remove(id: String) {
        mutex.withLock {
            store.delete(id)
            refreshServers()
        }
    }

    fun currentServers(): List<ManagedServer> = servers.value

    private suspend fun refreshServers() {
        servers.value = loadServers()
    }

    private suspend fun loadServers(): List<ManagedServer> {
        val records = store.list()
        if (records.isEmpty()) {
            return emptyList()
        }

        val validServers = mutableListOf<ManagedServer>()
        records.forEach { record ->
            val managed =
                runCatching { record.toManagedServer() }
                    .onFailure { error ->
                        Napier.e(
                            message = "Dropping corrupted server entry ${record.id}",
                            throwable = error,
                        )
                        runCatching { store.delete(record.id) }
                            .onFailure { cleanupError ->
                                Napier.e(
                                    message = "Failed to remove corrupted server entry ${record.id}",
                                    throwable = cleanupError,
                                )
                            }
                    }.getOrNull()
            if (managed != null) {
                validServers += managed
            }
        }
        return validServers
    }

    private fun validate(request: ServerRegistration) {
        if (request.name.isBlank()) {
            throw InvalidServerConfiguration("Server name cannot be blank")
        }
        when (val creds = request.credentials) {
            is CredentialInput.Jellyfin -> {
                if (creds.username.isBlank()) {
                    throw InvalidServerConfiguration("Jellyfin username cannot be blank")
                }
                if (creds.password.isBlank()) {
                    throw InvalidServerConfiguration("Jellyfin password cannot be blank")
                }
            }
            is CredentialInput.ApiKey ->
                if (creds.apiKey.isBlank()) {
                    throw InvalidServerConfiguration("API key cannot be blank")
                }
        }
    }

    private fun toRecord(
        id: String,
        registration: ServerRegistration,
        credential: StoredCredential,
        createdAt: Instant,
        updatedAt: Instant,
    ): ServerRecord =
        when (credential) {
            is StoredCredential.Jellyfin ->
                ServerRecord(
                    id = id,
                    type = registration.type,
                    name = registration.name.trim(),
                    baseUrl = registration.baseUrl,
                    username = credential.username,
                    deviceId = credential.deviceId,
                    apiKey = null,
                    accessToken = credential.accessToken,
                    userId = credential.userId,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            is StoredCredential.ApiKey ->
                ServerRecord(
                    id = id,
                    type = registration.type,
                    name = registration.name.trim(),
                    baseUrl = registration.baseUrl,
                    username = null,
                    deviceId = null,
                    apiKey = credential.apiKey,
                    accessToken = null,
                    userId = null,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
        }

    private fun ServerRecord.toManagedServer(): ManagedServer {
        val storedCredential =
            when (type) {
                ServerType.JELLYFIN ->
                    StoredCredential.Jellyfin(
                        username = username ?: throw IllegalStateException("Missing username"),
                        deviceId = deviceId,
                        accessToken = accessToken ?: throw IllegalStateException("Missing access token"),
                        userId = userId ?: throw IllegalStateException("Missing user id"),
                    )
                ServerType.SONARR,
                ServerType.RADARR,
                ServerType.JELLYSEERR,
                -> StoredCredential.ApiKey(apiKey ?: throw IllegalStateException("Missing api key"))
            }

        return ManagedServer(
            id = id,
            type = type,
            name = name,
            baseUrl = baseUrl,
            credentials = storedCredential,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
