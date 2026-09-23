package org.jellycopter.telemetry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.repository.AchievementRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AchievementEngineTest {

    private class InMemoryAchievementRepository : AchievementRepository {
        private val store = mutableMapOf<String, Achievement>()
        private val flow = MutableStateFlow<Map<String, Achievement>>(emptyMap())

        override fun getAllAchievements(): Flow<List<Achievement>> =
            flow.map { it.values.toList() }

        override fun getUnlockedAchievements(): Flow<List<Achievement>> =
            flow.map { it.values.filter { a -> a.isUnlocked } }

        override suspend fun getAchievement(id: String): Achievement? = store[id]

        override suspend fun unlockAchievement(id: String, timestampUtc: String?): Boolean {
            val cur = store[id] ?: return false
            if (cur.isUnlocked) return false
            val updated = cur.copy(
                isUnlocked = true,
                unlockedAtUtc = timestampUtc ?: "2026-09-16T04:00:00Z",
                progress = cur.maxProgress
            )
            store[id] = updated
            flow.value = store.toMap()
            return true
        }

        override suspend fun updateProgress(id: String, progress: Float) {
            val cur = store[id] ?: return
            if (cur.isUnlocked) return
            val clamped = progress.coerceIn(0.0f, cur.maxProgress)
            val updated = cur.copy(progress = clamped)
            store[id] = updated
            if (clamped >= cur.maxProgress) {
                store[id] = updated.copy(
                    isUnlocked = true,
                    unlockedAtUtc = "2026-09-16T04:00:00Z"
                )
            }
            flow.value = store.toMap()
        }

        override suspend fun syncAchievements(incoming: List<Achievement>) {
            for (inc in incoming) {
                val cur = store[inc.id]
                if (cur == null) {
                    store[inc.id] = inc
                } else {
                    val maxProg = maxOf(cur.progress, inc.progress)
                    val isUnlocked = cur.isUnlocked || inc.isUnlocked
                    val unlockTime = cur.unlockedAtUtc ?: inc.unlockedAtUtc
                    store[inc.id] = cur.copy(
                        progress = maxProg,
                        isUnlocked = isUnlocked,
                        unlockedAtUtc = unlockTime
                    )
                }
            }
            flow.value = store.toMap()
        }

        override suspend fun seedDefaults(achievements: List<Achievement>) {
            for (a in achievements) {
                if (!store.containsKey(a.id)) {
                    store[a.id] = a
                }
            }
            flow.value = store.toMap()
        }
    }

    private lateinit var repository: InMemoryAchievementRepository
    private lateinit var engine: AchievementEngine

    @Before
    fun setup() = runTest {
        repository = InMemoryAchievementRepository()
        engine = AchievementEngine(
            repository = repository,
            scope = this,
            ioDispatcher = Dispatchers.Unconfined
        )
        // Ensure seeding runs
        repository.seedDefaults(MilestoneDefinitions.ALL_MILESTONES)
        engine.refreshAchievementsFromRepo()
    }

    @Test
    fun testTripToTheMoonAccumulation() = runTest {
        // 72 hours = 259,200,000 ms
        val oneHourMs = 3_600_000L

        // Session 1: 40 hours
        val session1 = PlaybackSessionStats(
            mediaId = "m1",
            playedMs = 40 * oneHourMs,
            completed = true
        )
        val unlocked1 = engine.onPlaybackSessionEnded(session1)
        assertTrue(unlocked1.none { it.id == MilestoneDefinitions.ID_TRIP_TO_THE_MOON })
        val prog1 = repository.getAchievement(MilestoneDefinitions.ID_TRIP_TO_THE_MOON)?.progress ?: 0f
        assertEquals(40.0f, prog1, 0.1f)

        // Session 2: 32 more hours (total 72 hours)
        val session2 = PlaybackSessionStats(
            mediaId = "m2",
            playedMs = 32 * oneHourMs,
            completed = true
        )
        val unlocked2 = engine.onPlaybackSessionEnded(session2)
        assertTrue(unlocked2.any { it.id == MilestoneDefinitions.ID_TRIP_TO_THE_MOON })
        val tripAch = repository.getAchievement(MilestoneDefinitions.ID_TRIP_TO_THE_MOON)
        assertNotNull(tripAch)
        assertTrue(tripAch!!.isUnlocked)
        assertEquals(72.0f, tripAch.progress, 0.01f)
    }

    @Test
    fun testBingeVoyagerRolling24Hours() = runTest {
        val now = System.currentTimeMillis()
        val oneHour = 3_600_000L

        // 7 episodes watched within last 10 hours
        for (i in 1..7) {
            val session = PlaybackSessionStats(
                mediaId = "ep_$i",
                playedMs = 25 * 60 * 1000L,
                completed = true,
                endedAtUtcMs = now - (10 * oneHour) + (i * oneHour)
            )
            val unlocked = engine.onPlaybackSessionEnded(session)
            assertTrue(unlocked.none { it.id == MilestoneDefinitions.ID_BINGE_VOYAGER })
        }

        // 8th episode completed
        val session8 = PlaybackSessionStats(
            mediaId = "ep_8",
            playedMs = 25 * 60 * 1000L,
            completed = true,
            endedAtUtcMs = now
        )
        val unlocked = engine.onPlaybackSessionEnded(session8)
        assertTrue(unlocked.any { it.id == MilestoneDefinitions.ID_BINGE_VOYAGER })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_BINGE_VOYAGER)!!.isUnlocked)
    }

    @Test
    fun testBingeVoyagerIgnoresEpisodesOlderThan24Hours() = runTest {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L

        // 5 episodes watched 30 hours ago (outside rolling 24h window)
        for (i in 1..5) {
            val oldSession = PlaybackSessionStats(
                mediaId = "old_$i",
                playedMs = 20 * 60 * 1000L,
                completed = true,
                endedAtUtcMs = now - (30 * hour)
            )
            engine.onPlaybackSessionEnded(oldSession)
        }

        // 3 episodes in current 24h window (total 3 in rolling window)
        for (i in 1..3) {
            val curSession = PlaybackSessionStats(
                mediaId = "cur_$i",
                playedMs = 20 * 60 * 1000L,
                completed = true,
                endedAtUtcMs = now - (2 * hour) + (i * 1000L)
            )
            val unlocked = engine.onPlaybackSessionEnded(curSession)
            assertTrue(unlocked.none { it.id == MilestoneDefinitions.ID_BINGE_VOYAGER })
        }

        val binge = repository.getAchievement(MilestoneDefinitions.ID_BINGE_VOYAGER)!!
        assertFalse(binge.isUnlocked)
        assertEquals(3.0f, binge.progress, 0.01f)
    }

    @Test
    fun testShuffleCenturion100Episodes() = runTest {
        for (i in 1..99) {
            val session = PlaybackSessionStats(
                mediaId = "shuf_$i",
                playedMs = 1000L,
                completed = true,
                isShuffle = true
            )
            val unlocked = engine.onPlaybackSessionEnded(session)
            assertTrue(unlocked.none { it.id == MilestoneDefinitions.ID_SHUFFLE_CENTURION })
        }

        val session100 = PlaybackSessionStats(
            mediaId = "shuf_100",
            playedMs = 1000L,
            completed = true,
            isShuffle = true
        )
        val unlocked100 = engine.onPlaybackSessionEnded(session100)
        assertTrue(unlocked100.any { it.id == MilestoneDefinitions.ID_SHUFFLE_CENTURION })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_SHUFFLE_CENTURION)!!.isUnlocked)
    }

    @Test
    fun testNightOwlEvaluation() = runTest {
        // Playback at 2 PM (14:00) -> does not unlock
        val afternoonSession = PlaybackSessionStats(
            mediaId = "afternoon",
            playedMs = 120_000L,
            completed = true,
            localHourOfDay = 14
        )
        val unlocked1 = engine.onPlaybackSessionEnded(afternoonSession)
        assertTrue(unlocked1.none { it.id == MilestoneDefinitions.ID_NIGHT_OWL })
        assertFalse(repository.getAchievement(MilestoneDefinitions.ID_NIGHT_OWL)!!.isUnlocked)

        // Playback at 3:30 AM (03:00) -> unlocks Night Owl
        val nightSession = PlaybackSessionStats(
            mediaId = "night",
            playedMs = 120_000L,
            completed = true,
            localHourOfDay = 3
        )
        val unlocked2 = engine.onPlaybackSessionEnded(nightSession)
        assertTrue(unlocked2.any { it.id == MilestoneDefinitions.ID_NIGHT_OWL })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_NIGHT_OWL)!!.isUnlocked)
    }

    @Test
    fun testTheCompleatistDeckExhaustion() = runTest {
        val session = PlaybackSessionStats(
            mediaId = "ep_final",
            deckId = "deck_simpsons",
            deckExhausted = true,
            completed = true
        )
        val unlocked = engine.onPlaybackSessionEnded(session)
        assertTrue(unlocked.any { it.id == MilestoneDefinitions.ID_THE_COMPLEATIST })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_THE_COMPLEATIST)!!.isUnlocked)
    }

    @Test
    fun testStereoPuristTenSessions() = runTest {
        for (i in 1..9) {
            val session = PlaybackSessionStats(
                mediaId = "stereo_$i",
                completed = true,
                isForceStereo = true
            )
            val res = engine.onPlaybackSessionEnded(session)
            assertTrue(res.none { it.id == MilestoneDefinitions.ID_STEREO_PURIST })
        }

        val session10 = PlaybackSessionStats(
            mediaId = "stereo_10",
            completed = true,
            isForceStereo = true
        )
        val res10 = engine.onPlaybackSessionEnded(session10)
        assertTrue(res10.any { it.id == MilestoneDefinitions.ID_STEREO_PURIST })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_STEREO_PURIST)!!.isUnlocked)
    }

    @Test
    fun testSpeedDemonAcceleratedPlayback() = runTest {
        val session = PlaybackSessionStats(
            mediaId = "fast_ep",
            completed = true,
            playbackSpeed = 1.25f,
            playedMs = 120_000L
        )
        val res = engine.onPlaybackSessionEnded(session)
        assertTrue(res.any { it.id == MilestoneDefinitions.ID_SPEED_DEMON })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_SPEED_DEMON)!!.isUnlocked)
    }

    @Test
    fun testDeckMasterActiveDecks() = runTest {
        val session = PlaybackSessionStats(
            mediaId = "master_ep",
            completed = true,
            activeDecksCount = 5
        )
        val res = engine.onPlaybackSessionEnded(session)
        assertTrue(res.any { it.id == MilestoneDefinitions.ID_DECK_MASTER })
        assertTrue(repository.getAchievement(MilestoneDefinitions.ID_DECK_MASTER)!!.isUnlocked)
    }

    @Test
    fun testSacredPlaybackRuleCelebrationQueueing() = runTest {
        // When session ends, achievements are added to celebrationQueue
        val session = PlaybackSessionStats(
            mediaId = "ep1",
            deckExhausted = true,
            completed = true,
            localHourOfDay = 4
        )
        val unlocked = engine.onPlaybackSessionEnded(session)
        assertTrue(unlocked.size >= 2)

        // Verifies celebration queue holds unlocked items
        val queued = engine.celebrationQueue.value
        assertEquals(unlocked.size, queued.size)

        // PostPlaybackScreen consumes celebrations
        val consumed = engine.consumeCelebrations()
        assertEquals(unlocked.size, consumed.size)

        // Celebration queue is cleared
        assertTrue(engine.celebrationQueue.value.isEmpty())
    }
}
