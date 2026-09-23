package org.jellycopter.auth

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellycopter.core.model.AuthState
import org.jellycopter.core.model.ServerInfo
import org.jellycopter.core.network.AuthenticateByNameRequest
import org.jellycopter.core.network.AuthenticateUserResult
import org.jellycopter.core.network.JellyfinApiService
import org.jellycopter.core.network.UserDto
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.repository.DefaultAuthRepository
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.session.UserSession
import org.jellycopter.session.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private lateinit var apiService: JellyfinApiService
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storage: EncryptedSessionStorage
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        apiService = mockk()
        fakePrefs = FakeSharedPreferences()
        val mockContext: android.content.Context = mockk(relaxed = true)
        storage = EncryptedSessionStorage(mockContext, fakePrefs)
        repository = DefaultAuthRepository(apiService, storage, testDispatcher)
    }

    @Test
    fun testInitialStateUnauthenticatedWhenStorageEmpty() {
        assertEquals(AuthState.Unauthenticated, repository.authState.value)
        assertNull(repository.getSession())
    }

    @Test
    fun testInitialStateAuthenticatedWhenStorageHasSession() {
        val existingSession = UserSession(
            serverUrl = "http://jellyfin.home:8096",
            accessToken = "saved_token",
            userId = "user_existing",
            username = "admin"
        )
        storage.saveSession(existingSession)

        val restoredRepo = DefaultAuthRepository(apiService, storage, testDispatcher)
        val state = restoredRepo.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals(existingSession, (state as AuthState.Authenticated).session)
        assertEquals(existingSession, restoredRepo.getSession())
    }

    @Test
    fun testUrlNormalization() {
        assertEquals("http://192.168.1.100:8096", DefaultAuthRepository.normalizeServerUrl("192.168.1.100:8096"))
        assertEquals("http://192.168.1.100:8096", DefaultAuthRepository.normalizeServerUrl("  http://192.168.1.100:8096/  "))
        assertEquals("https://jellyfin.example.com", DefaultAuthRepository.normalizeServerUrl("https://jellyfin.example.com/"))
    }

    @Test
    fun testProbeServerSuccess() = runTest(testDispatcher) {
        val serverInfo = ServerInfo(serverName = "Gold-Mock", version = "10.11.11", id = "mock_001")
        coEvery {
            apiService.getPublicSystemInfoByUrl("http://jellyfin.local:8096/System/Info/Public")
        } returns Response.success(serverInfo)

        val result = repository.probeServer("jellyfin.local:8096")
        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertNotNull(info)
        assertEquals("Gold-Mock", info?.serverName)
        assertEquals("10.11.11", info?.version)
        assertEquals("mock_001", info?.id)
    }

    @Test
    fun testProbeServerFailure() = runTest(testDispatcher) {
        coEvery {
            apiService.getPublicSystemInfoByUrl("http://badserver.local/System/Info/Public")
        } throws IOException("Connection refused")

        val result = repository.probeServer("badserver.local")
        assertTrue(result.isFailure)
    }

    @Test
    fun testProbeServerEmptyUrl() = runTest(testDispatcher) {
        val result = repository.probeServer("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testAuthenticateSuccess() = runTest(testDispatcher) {
        val serverInfo = ServerInfo(serverName = "MockServer", version = "10.8.0", id = "srv_999")
        coEvery {
            apiService.getPublicSystemInfoByUrl("http://jellyfin.home:8096/System/Info/Public")
        } returns Response.success(serverInfo)

        val authResult = AuthenticateUserResult(
            user = UserDto(id = "uid_456", name = "Nathan", serverId = "srv_999"),
            accessToken = "token_xyz_789",
            serverId = "srv_999"
        )
        coEvery {
            apiService.authenticateByNameByUrl(
                "http://jellyfin.home:8096/Users/AuthenticateByName",
                any(),
                AuthenticateByNameRequest(username = "Nathan", pw = "secret123")
            )
        } returns Response.success(authResult)

        val result = repository.authenticate("jellyfin.home:8096", "Nathan", "secret123")
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("http://jellyfin.home:8096", session?.serverUrl)
        assertEquals("token_xyz_789", session?.accessToken)
        assertEquals("uid_456", session?.userId)
        assertEquals("Nathan", session?.username)
        assertEquals("MockServer", session?.serverName)
        assertEquals("srv_999", session?.serverId)

        // Verify storage updated
        assertEquals(session, storage.getSession())

        // Verify authState updated
        val state = repository.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals(session, (state as AuthState.Authenticated).session)
    }

    @Test
    fun testAuthenticateInvalidCredentials() = runTest(testDispatcher) {
        coEvery {
            apiService.getPublicSystemInfoByUrl(any())
        } returns Response.success(ServerInfo())

        val errorResponse = Response.error<AuthenticateUserResult>(
            401,
            """{"message": "Invalid username or password"}""".toResponseBody("application/json".toMediaType())
        )
        coEvery {
            apiService.authenticateByNameByUrl(any(), any(), any())
        } returns errorResponse

        val result = repository.authenticate("http://jellyfin.home:8096", "baduser", "wrongpassword")
        assertTrue(result.isFailure)

        val state = repository.authState.value
        assertTrue(state is AuthState.Error)
        assertEquals("Invalid username or password", (state as AuthState.Error).message)
        assertNull(storage.getSession())
    }

    @Test
    fun testAuthenticateMissingTokenInPayload() = runTest(testDispatcher) {
        coEvery {
            apiService.getPublicSystemInfoByUrl(any())
        } returns Response.success(ServerInfo())

        // Missing accessToken
        val malformedResult = AuthenticateUserResult(
            user = UserDto(id = "uid_123", name = "Test"),
            accessToken = null
        )
        coEvery {
            apiService.authenticateByNameByUrl(any(), any(), any())
        } returns Response.success(malformedResult)

        val result = repository.authenticate("http://server:8096", "user", "pass")
        assertTrue(result.isFailure)
        assertTrue(repository.authState.value is AuthState.Error)
    }

    @Test
    fun testLogout() = runTest(testDispatcher) {
        val session = UserSession(
            serverUrl = "http://jellyfin.home:8096",
            accessToken = "token_abc",
            userId = "user_1",
            username = "user"
        )
        storage.saveSession(session)

        val repo = DefaultAuthRepository(apiService, storage, testDispatcher)
        assertTrue(repo.authState.value is AuthState.Authenticated)

        repo.logout()
        assertEquals(AuthState.Unauthenticated, repo.authState.value)
        assertNull(storage.getSession())
    }
}
