package dev.jellystack.design

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerFormStateTest {
    @Test
    fun jellyseerrFormValidWithCredentials() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYSEERR,
                baseUrl = "https://requests.local",
                email = "user@example.com",
                password = "secret",
            )

        assertTrue(state.isValid)
    }

    @Test
    fun jellyseerrFormInvalidWithoutEmail() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYSEERR,
                baseUrl = "https://requests.local",
                password = "secret",
            )

        assertFalse(state.isValid)
    }

    @Test
    fun jellyfinFormStillRequiresPassword() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYFIN,
                name = "Media",
                baseUrl = "https://media.local",
                username = "demo",
            )

        assertFalse(state.isValid)
    }
}
