package org.jellycopter.telemetry

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jellycopter.core.database.AchievementDao
import org.jellycopter.core.database.AchievementEntity
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.model.AchievementTier
import org.jellycopter.core.repository.RoomAchievementRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomAchievementRepositoryTest {

    private lateinit var dao: AchievementDao
    private lateinit var repository: RoomAchievementRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = RoomAchievementRepository(dao)
    }

    @Test
    fun testGetAchievementReturnsMappedModel() = runTest {
        val entity = AchievementEntity(
            id = "trip_to_the_moon",
            title = "Trip to the Moon",
            description = "Fly high",
            tier = "PLATINUM",
            points = 100,
            isUnlocked = true,
            unlockedAtUtc = "2026-09-16T04:00:00Z",
            progress = 72.0f,
            maxProgress = 72.0f
        )
        coEvery { dao.getAchievementById("trip_to_the_moon") } returns entity

        val result = repository.getAchievement("trip_to_the_moon")
        assertNotNull(result)
        assertEquals("trip_to_the_moon", result!!.id)
        assertEquals(AchievementTier.PLATINUM, result.tier)
        assertTrue(result.isUnlocked)
        assertEquals(100, result.points)
    }

    @Test
    fun testUnlockAchievementWhenLockedMarksUnlocked() = runTest {
        val entity = AchievementEntity(
            id = "night_owl",
            title = "Night Owl",
            description = "Stay up",
            tier = "SILVER",
            points = 25,
            isUnlocked = false
        )
        coEvery { dao.getAchievementById("night_owl") } returns entity

        val unlocked = repository.unlockAchievement("night_owl", "2026-09-16T04:30:00Z")
        assertTrue(unlocked)
        coVerify { dao.markUnlocked("night_owl", "2026-09-16T04:30:00Z", any()) }
    }

    @Test
    fun testUnlockAchievementWhenAlreadyUnlockedReturnsFalse() = runTest {
        val entity = AchievementEntity(
            id = "night_owl",
            title = "Night Owl",
            description = "Stay up",
            tier = "SILVER",
            points = 25,
            isUnlocked = true
        )
        coEvery { dao.getAchievementById("night_owl") } returns entity

        val unlocked = repository.unlockAchievement("night_owl")
        assertFalse(unlocked)
        coVerify(exactly = 0) { dao.markUnlocked(any(), any(), any()) }
    }

    @Test
    fun testUpdateProgressClampsAndMarksUnlockedAtThreshold() = runTest {
        val entity = AchievementEntity(
            id = "binge_voyager",
            title = "Binge Voyager",
            description = "Binge",
            tier = "GOLD",
            points = 50,
            isUnlocked = false,
            progress = 7.0f,
            maxProgress = 8.0f
        )
        coEvery { dao.getAchievementById("binge_voyager") } returns entity

        // Updating to 8.0f triggers markUnlocked
        repository.updateProgress("binge_voyager", 8.0f)
        coVerify { dao.updateProgress("binge_voyager", 8.0f, any()) }
        coVerify { dao.markUnlocked("binge_voyager", any(), any()) }
    }

    @Test
    fun testSyncAchievementsConflictResolution() = runTest {
        val existing = AchievementEntity(
            id = "trip_to_the_moon",
            title = "Trip to the Moon",
            description = "Fly high",
            tier = "PLATINUM",
            points = 100,
            isUnlocked = true,
            unlockedAtUtc = "2026-09-16T02:00:00Z",
            progress = 50.0f,
            maxProgress = 72.0f
        )
        coEvery { dao.getAchievementById("trip_to_the_moon") } returns existing

        val incoming = listOf(
            Achievement(
                id = "trip_to_the_moon",
                title = "Trip to the Moon",
                description = "Fly high",
                tier = AchievementTier.PLATINUM,
                points = 100,
                isUnlocked = true,
                unlockedAtUtc = "2026-09-16T04:00:00Z", // later timestamp
                progress = 65.0f, // higher progress
                maxProgress = 72.0f
            )
        )

        repository.syncAchievements(incoming)

        // Verifies earliest unlock timestamp (02:00:00Z) and max progress (65.0f) are retained
        coVerify {
            dao.upsertAchievement(
                match {
                    it.id == "trip_to_the_moon" &&
                        it.unlockedAtUtc == "2026-09-16T02:00:00Z" &&
                        it.progress == 65.0f &&
                        it.isUnlocked
                }
            )
        }
    }
}
