package org.jellycopter.di

import android.content.Context
import io.mockk.mockk
import org.jellycopter.core.database.JellycopterDatabase
import org.jellycopter.core.network.JellyfinApiService
import org.jellycopter.core.repository.AchievementRepository
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.repository.ChannelRepository
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

class CheckModulesTest : KoinTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        stopKoin()
        mockContext = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testAppModulesInitializationAndResolution() {
        startKoin {
            androidContext(mockContext)
            modules(appModules)
        }

        // Verify Storage Module
        val sessionStorage = get<EncryptedSessionStorage>()
        assertNotNull(sessionStorage)

        val settingsRepo = get<SettingsRepository>()
        assertNotNull(settingsRepo)

        val database = get<JellycopterDatabase>()
        assertNotNull(database)

        // Verify Network Module
        val apiService = get<JellyfinApiService>()
        assertNotNull(apiService)

        // Verify Repository Module
        val authRepo = get<AuthRepository>()
        assertNotNull(authRepo)

        val deckRepo = get<DeckRepository>()
        assertNotNull(deckRepo)

        val channelRepo = get<ChannelRepository>()
        assertNotNull(channelRepo)

        val achRepo = get<AchievementRepository>()
        assertNotNull(achRepo)
    }
}
