package org.jellycopter.telemetry

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellycopter.core.database.AchievementDao
import org.jellycopter.core.database.AchievementEntity
import org.jellycopter.core.database.DeckDao
import org.jellycopter.core.database.DeckEntity
import org.jellycopter.sync.PluginSyncApi
import org.jellycopter.sync.PluginSyncService
import org.jellycopter.sync.model.AchievementSyncDto
import org.jellycopter.sync.model.DeckSyncDto
import org.jellycopter.sync.model.PingResponseDto
import org.jellycopter.sync.model.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class PluginSyncServiceTest {

    private lateinit var api: PluginSyncApi
    private lateinit var deckDao: DeckDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var service: PluginSyncService

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        deckDao = mockk(relaxed = true)
        achievementDao = mockk(relaxed = true)
        service = PluginSyncService(api, deckDao, achievementDao)
    }

    @Test
    fun testProbePluginReturnsTrueOn200() = runTest {
        coEvery { api.ping() } returns Response.success(PingResponseDto(version = "1.0.0"))

        val available = service.probePlugin()
        assertTrue(available)
        assertEquals(true, service.isPluginAvailable.value)
    }

    @Test
    fun testProbePluginExtractsServerAudioDrcSettings() = runTest {
        coEvery { api.ping() } returns Response.success(
            PingResponseDto(
                version = "1.0.0",
                serverAudioDrcActive = true,
                downmixAlgorithm = "dynaudnorm"
            )
        )

        val available = service.probePlugin()
        assertTrue(available)
        assertEquals(true, service.serverAudioDrcActive.value)
        assertEquals("dynaudnorm", service.serverDownmixAlgorithm.value)
    }

    @Test
    fun testProbePluginReturnsFalseOn404() = runTest {
        coEvery { api.ping() } returns Response.error(404, "".toResponseBody(null))

        val available = service.probePlugin()
        assertFalse(available)
        assertEquals(false, service.isPluginAvailable.value)
    }

    @Test
    fun testProbePluginReturnsFalseOnNetworkException() = runTest {
        coEvery { api.ping() } throws IOException("Server unreachable")

        val available = service.probePlugin()
        assertFalse(available)
        assertEquals(false, service.isPluginAvailable.value)
    }

    @Test
    fun testSyncDecksFallsBackToLocalWhenPluginAbsent() = runTest {
        coEvery { api.ping() } returns Response.error(404, "".toResponseBody(null))

        val localDecks = listOf(
            DeckEntity(deckId = "deck_1", name = "The Simpsons", exhaustedCardIdsJson = "[\"ep_1\"]")
        )

        val result = service.syncDecks(localDecks)
        assertTrue(result is SyncResult.FallbackLocal)
        coVerify(exactly = 0) { api.upsertDeck(any(), any()) }
    }

    @Test
    fun testSyncDecksPerformsSetUnionWhenPluginPresent() = runTest {
        // Plugin available
        coEvery { api.ping() } returns Response.success(PingResponseDto(version = "1.0.0"))

        // Client has ep_1, ep_2
        val localDecks = listOf(
            DeckEntity(
                deckId = "deck_simpsons",
                name = "The Simpsons",
                seriesId = "series_simpsons",
                cycleNumber = 1,
                exhaustedCardIdsJson = "[\"ep_1\",\"ep_2\"]"
            )
        )

        // Server returns union with ep_2, ep_3 -> merged is ep_1, ep_2, ep_3
        val serverMergedDto = DeckSyncDto(
            deckId = "deck_simpsons",
            seriesId = "series_simpsons",
            cycleNumber = 1,
            exhaustedItemIds = listOf("ep_1", "ep_2", "ep_3")
        )
        coEvery { api.upsertDeck(any(), any()) } returns Response.success(serverMergedDto)
        coEvery { api.getDecks(any()) } returns Response.success(emptyList())

        val result = service.syncDecks(localDecks)
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).syncedCount)

        coVerify {
            deckDao.upsertDeck(
                match {
                    it.deckId == "deck_simpsons" &&
                        it.exhaustedCardIdsJson.contains("ep_1") &&
                        it.exhaustedCardIdsJson.contains("ep_2") &&
                        it.exhaustedCardIdsJson.contains("ep_3")
                }
            )
        }
    }

    @Test
    fun testSyncAchievementsMergesEarliestUnlockAndMaxProgress() = runTest {
        coEvery { api.ping() } returns Response.success(PingResponseDto())

        val localAchievements = listOf(
            AchievementEntity(
                id = "trip_to_the_moon",
                title = "Trip to the Moon",
                description = "Fly high",
                tier = "PLATINUM",
                points = 100,
                isUnlocked = true,
                unlockedAtUtc = "2026-09-16T03:00:00Z",
                progress = 50.0f,
                maxProgress = 72.0f
            )
        )
        coEvery { achievementDao.getAchievementById("trip_to_the_moon") } returns localAchievements.first()
        coEvery { api.upsertAchievements(any(), any()) } returns Response.success("{}".toResponseBody(null))

        // Remote has earlier unlock (01:00:00Z) and higher progress (60.0f)
        val remoteList = listOf(
            AchievementSyncDto(
                id = "trip_to_the_moon",
                title = "Trip to the Moon",
                unlockedAtUtc = "2026-09-16T01:00:00Z",
                progress = 60.0f,
                maxProgress = 72.0f,
                isUnlocked = true
            )
        )
        coEvery { api.getAchievements(any()) } returns Response.success(remoteList)

        val result = service.syncAchievements(localAchievements)
        assertTrue(result is SyncResult.Success)

        coVerify {
            achievementDao.upsertAchievement(
                match {
                    it.id == "trip_to_the_moon" &&
                        it.unlockedAtUtc == "2026-09-16T01:00:00Z" &&
                        it.progress == 60.0f &&
                        it.isUnlocked
                }
            )
        }
    }

    @Test
    fun testSyncAchievementsFallsBackToLocalWhenPluginAbsent() = runTest {
        coEvery { api.ping() } returns Response.error(404, "".toResponseBody(null))

        val result = service.syncAchievements(emptyList())
        assertTrue(result is SyncResult.FallbackLocal)
        coVerify(exactly = 0) { api.upsertAchievements(any(), any()) }
    }
}
