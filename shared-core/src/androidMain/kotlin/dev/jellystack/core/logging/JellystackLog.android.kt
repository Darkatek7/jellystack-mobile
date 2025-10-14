package dev.jellystack.core.logging

import android.util.Log

actual object JellystackLog {
    private const val TAG = "Jellystack"

    actual fun d(message: String) {
        runCatching { Log.d(TAG, message) }
            .onFailure { println("$TAG D: $message") }
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        runCatching {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }.onFailure {
            if (throwable != null) {
                println("$TAG E: $message\n${throwable.stackTraceToString()}")
            } else {
                println("$TAG E: $message")
            }
        }
    }
}
