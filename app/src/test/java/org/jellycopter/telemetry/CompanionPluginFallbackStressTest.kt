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
import java.net.ConnectException
import java.net.SocketTimeoutException

class CompanionPluginFallbackStressTest {

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

    // -------------------------------------------------------------
    // 1. Handshake Probe Failure Modes
    // -------------------------------------------------------------

    @Test
    fun testProbeHandlesHttp404PluginNotInstalled() = runTest {
        coEvery { api.ping() } returns Response.error(404, "Plugin not installed".toResponseBody(null))

        val available = service.probePlugin()
        assertFalse(available)
        assertEquals(false, service.isPluginAvailable.value)
    }

    @Test
    fun testProbeHandlesHttp502BadGateway() = runTest {
        coEvery { api.ping() } returns Response.error(502, "Bad Gateway".toResponseBody(null))

        val available = service.probePlugin()
        assertFalse(available)
        assertEquals(false, service.isPluginAvailable.value)
    }

    @Test
    fun testProbeHandlesConnectionRefused() = runTest {
        coEvery { api.ping() } throws ConnectException("Connection refused: 127.0.0.1:8096")

        val available = service.probePlugin()
        assertFalse(available)
        assertEquals(false, service.isPluginAvailable.value)
    }

    @Test
    fun testProbeHandlesSocketTimeout() = runTest {
        coEvery { api.ping() } throws SocketTimeoutException("Read timed out after 5000ms")

        val available = service.probePlugin()
        assertFalse(available)
        assertEquals(false, service.isPluginAvailable.value)
    }

    // -------------------------------------------------------------
    // 2. Standalone Room DB Fallback Operations
    // -------------------------------------------------------------

    @Test
    fun testSyncDecksStandaloneFallbackUnderNetworkOutage() = runTest {
        coEvery { api.ping() } throws IOException("No route to host")

        val localDecks = listOf(
            DeckEntity(
                deckId = "offline_deck_1",
                name = "Breaking Bad",
                seriesId = "bb_01",
                cycleNumber = 1,
                exhaustedCardIdsJson = "[\"ep_1\",\"ep_2\"]"
            )
        )

        val result = service.syncDecks(localDecks)
        assertTrue(result is SyncResult.FallbackLocal)
        val fallback = result as SyncResult.FallbackLocal
        assertTrue(fallback.reason.contains("Room DB"))

        // Remote API was never called for upsert
        coVerify(exactly = 0) { api.upsertDeck(any(), any()) }
    }

    @Test
    fun testZeroDataLossAcrossMultipleSyncFailures() = runTest {
        coEvery { api.ping() } returns Response.error(404, "".toResponseBody(null))

        val localDecks = listOf(
            DeckEntity(
                deckId = "survivor_deck",
                name = "Survivor Show",
                cycleNumber = 3,
                exhaustedCardIdsJson = "[\"s1_e1\",\"s1_e2\"]"
            )
        )
        val localAchievements = listOf(
            AchievementEntity(
                id = "binge_voyager",
                title = "Binge Voyager",
                description = "Watch 8 episodes consecutively",
                tier = "Gold",
                isUnlocked = true,
                points = 50
            )
        )

        // 25 successive sync cycles during server outage
        for (i in 1..25) {
            val deckResult = service.syncDecks(localDecks)
            val achResult = service.syncAchievements(localAchievements)

            assertTrue(deckResult is SyncResult.FallbackLocal)
            assertTrue(achResult is SyncResult.FallbackLocal)
        }

        // Verify zero attempts to push to non-existent plugin
        coVerify(exactly = 0) { api.upsertDeck(any(), any()) }
        coVerify(exactly = 0) { api.upsertAchievements(any(), any()) }
    }

    // -------------------------------------------------------------
    // 3. Set-Union Merge Invariants on Reconnection
    // -------------------------------------------------------------

    @Test
    fun testReconnectionSetUnionMergesDisjointExhaustedSets() = runTest {
        coEvery { api.ping() } returns Response.success(PingResponseDto(version = "1.0.0"))

        val localDecks = listOf(
            DeckEntity(
                deckId = "deck_union",
                name = "Union Show",
                cycleNumber = 1,
                exhaustedCardIdsJson = "[\"ep_1\",\"ep_2\"]"
            )
        )

        // Server has ep_3, ep_4 and cycle 2
        val serverMerged = DeckSyncDto(
            deckId = "deck_union",
            cycleNumber = 2,
            exhaustedItemIds = listOf("ep_3", "ep_4")
        )
        coEvery { api.upsertDeck(any(), any()) } returns Response.success(serverMerged)
        coEvery { api.getDecks(any()) } returns Response.success(emptyList())

        val result = service.syncDecks(localDecks)
        assertTrue(result is SyncResult.Success)

        coVerify {
            deckDao.upsertDeck(
                match {
                    it.deckId == "deck_union" &&
                        it.cycleNumber == 2 &&
                        it.exhaustedCardIdsJson.contains("ep_1") &&
                        it.exhaustedCardIdsJson.contains("ep_2") &&
                        it.exhaustedCardIdsJson.contains("ep_3") &&
                        it.exhaustedCardIdsJson.contains("ep_4")
                }
            )
        }
    }
}
