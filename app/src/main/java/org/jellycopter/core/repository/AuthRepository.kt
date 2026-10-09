package org.jellycopter.core.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jellycopter.core.model.AuthState
import org.jellycopter.core.model.ServerInfo
import org.jellycopter.core.network.AuthenticateByNameRequest
import org.jellycopter.core.network.JellyfinApiService
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.session.UserSession

/**
 * Repository for Jellyfin server probing, authentication, and session management.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun probeServer(serverUrl: String): Result<ServerInfo>
    suspend fun authenticate(serverUrl: String, username: String, password: String): Result<UserSession>
    suspend fun logout()
    fun getSession(): UserSession?
}

/**
 * Default implementation of [AuthRepository].
 */
class DefaultAuthRepository(
    private val apiService: JellyfinApiService,
    private val sessionStorage: EncryptedSessionStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(
        sessionStorage.getSession()?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        fun normalizeServerUrl(url: String): String {
            var trimmed = url.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed = "http://$trimmed"
            }
            return trimmed.removeSuffix("/")
        }
    }

    override suspend fun probeServer(serverUrl: String): Result<ServerInfo> = withContext(ioDispatcher) {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL cannot be empty"))
        }
        val normalized = normalizeServerUrl(trimmed)
        val probeUrl = "$normalized/System/Info/Public"

        try {
            val response = apiService.getPublicSystemInfoByUrl(probeUrl)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Server returned HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String
    ): Result<UserSession> = withContext(ioDispatcher) {
        val trimmedUrl = serverUrl.trim()
        val trimmedUser = username.trim()

        if (trimmedUrl.isBlank()) {
            val err = "Server URL cannot be empty"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        if (trimmedUser.isBlank()) {
            val err = "Username cannot be empty"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val normalized = normalizeServerUrl(trimmedUrl)
        android.util.Log.d("AuthRepository", "Starting authenticate for '$trimmedUser' at $normalized")

        val serverInfo = probeServer(normalized).getOrNull()

        val authUrl = "$normalized/Users/AuthenticateByName"
        val header = JellyfinApiService.buildEmbyAuthHeader()
        val request = AuthenticateByNameRequest(username = trimmedUser, pw = password)

        try {
            android.util.Log.d("AuthRepository", "Calling authenticateByNameByUrl at $authUrl...")
            val response = apiService.authenticateByNameByUrl(authUrl, header, header, request)
            android.util.Log.d("AuthRepository", "Authenticate response code: ${response.code()}")
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val token = body.accessToken
                val userId = body.user?.id
                if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                    val msg = "Server returned invalid authentication payload (missing token or userId)"
                    _authState.value = AuthState.Error(msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                val resolvedUsername = body.user?.name?.ifBlank { trimmedUser } ?: trimmedUser
                val resolvedServerName = serverInfo?.serverName?.ifBlank { "" } ?: ""
                val resolvedServerId = serverInfo?.id?.ifBlank { body.serverId ?: body.user?.serverId ?: "" }
                    ?: body.serverId ?: body.user?.serverId ?: ""

                val session = UserSession(
                    serverUrl = normalized,
                    accessToken = token,
                    userId = userId,
                    username = resolvedUsername,
                    serverName = resolvedServerName,
                    serverId = resolvedServerId
                )

                sessionStorage.saveSession(session)
                _authState.value = AuthState.Authenticated(session)
                android.util.Log.d("AuthRepository", "Session saved! AuthState set to Authenticated.")
                Result.success(session)
            } else {
                val errorMsg = if (response.code() == 401) {
                    "Invalid username or password"
                } else {
                    "Authentication failed: HTTP ${response.code()}"
                }
                _authState.value = AuthState.Error(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Exception during authenticate: ${e.message}", e)
            val errorMsg = e.localizedMessage ?: "Connection error during authentication"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    override suspend fun logout() = withContext(ioDispatcher) {
        sessionStorage.clearSession()
        _authState.value = AuthState.Unauthenticated
    }

    override fun getSession(): UserSession? {
        return sessionStorage.getSession()
    }
}
