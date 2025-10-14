package dev.jellystack.core.logging

expect object JellystackLog {
    fun d(message: String)

    fun e(
        message: String,
        throwable: Throwable? = null,
    )
}
