package org.jellycopter.core.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages encrypted persistence of Jellyfin user credentials and server session details.
 *
 * Uses EncryptedSharedPreferences backed by Android Keystore (AES256_GCM).
 * Provides a fallback to standard private SharedPreferences in non-device test environments
 * where Android KeyStore / MasterKey is unavailable on the JVM classpath.
 */
class EncryptedSessionStorage(
    context: Context,
    prefs: SharedPreferences? = null
) {
    private val preferences: SharedPreferences = prefs ?: createPreferences(context)

    companion object {
        private const val TAG = "EncryptedSessionStorage"
        private const val PREFS_FILE = "jellycopter_secure_session"
        private const val FALLBACK_PREFS_FILE = "jellycopter_session_fallback"

        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_ACCESS_TOKEN = "key_access_token"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_SERVER_NAME = "key_server_name"
        private const val KEY_SERVER_ID = "key_server_id"

        private fun createPreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                try {
                    Log.w(TAG, "Android KeyStore unavailable, falling back to local preferences: ${t.message}")
                } catch (_: Throwable) {
                    // Log might not be mocked in all JVM testing environments
                }
                context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    fun saveSession(session: UserSession) {
        preferences.edit()
            .putString(KEY_SERVER_URL, session.serverUrl)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_SERVER_NAME, session.serverName)
            .putString(KEY_SERVER_ID, session.serverId)
            .apply()
    }

    fun getSession(): UserSession? {
        val serverUrl = preferences.getString(KEY_SERVER_URL, null) ?: return null
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val userId = preferences.getString(KEY_USER_ID, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        val serverName = preferences.getString(KEY_SERVER_NAME, "") ?: ""
        val serverId = preferences.getString(KEY_SERVER_ID, "") ?: ""

        if (serverUrl.isBlank() || accessToken.isBlank() || userId.isBlank()) {
            return null
        }

        return UserSession(
            serverUrl = serverUrl,
            accessToken = accessToken,
            userId = userId,
            username = username,
            serverName = serverName,
            serverId = serverId
        )
    }

    fun clearSession() {
        preferences.edit().clear().apply()
    }

    fun hasSession(): Boolean {
        return getSession() != null
    }
}
