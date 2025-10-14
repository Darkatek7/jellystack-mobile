package dev.jellystack.core.logging

import io.github.aakira.napier.Napier

actual object JellystackLog {
    private const val TAG = "Jellystack"

    actual fun d(message: String) {
        Napier.d(message, tag = TAG)
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        Napier.e(message, tag = TAG, throwable = throwable)
    }
}
