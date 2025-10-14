package dev.jellystack.core.server

sealed interface CredentialInput {
    data class Jellyfin(
        val username: String,
        val password: String,
        val deviceId: String? = null,
    ) : CredentialInput

    data class ApiKey(
        val apiKey: String,
    ) : CredentialInput
}

sealed interface StoredCredential {
    data class Jellyfin(
        val username: String,
        val deviceId: String?,
        val accessToken: String,
        val userId: String,
    ) : StoredCredential

    data class ApiKey(
        val apiKey: String,
    ) : StoredCredential
}
