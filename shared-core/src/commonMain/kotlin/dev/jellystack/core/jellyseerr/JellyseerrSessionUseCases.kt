package dev.jellystack.core.jellyseerr

class SaveJellyseerrSessionUseCase(
    private val repository: JellyseerrSessionRepository,
) {
    suspend operator fun invoke(
        serverId: String,
        secrets: JellyseerrSessionSecrets,
    ) {
        repository.save(serverId, secrets)
    }
}

class LoadJellyseerrSessionUseCase(
    private val repository: JellyseerrSessionRepository,
) {
    suspend operator fun invoke(serverId: String): JellyseerrSessionSecrets? = repository.read(serverId)
}

class ClearJellyseerrSessionUseCase(
    private val repository: JellyseerrSessionRepository,
) {
    suspend operator fun invoke(serverId: String) {
        repository.clear(serverId)
    }
}
