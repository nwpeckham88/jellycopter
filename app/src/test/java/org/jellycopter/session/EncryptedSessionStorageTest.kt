package org.jellycopter.session

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.session.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EncryptedSessionStorageTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var mockContext: Context
    private lateinit var storage: EncryptedSessionStorage

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        mockContext = mockk(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns fakePrefs
        storage = EncryptedSessionStorage(mockContext, fakePrefs)
    }

    @Test
    fun testSaveAndRetrieveSession() {
        val session = UserSession(
            serverUrl = "https://jellyfin.example.com",
            accessToken = "secure_token_12345",
            userId = "user_abc_789",
            username = "captain_video",
            serverName = "Main Media Server",
            serverId = "server_guid_001"
        )

        assertFalse(storage.hasSession())
        assertNull(storage.getSession())

        storage.saveSession(session)

        assertTrue(storage.hasSession())
        val retrieved = storage.getSession()
        assertNotNull(retrieved)
        assertEquals(session.serverUrl, retrieved?.serverUrl)
        assertEquals(session.accessToken, retrieved?.accessToken)
        assertEquals(session.userId, retrieved?.userId)
        assertEquals(session.username, retrieved?.username)
        assertEquals(session.serverName, retrieved?.serverName)
        assertEquals(session.serverId, retrieved?.serverId)
    }

    @Test
    fun testClearSession() {
        val session = UserSession(
            serverUrl = "http://192.168.1.50:8096",
            accessToken = "token_to_clear",
            userId = "user_1",
            username = "user"
        )

        storage.saveSession(session)
        assertTrue(storage.hasSession())

        storage.clearSession()
        assertFalse(storage.hasSession())
        assertNull(storage.getSession())
    }

    @Test
    fun testIncompleteSessionReturnsNull() {
        // Blank or missing access token
        fakePrefs.edit()
            .putString("key_server_url", "http://server:8096")
            .putString("key_user_id", "user_id_1")
            .putString("key_username", "alice")
            .apply()

        assertNull(storage.getSession())
        assertFalse(storage.hasSession())
    }

    @Test
    fun testSafeFallbackInitializationWithoutKeyStore() {
        // When EncryptedSessionStorage is constructed with only Context on JVM,
        // it catches MasterKey / KeyStore failure and safely delegates to context.getSharedPreferences
        val fallbackStorage = EncryptedSessionStorage(mockContext)

        val session = UserSession(
            serverUrl = "http://fallback.local:8096",
            accessToken = "fallback_token",
            userId = "user_fb",
            username = "fallback_user"
        )

        fallbackStorage.saveSession(session)
        val loaded = fallbackStorage.getSession()
        assertNotNull(loaded)
        assertEquals("http://fallback.local:8096", loaded?.serverUrl)
        assertEquals("fallback_token", loaded?.accessToken)
    }
}
