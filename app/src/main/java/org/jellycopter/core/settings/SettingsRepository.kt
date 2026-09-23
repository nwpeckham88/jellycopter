package org.jellycopter.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellycopter.ui.theme.BaseThemeMode

/**
 * Audio DSP configuration defaults.
 */
data class AudioDspSettings(
    val nightModeDrcEnabled: Boolean = false,
    val forceStereoEnabled: Boolean = false,
    val centerBoostDb: Float = 3.0f
)

/**
 * Subtitle Dimming configuration defaults.
 */
data class SubtitleSettings(
    val subtitleDimmerEnabled: Boolean = false,
    val subtitleDimRatio: Float = 0.5f
)

/**
 * Auto Night Mode quiet hours configuration defaults.
 */
data class AutoNightSettings(
    val autoNightModeEnabled: Boolean = false,
    val quietHoursStart: String = "22:30",
    val quietHoursEnd: String = "07:00"
)

/**
 * Repository interface for user preferences, audio DSP defaults, and integration settings.
 */
interface SettingsRepository {
    val themeModeFlow: StateFlow<BaseThemeMode>
    val audioDspSettingsFlow: StateFlow<AudioDspSettings>
    val subtitleSettingsFlow: StateFlow<SubtitleSettings>
    val autoNightSettingsFlow: StateFlow<AutoNightSettings>
    val jellyseerrUrlFlow: StateFlow<String?>

    fun getThemeMode(): BaseThemeMode
    fun setThemeMode(mode: BaseThemeMode)

    fun getAudioDspSettings(): AudioDspSettings
    fun setAudioDspSettings(settings: AudioDspSettings)
    fun setNightModeDrcEnabled(enabled: Boolean)
    fun setForceStereoEnabled(enabled: Boolean)
    fun setCenterBoostDb(boostDb: Float)

    fun getSubtitleSettings(): SubtitleSettings
    fun setSubtitleSettings(settings: SubtitleSettings)
    fun setSubtitleDimmerEnabled(enabled: Boolean)
    fun setSubtitleDimRatio(ratio: Float)

    fun getAutoNightSettings(): AutoNightSettings
    fun setAutoNightSettings(settings: AutoNightSettings)
    fun setAutoNightModeEnabled(enabled: Boolean)
    fun setQuietHours(start: String, end: String)

    fun getJellyseerrUrl(): String?
    fun setJellyseerrUrl(url: String?)
}

/**
 * SharedPreferences-backed implementation of [SettingsRepository].
 */
class SharedPreferencesSettingsRepository(
    context: Context,
    prefs: SharedPreferences? = null
) : SettingsRepository {

    private val preferences: SharedPreferences = prefs
        ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeModeFlow = MutableStateFlow(readThemeMode())
    override val themeModeFlow: StateFlow<BaseThemeMode> = _themeModeFlow.asStateFlow()

    private val _audioDspSettingsFlow = MutableStateFlow(readAudioDspSettings())
    override val audioDspSettingsFlow: StateFlow<AudioDspSettings> = _audioDspSettingsFlow.asStateFlow()

    private val _subtitleSettingsFlow = MutableStateFlow(readSubtitleSettings())
    override val subtitleSettingsFlow: StateFlow<SubtitleSettings> = _subtitleSettingsFlow.asStateFlow()

    private val _autoNightSettingsFlow = MutableStateFlow(readAutoNightSettings())
    override val autoNightSettingsFlow: StateFlow<AutoNightSettings> = _autoNightSettingsFlow.asStateFlow()

    private val _jellyseerrUrlFlow = MutableStateFlow(readJellyseerrUrl())
    override val jellyseerrUrlFlow: StateFlow<String?> = _jellyseerrUrlFlow.asStateFlow()

    companion object {
        private const val PREFS_NAME = "jellycopter_settings"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_NIGHT_MODE_DRC = "key_night_mode_drc"
        private const val KEY_FORCE_STEREO = "key_force_stereo"
        private const val KEY_CENTER_BOOST_DB = "key_center_boost_db"
        private const val KEY_SUBTITLE_DIMMER_ENABLED = "key_subtitle_dimmer_enabled"
        private const val KEY_SUBTITLE_DIM_RATIO = "key_subtitle_dim_ratio"
        private const val KEY_AUTO_NIGHT_MODE_ENABLED = "key_auto_night_mode_enabled"
        private const val KEY_QUIET_HOURS_START = "key_quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "key_quiet_hours_end"
        private const val KEY_JELLYSEERR_URL = "key_jellyseerr_url"

        val DEFAULT_THEME = BaseThemeMode.WARM_DARK
        const val DEFAULT_NIGHT_MODE_DRC = false
        const val DEFAULT_FORCE_STEREO = false
        const val DEFAULT_CENTER_BOOST_DB = 3.0f
        const val DEFAULT_SUBTITLE_DIMMER = false
        const val DEFAULT_SUBTITLE_DIM_RATIO = 0.5f
        const val DEFAULT_AUTO_NIGHT_MODE = false
        const val DEFAULT_QUIET_HOURS_START = "22:30"
        const val DEFAULT_QUIET_HOURS_END = "07:00"
    }

    private fun readThemeMode(): BaseThemeMode {
        val name = preferences.getString(KEY_THEME_MODE, DEFAULT_THEME.name) ?: DEFAULT_THEME.name
        return try {
            BaseThemeMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            DEFAULT_THEME
        }
    }

    private fun readAudioDspSettings(): AudioDspSettings {
        val drc = preferences.getBoolean(KEY_NIGHT_MODE_DRC, DEFAULT_NIGHT_MODE_DRC)
        val forceStereo = preferences.getBoolean(KEY_FORCE_STEREO, DEFAULT_FORCE_STEREO)
        val centerBoost = preferences.getFloat(KEY_CENTER_BOOST_DB, DEFAULT_CENTER_BOOST_DB)
        return AudioDspSettings(
            nightModeDrcEnabled = drc,
            forceStereoEnabled = forceStereo,
            centerBoostDb = centerBoost
        )
    }

    private fun readSubtitleSettings(): SubtitleSettings {
        val enabled = preferences.getBoolean(KEY_SUBTITLE_DIMMER_ENABLED, DEFAULT_SUBTITLE_DIMMER)
        val ratio = preferences.getFloat(KEY_SUBTITLE_DIM_RATIO, DEFAULT_SUBTITLE_DIM_RATIO)
        return SubtitleSettings(
            subtitleDimmerEnabled = enabled,
            subtitleDimRatio = ratio
        )
    }

    private fun readAutoNightSettings(): AutoNightSettings {
        val enabled = preferences.getBoolean(KEY_AUTO_NIGHT_MODE_ENABLED, DEFAULT_AUTO_NIGHT_MODE)
        val start = preferences.getString(KEY_QUIET_HOURS_START, DEFAULT_QUIET_HOURS_START) ?: DEFAULT_QUIET_HOURS_START
        val end = preferences.getString(KEY_QUIET_HOURS_END, DEFAULT_QUIET_HOURS_END) ?: DEFAULT_QUIET_HOURS_END
        return AutoNightSettings(
            autoNightModeEnabled = enabled,
            quietHoursStart = start,
            quietHoursEnd = end
        )
    }

    private fun readJellyseerrUrl(): String? {
        val url = preferences.getString(KEY_JELLYSEERR_URL, null)?.trim()
        return if (url.isNullOrBlank()) null else url
    }

    override fun getThemeMode(): BaseThemeMode = _themeModeFlow.value

    override fun setThemeMode(mode: BaseThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
    }

    override fun getAudioDspSettings(): AudioDspSettings = _audioDspSettingsFlow.value

    override fun setAudioDspSettings(settings: AudioDspSettings) {
        preferences.edit()
            .putBoolean(KEY_NIGHT_MODE_DRC, settings.nightModeDrcEnabled)
            .putBoolean(KEY_FORCE_STEREO, settings.forceStereoEnabled)
            .putFloat(KEY_CENTER_BOOST_DB, settings.centerBoostDb)
            .apply()
        _audioDspSettingsFlow.value = settings
    }

    override fun setNightModeDrcEnabled(enabled: Boolean) {
        val current = _audioDspSettingsFlow.value
        setAudioDspSettings(current.copy(nightModeDrcEnabled = enabled))
    }

    override fun setForceStereoEnabled(enabled: Boolean) {
        val current = _audioDspSettingsFlow.value
        setAudioDspSettings(current.copy(forceStereoEnabled = enabled))
    }

    override fun setCenterBoostDb(boostDb: Float) {
        val current = _audioDspSettingsFlow.value
        setAudioDspSettings(current.copy(centerBoostDb = boostDb))
    }

    override fun getSubtitleSettings(): SubtitleSettings = _subtitleSettingsFlow.value

    override fun setSubtitleSettings(settings: SubtitleSettings) {
        val coercedRatio = settings.subtitleDimRatio.coerceIn(0.1f, 0.9f)
        val updated = settings.copy(subtitleDimRatio = coercedRatio)
        preferences.edit()
            .putBoolean(KEY_SUBTITLE_DIMMER_ENABLED, updated.subtitleDimmerEnabled)
            .putFloat(KEY_SUBTITLE_DIM_RATIO, coercedRatio)
            .apply()
        _subtitleSettingsFlow.value = updated
    }

    override fun setSubtitleDimmerEnabled(enabled: Boolean) {
        val current = _subtitleSettingsFlow.value
        setSubtitleSettings(current.copy(subtitleDimmerEnabled = enabled))
    }

    override fun setSubtitleDimRatio(ratio: Float) {
        val current = _subtitleSettingsFlow.value
        setSubtitleSettings(current.copy(subtitleDimRatio = ratio))
    }

    override fun getAutoNightSettings(): AutoNightSettings = _autoNightSettingsFlow.value

    override fun setAutoNightSettings(settings: AutoNightSettings) {
        preferences.edit()
            .putBoolean(KEY_AUTO_NIGHT_MODE_ENABLED, settings.autoNightModeEnabled)
            .putString(KEY_QUIET_HOURS_START, settings.quietHoursStart)
            .putString(KEY_QUIET_HOURS_END, settings.quietHoursEnd)
            .apply()
        _autoNightSettingsFlow.value = settings
    }

    override fun setAutoNightModeEnabled(enabled: Boolean) {
        val current = _autoNightSettingsFlow.value
        setAutoNightSettings(current.copy(autoNightModeEnabled = enabled))
    }

    override fun setQuietHours(start: String, end: String) {
        val current = _autoNightSettingsFlow.value
        setAutoNightSettings(current.copy(quietHoursStart = start, quietHoursEnd = end))
    }

    override fun getJellyseerrUrl(): String? = _jellyseerrUrlFlow.value

    override fun setJellyseerrUrl(url: String?) {
        val normalized = url?.trim()?.ifBlank { null }
        if (normalized == null) {
            preferences.edit().remove(KEY_JELLYSEERR_URL).apply()
        } else {
            preferences.edit().putString(KEY_JELLYSEERR_URL, normalized).apply()
        }
        _jellyseerrUrlFlow.value = normalized
    }
}
