package org.jellycopter.session

import android.content.Context
import io.mockk.mockk
import org.jellycopter.core.settings.AudioDspSettings
import org.jellycopter.core.settings.SharedPreferencesSettingsRepository
import org.jellycopter.ui.theme.BaseThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: SharedPreferencesSettingsRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        val mockContext: Context = mockk(relaxed = true)
        repository = SharedPreferencesSettingsRepository(mockContext, fakePrefs)
    }

    @Test
    fun testDefaultSettings() {
        assertEquals(BaseThemeMode.WARM_DARK, repository.getThemeMode())
        assertEquals(BaseThemeMode.WARM_DARK, repository.themeModeFlow.value)

        val dsp = repository.getAudioDspSettings()
        assertFalse(dsp.nightModeDrcEnabled)
        assertFalse(dsp.forceStereoEnabled)
        assertEquals(3.0f, dsp.centerBoostDb, 0.01f)

        val subtitle = repository.getSubtitleSettings()
        assertFalse(subtitle.subtitleDimmerEnabled)
        assertEquals(0.5f, subtitle.subtitleDimRatio, 0.01f)

        val autoNight = repository.getAutoNightSettings()
        assertFalse(autoNight.autoNightModeEnabled)
        assertEquals("22:30", autoNight.quietHoursStart)
        assertEquals("07:00", autoNight.quietHoursEnd)

        assertNull(repository.getJellyseerrUrl())
        assertNull(repository.jellyseerrUrlFlow.value)
    }

    @Test
    fun testSetThemeMode() {
        repository.setThemeMode(BaseThemeMode.CYBERPUNK_NEON)
        assertEquals(BaseThemeMode.CYBERPUNK_NEON, repository.getThemeMode())
        assertEquals(BaseThemeMode.CYBERPUNK_NEON, repository.themeModeFlow.value)

        repository.setThemeMode(BaseThemeMode.OLED_PURE_BLACK)
        assertEquals(BaseThemeMode.OLED_PURE_BLACK, repository.getThemeMode())
        assertEquals(BaseThemeMode.OLED_PURE_BLACK, repository.themeModeFlow.value)

        repository.setThemeMode(BaseThemeMode.WARM_DARK)
        assertEquals(BaseThemeMode.WARM_DARK, repository.getThemeMode())
        assertEquals(BaseThemeMode.WARM_DARK, repository.themeModeFlow.value)
    }

    @Test
    fun testAudioDspSettingsUpdates() {
        repository.setNightModeDrcEnabled(true)
        assertTrue(repository.getAudioDspSettings().nightModeDrcEnabled)
        assertTrue(repository.audioDspSettingsFlow.value.nightModeDrcEnabled)

        repository.setForceStereoEnabled(true)
        assertTrue(repository.getAudioDspSettings().forceStereoEnabled)

        repository.setCenterBoostDb(4.5f)
        assertEquals(4.5f, repository.getAudioDspSettings().centerBoostDb, 0.01f)
        assertEquals(4.5f, repository.audioDspSettingsFlow.value.centerBoostDb, 0.01f)

        val customDsp = AudioDspSettings(
            nightModeDrcEnabled = false,
            forceStereoEnabled = true,
            centerBoostDb = 2.0f
        )
        repository.setAudioDspSettings(customDsp)
        assertEquals(customDsp, repository.getAudioDspSettings())
        assertEquals(customDsp, repository.audioDspSettingsFlow.value)
    }

    @Test
    fun testJellyseerrUrlPersistence() {
        repository.setJellyseerrUrl("https://jellyseerr.kn8design.com")
        assertEquals("https://jellyseerr.kn8design.com", repository.getJellyseerrUrl())
        assertEquals("https://jellyseerr.kn8design.com", repository.jellyseerrUrlFlow.value)

        // Setting blank or null removes the URL
        repository.setJellyseerrUrl("   ")
        assertNull(repository.getJellyseerrUrl())
        assertNull(repository.jellyseerrUrlFlow.value)
    }

    @Test
    fun testSubtitleSettingsUpdates() {
        repository.setSubtitleDimmerEnabled(true)
        assertTrue(repository.getSubtitleSettings().subtitleDimmerEnabled)
        assertTrue(repository.subtitleSettingsFlow.value.subtitleDimmerEnabled)

        repository.setSubtitleDimRatio(0.7f)
        assertEquals(0.7f, repository.getSubtitleSettings().subtitleDimRatio, 0.01f)
        assertEquals(0.7f, repository.subtitleSettingsFlow.value.subtitleDimRatio, 0.01f)

        // Clamping checks
        repository.setSubtitleDimRatio(0.05f)
        assertEquals(0.1f, repository.getSubtitleSettings().subtitleDimRatio, 0.01f)

        repository.setSubtitleDimRatio(0.95f)
        assertEquals(0.9f, repository.getSubtitleSettings().subtitleDimRatio, 0.01f)
    }

    @Test
    fun testAutoNightSettingsUpdates() {
        repository.setAutoNightModeEnabled(true)
        assertTrue(repository.getAutoNightSettings().autoNightModeEnabled)
        assertTrue(repository.autoNightSettingsFlow.value.autoNightModeEnabled)

        repository.setQuietHours("23:00", "06:30")
        assertEquals("23:00", repository.getAutoNightSettings().quietHoursStart)
        assertEquals("06:30", repository.getAutoNightSettings().quietHoursEnd)
        assertEquals("23:00", repository.autoNightSettingsFlow.value.quietHoursStart)
        assertEquals("06:30", repository.autoNightSettingsFlow.value.quietHoursEnd)
    }
}
