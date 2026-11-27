package com.revibe.account

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DpapiAuthSessionStoreTest {

    @Test
    fun `session is encrypted at rest and round-trips on Windows`() = runBlocking {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return@runBlocking
        val dir = Files.createTempDirectory("revibe-session").toFile()
        val file = java.io.File(dir, "account.session")
        val store = DpapiAuthSessionStore(file)
        val session = StoredSession(
            accessToken = "access-token-value",
            refreshToken = "refresh-token-value",
            expiresAt = 1_700_000_000_000,
            user = AccountUser("user-1", "a@example.com", "Listener"),
        )

        store.save(session)

        assertTrue(file.isFile)
        val bytes = file.readBytes().decodeToString()
        assertFalse(bytes.contains("access-token-value"))
        assertFalse(bytes.contains("refresh-token-value"))

        assertEquals(session, store.load())

        store.clear()
        assertNull(store.load())
    }
}
