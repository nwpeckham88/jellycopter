package org.jellycopter.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jellycopter.core.repository.AuthRepository

/**
 * ViewModel managing authentication state and network mutations.
 * Offloads server probing and credential authentication to [viewModelScope]
 * to prevent early coroutine cancellations during UI navigation or recomposition.
 */
class LoginViewModel(
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    var serverUrl by mutableStateOf(authRepository?.getSession()?.serverUrl ?: "")
    var username by mutableStateOf(authRepository?.getSession()?.username ?: "")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var serverProbeResult by mutableStateOf<String?>(null)
        private set

    fun clearErrors() {
        errorMessage = null
        serverProbeResult = null
    }

    fun performLogin(onSuccess: () -> Unit = {}) {
        if (serverUrl.isBlank()) {
            errorMessage = "Please enter a server URL"
            return
        }
        if (username.isBlank()) {
            errorMessage = "Please enter a username"
            return
        }
        val repo = authRepository
        if (repo == null) {
            onSuccess()
            return
        }

        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val probe = repo.probeServer(serverUrl)
                probe.onSuccess { info ->
                    serverProbeResult = "Found: ${info.serverName ?: "Jellyfin"} (${info.version ?: "Unknown"})"
                }

                val result = repo.authenticate(serverUrl, username, password)
                isLoading = false
                result.onSuccess {
                    onSuccess()
                }.onFailure { err ->
                    errorMessage = err.message ?: "Authentication failed"
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.message ?: "Authentication failed"
            }
        }
    }
}
