package dev.jellystack.core.security.android

import android.content.Context
import dev.jellystack.core.security.SecureStoreEngine

/**
 * Plaintext fallback used when encrypted storage cannot be initialized.
 * This is intended as a best-effort safety net to keep the app operational on
 * devices where [AndroidSecureStoreEngine] fails to boot.
 */
internal class PlaintextSecureStoreEngine(
    context: Context,
    name: String,
) : SecureStoreEngine {
    private val preferences = context.getSharedPreferences("${name}_fallback", Context.MODE_PRIVATE)

    override fun write(
        key: String,
        value: String,
    ) {
        val success =
            preferences
                .edit()
                .putString(key, value)
                .commit()
        if (!success) {
            throw IllegalStateException("Failed to persist plaintext entry for key '$key'.")
        }
    }

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun delete(key: String) {
        val success =
            preferences
                .edit()
                .remove(key)
                .commit()
        if (!success) {
            throw IllegalStateException("Failed to remove plaintext entry for key '$key'.")
        }
    }
}
