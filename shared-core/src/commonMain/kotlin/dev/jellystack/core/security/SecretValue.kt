package dev.jellystack.core.security

value class SecretValue internal constructor(
    private val raw: String,
) {
    fun reveal(): String = raw

    override fun toString(): String = REDACTED

    companion object {
        private const val REDACTED = "██"
    }
}

fun secretValue(raw: String): SecretValue = SecretValue(raw)
