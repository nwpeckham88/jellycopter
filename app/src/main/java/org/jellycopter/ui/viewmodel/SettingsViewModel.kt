package org.jellycopter.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.settings.SettingsRepository
import org.jellycopter.sync.PluginSyncService
import java.util.concurrent.TimeUnit

/**
 * ViewModel managing settings screen mutations and probe calls.
 * Offloads companion plugin probing and user logout to [viewModelScope]
 * so async network mutations survive UI navigation and screen teardown.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository? = null,
    private val authRepository: AuthRepository? = null,
    private val pluginSyncService: PluginSyncService? = null
) : ViewModel() {

    var pluginStatus by mutableStateOf("Checking companion plugin...")
        private set
    var isProbingPlugin by mutableStateOf(false)
        private set

    fun probePlugin(serverUrl: String? = authRepository?.getSession()?.serverUrl) {
        isProbingPlugin = true
        viewModelScope.launch {
            if (pluginSyncService != null) {
                val available = pluginSyncService.probePlugin()
                pluginStatus = if (available) "Connected (v1.0.0 Active)" else "Plugin not detected (Local fallback active)"
            } else if (!serverUrl.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
                        val pingUrl = if (serverUrl.endsWith("/")) "${serverUrl}Jellycopter/Ping" else "$serverUrl/Jellycopter/Ping"
                        val req = Request.Builder().url(pingUrl).build()
                        val resp = client.newCall(req).execute()
                        pluginStatus = if (resp.isSuccessful) "Connected (v1.0.0 Active)" else "Plugin not detected (Local fallback active)"
                    } catch (_: Exception) {
                        pluginStatus = "Plugin not detected (Local fallback active)"
                    }
                }
            } else {
                pluginStatus = "Plugin not detected (Local fallback active)"
            }
            isProbingPlugin = false
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                authRepository?.logout()
            } finally {
                onComplete()
            }
        }
    }
}
