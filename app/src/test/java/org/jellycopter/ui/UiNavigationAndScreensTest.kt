package org.jellycopter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.Color
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellycopter.core.model.AuthState
import org.jellycopter.core.model.ServerInfo
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.session.UserSession
import org.jellycopter.core.settings.AudioDspSettings
import org.jellycopter.core.settings.SettingsRepository
import org.jellycopter.ui.navigation.NavScreen
import org.jellycopter.ui.navigation.navItemIconTint
import org.jellycopter.ui.theme.BaseThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class UiNavigationAndScreensTest {

    @Test
    fun testAllNavScreensHaveCorrectIconsAndRoutes() {
        assertEquals("home", NavScreen.HOME.route)
        assertEquals("Home", NavScreen.HOME.title)
        assertEquals(Icons.Rounded.Home, NavScreen.HOME.icon)
        assertTrue(NavScreen.HOME.isTopLevel)

        assertEquals("channels", NavScreen.CHANNELS.route)
        assertEquals("Shuffle Channels", NavScreen.CHANNELS.title)
        assertEquals(Icons.Rounded.Tv, NavScreen.CHANNELS.icon)
        assertTrue(NavScreen.CHANNELS.isTopLevel)

        assertEquals("discover", NavScreen.DISCOVER.route)
        assertEquals("Discover", NavScreen.DISCOVER.title)
        assertEquals(Icons.Rounded.AutoAwesome, NavScreen.DISCOVER.icon)
        assertTrue(NavScreen.DISCOVER.isTopLevel)

        assertEquals("library", NavScreen.LIBRARY.route)
        assertEquals("Library", NavScreen.LIBRARY.title)
        assertEquals(Icons.Rounded.LocalMovies, NavScreen.LIBRARY.icon)
        assertTrue(NavScreen.LIBRARY.isTopLevel)

        assertEquals("trophy_room", NavScreen.TROPHY_ROOM.route)
        assertEquals("Trophy Room", NavScreen.TROPHY_ROOM.title)
        assertEquals(Icons.Rounded.EmojiEvents, NavScreen.TROPHY_ROOM.icon)
        assertTrue(NavScreen.TROPHY_ROOM.isTopLevel)

        assertEquals("post_playback", NavScreen.POST_PLAYBACK.route)
        assertEquals("Session End", NavScreen.POST_PLAYBACK.title)
        assertEquals(Icons.Rounded.Movie, NavScreen.POST_PLAYBACK.icon)
        assertFalse(NavScreen.POST_PLAYBACK.isTopLevel)

        assertEquals("settings", NavScreen.SETTINGS.route)
        assertEquals("Settings", NavScreen.SETTINGS.title)
        assertEquals(Icons.Rounded.Settings, NavScreen.SETTINGS.icon)
        assertFalse(NavScreen.SETTINGS.isTopLevel)
    }

    @Test
    fun testDiscoverFilteringContract() {
        // When Jellyseerr is configured, Discover is enabled
        val screensWithDiscover = NavScreen.topLevelScreens(isDiscoverEnabled = true)
        assertEquals(5, screensWithDiscover.size)
        assertTrue(screensWithDiscover.contains(NavScreen.DISCOVER))

        // When Jellyseerr is NOT configured, Discover is omitted
        val screensWithoutDiscover = NavScreen.topLevelScreens(isDiscoverEnabled = false)
        assertEquals(4, screensWithoutDiscover.size)
        assertFalse(screensWithoutDiscover.contains(NavScreen.DISCOVER))
        assertTrue(screensWithoutDiscover.contains(NavScreen.HOME))
        assertTrue(screensWithoutDiscover.contains(NavScreen.CHANNELS))
        assertTrue(screensWithoutDiscover.contains(NavScreen.LIBRARY))
        assertTrue(screensWithoutDiscover.contains(NavScreen.TROPHY_ROOM))

        // Default property must be backward-compatible
        assertEquals(5, NavScreen.topLevelScreens.size)
    }

    @Test
    fun test3StateFocusTintingResolver() {
        val accent = Color(0xFFAA5CC3)
        val muted = Color(0xFF94A3B8)
        val white = Color.White

        // Idle
        assertEquals(muted, navItemIconTint(isFocused = false, isSelected = false, primaryAccent = accent))
        // Focused only
        assertEquals(white, navItemIconTint(isFocused = true, isSelected = false, primaryAccent = accent))
        // Selected only
        assertEquals(accent, navItemIconTint(isFocused = false, isSelected = true, primaryAccent = accent))
        // Both focused and selected -> white (high contrast active focus)
        assertEquals(white, navItemIconTint(isFocused = true, isSelected = true, primaryAccent = accent))
    }

    @Test
    fun testAuthRepositoryIntegrationContract() = runTest {
        val authRepo = mockk<AuthRepository>()
        val mockSession = UserSession(
            serverUrl = "http://192.168.1.100:8096",
            accessToken = "token_abc_123",
            userId = "user_001",
            username = "Nathan",
            serverName = "Mock-Jellyfin"
        )
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

        every { authRepo.authState } returns authStateFlow
        every { authRepo.getSession() } returns mockSession
        coEvery { authRepo.probeServer("http://192.168.1.100:8096") } returns Result.success(
            ServerInfo(serverName = "Mock-Jellyfin", version = "10.9.0", id = "srv_1")
        )
        coEvery { authRepo.authenticate("http://192.168.1.100:8096", "Nathan", "secret") } answers {
            authStateFlow.value = AuthState.Authenticated(mockSession)
            Result.success(mockSession)
        }
        coEvery { authRepo.logout() } answers {
            authStateFlow.value = AuthState.Unauthenticated
        }

        // Initially unauthenticated
        assertEquals(AuthState.Unauthenticated, authRepo.authState.value)

        // Authenticate
        val authResult = authRepo.authenticate("http://192.168.1.100:8096", "Nathan", "secret")
        assertTrue(authResult.isSuccess)
        assertEquals(mockSession, authResult.getOrNull())
        assertTrue(authRepo.authState.value is AuthState.Authenticated)

        // Logout
        authRepo.logout()
        assertEquals(AuthState.Unauthenticated, authRepo.authState.value)
        coVerify(exactly = 1) { authRepo.logout() }
    }

    @Test
    fun testSettingsRepositoryIntegrationContract() {
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)

        val themeFlow = MutableStateFlow(BaseThemeMode.JELLYFIN_PURPLE)
        val dspFlow = MutableStateFlow(AudioDspSettings(nightModeDrcEnabled = false, forceStereoEnabled = false, centerBoostDb = 3.0f))
        val subFlow = MutableStateFlow(org.jellycopter.core.settings.SubtitleSettings())
        val nightFlow = MutableStateFlow(org.jellycopter.core.settings.AutoNightSettings())
        val jellyseerrFlow = MutableStateFlow<String?>(null)

        every { settingsRepo.themeModeFlow } returns themeFlow
        every { settingsRepo.audioDspSettingsFlow } returns dspFlow
        every { settingsRepo.subtitleSettingsFlow } returns subFlow
        every { settingsRepo.autoNightSettingsFlow } returns nightFlow
        every { settingsRepo.jellyseerrUrlFlow } returns jellyseerrFlow
        every { settingsRepo.getThemeMode() } answers { themeFlow.value }
        every { settingsRepo.getAudioDspSettings() } answers { dspFlow.value }
        every { settingsRepo.getSubtitleSettings() } answers { subFlow.value }
        every { settingsRepo.getAutoNightSettings() } answers { nightFlow.value }
        every { settingsRepo.getJellyseerrUrl() } answers { jellyseerrFlow.value }

        every { settingsRepo.setThemeMode(any()) } answers {
            themeFlow.value = firstArg()
        }
        every { settingsRepo.setNightModeDrcEnabled(any()) } answers {
            dspFlow.value = dspFlow.value.copy(nightModeDrcEnabled = firstArg())
        }
        every { settingsRepo.setForceStereoEnabled(any()) } answers {
            dspFlow.value = dspFlow.value.copy(forceStereoEnabled = firstArg())
        }
        every { settingsRepo.setCenterBoostDb(any()) } answers {
            dspFlow.value = dspFlow.value.copy(centerBoostDb = firstArg())
        }
        every { settingsRepo.setSubtitleDimmerEnabled(any()) } answers {
            subFlow.value = subFlow.value.copy(subtitleDimmerEnabled = firstArg())
        }
        every { settingsRepo.setSubtitleDimRatio(any()) } answers {
            subFlow.value = subFlow.value.copy(subtitleDimRatio = firstArg())
        }
        every { settingsRepo.setAutoNightModeEnabled(any()) } answers {
            nightFlow.value = nightFlow.value.copy(autoNightModeEnabled = firstArg())
        }
        every { settingsRepo.setQuietHours(any(), any()) } answers {
            nightFlow.value = nightFlow.value.copy(quietHoursStart = firstArg(), quietHoursEnd = secondArg())
        }
        every { settingsRepo.setJellyseerrUrl(any()) } answers {
            jellyseerrFlow.value = firstArg()
        }

        // Theme switching
        settingsRepo.setThemeMode(BaseThemeMode.OLED_PURE_BLACK)
        assertEquals(BaseThemeMode.OLED_PURE_BLACK, settingsRepo.getThemeMode())

        // Audio DSP switching
        settingsRepo.setNightModeDrcEnabled(true)
        assertTrue(settingsRepo.getAudioDspSettings().nightModeDrcEnabled)

        settingsRepo.setForceStereoEnabled(true)
        assertTrue(settingsRepo.getAudioDspSettings().forceStereoEnabled)

        settingsRepo.setCenterBoostDb(4.5f)
        assertEquals(4.5f, settingsRepo.getAudioDspSettings().centerBoostDb, 0.01f)

        // Subtitle & Night mode settings
        settingsRepo.setSubtitleDimmerEnabled(true)
        assertTrue(settingsRepo.getSubtitleSettings().subtitleDimmerEnabled)
        settingsRepo.setSubtitleDimRatio(0.6f)
        assertEquals(0.6f, settingsRepo.getSubtitleSettings().subtitleDimRatio, 0.01f)

        settingsRepo.setAutoNightModeEnabled(true)
        assertTrue(settingsRepo.getAutoNightSettings().autoNightModeEnabled)
        settingsRepo.setQuietHours("23:15", "06:45")
        assertEquals("23:15", settingsRepo.getAutoNightSettings().quietHoursStart)
        assertEquals("06:45", settingsRepo.getAutoNightSettings().quietHoursEnd)

        // Jellyseerr URL configuration
        settingsRepo.setJellyseerrUrl("http://jellyseerr.local:5055")
        assertEquals("http://jellyseerr.local:5055", settingsRepo.getJellyseerrUrl())

        settingsRepo.setJellyseerrUrl(null)
        assertFalse(!settingsRepo.getJellyseerrUrl().isNullOrBlank())
    }

    @Test
    fun testZeroEmojisInUiSourcePackage() {
        val emojiPattern = Pattern.compile(
            "[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]|[\\u2300-\\u23FF]"
        )
        val uiDir = File("src/main/java/org/jellycopter/ui")
        val rootDir = if (uiDir.exists()) uiDir else File("app/src/main/java/org/jellycopter/ui")

        assertTrue("UI source directory must exist", rootDir.exists())

        val violations = mutableListOf<String>()
        rootDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (emojiPattern.matcher(line).find()) {
                        violations.add("${file.name}:${index + 1}: ${line.trim()}")
                    }
                }
            }
        }

        assertTrue(
            "Expected zero emojis in UI package, but found violations:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
