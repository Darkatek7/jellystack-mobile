package dev.jellystack.design

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerFormStateTest {
    @Test
    fun jellyseerrFormValidWithLinkedServer() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYSEERR,
                name = "Requests",
                baseUrl = "https://requests.local",
                jellyfinServerId = "srv-1",
            )

        assertTrue(state.isValid)
    }

    @Test
    fun jellyseerrFormInvalidWithoutLinkedServer() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYSEERR,
                baseUrl = "https://requests.local",
                jellyfinServerId = null,
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

    @Test
    fun jellyseerrFormRequiresPasswordWhenFlagged() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYSEERR,
                baseUrl = "https://requests.local",
                jellyfinServerId = "srv-1",
                requiresJellyseerrPassword = true,
            )

        assertFalse(state.isValid)
        assertTrue(state.copy(password = "secret").isValid)
    }
}
