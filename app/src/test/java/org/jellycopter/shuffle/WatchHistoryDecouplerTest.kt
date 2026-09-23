package org.jellycopter.shuffle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.jellycopter.core.database.DeckProgressEntity
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShuffledDeck
import org.jellycopter.core.repository.DeckRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryDecouplerTest {

    private class FakeDeckRepository : DeckRepository {
        private val progressMap = mutableMapOf<String, DeckProgressEntity>()
        private val decksMap = mutableMapOf<String, ShuffledDeck>()

        override fun getAllDecks(): Flow<List<ShuffledDeck>> = flowOf(decksMap.values.toList())
        override suspend fun getDeck(deckId: String): ShuffledDeck? = decksMap[deckId]
        override suspend fun saveDeck(deck: ShuffledDeck) { decksMap[deck.deckId] = deck }
        override suspend fun deleteDeck(deckId: String) { decksMap.remove(deckId) }

        override suspend fun saveProgress(
            deckId: String,
            currentCardId: String,
            currentEpisodeId: String,
            currentIndex: Int,
            positionMs: Long,
            durationMs: Long
        ) {
            progressMap[deckId] = DeckProgressEntity(
                deckId = deckId,
                currentCardId = currentCardId,
                currentEpisodeId = currentEpisodeId,
                currentIndex = currentIndex,
                midwayResumeMs = positionMs,
                durationMs = durationMs
            )
        }

        override suspend fun getProgress(deckId: String): DeckProgressEntity? = progressMap[deckId]
        override fun getProgressFlow(deckId: String): Flow<DeckProgressEntity?> = flowOf(progressMap[deckId])
        override suspend fun deleteProgress(deckId: String) { progressMap.remove(deckId) }
    }

    @Test
    fun testScrobbleSuppressionForShuffleSources() {
        val decoupler = WatchHistoryDecoupler()

        assertTrue(decoupler.shouldSuppressJellyfinScrobble(PlaybackSource.SHUFFLE_DECK))
        assertTrue(decoupler.shouldSuppressJellyfinScrobble(PlaybackSource.MIX_CHANNEL))
        assertFalse(decoupler.shouldSuppressJellyfinScrobble(PlaybackSource.SEQUENTIAL_NEXT_UP))
    }

    @Test
    fun testSanitizeEpisodeMetadata() {
        val decoupler = WatchHistoryDecoupler()
        val original = EpisodeItem(
            id = "ep_1",
            name = "Test Ep",
            seriesId = "series_1",
            seasonId = "season_1",
            seasonNumber = 1,
            indexNumber = 1,
            playCount = 5.0
        )

        val sanitized = decoupler.sanitizeEpisodeForPlayback(original)
        assertEquals(0.0, sanitized.playCount, 0.001)
        assertEquals(original.id, sanitized.id)
    }

    @Test
    fun testAutoplayProgressionMidwayResume() {
        val decoupler = WatchHistoryDecoupler()

        // 50% watched of a 20-minute episode (10 min in, 10 min left)
        val outcome = decoupler.evaluateProgress(
            positionMs = 600_000L,
            durationMs = 1_200_000L
        )
        assertEquals(PlaybackOutcome.MIDWAY_RESUME, outcome)
    }

    @Test
    fun testAutoplayProgressionCompletionThreshold() {
        val decoupler = WatchHistoryDecoupler()

        // 80% watched of a 20-minute episode
        val outcome80 = decoupler.evaluateProgress(
            positionMs = 960_000L,
            durationMs = 1_200_000L
        )
        assertEquals(PlaybackOutcome.COMPLETED, outcome80)

        // 90% watched
        val outcome90 = decoupler.evaluateProgress(
            positionMs = 1_080_000L,
            durationMs = 1_200_000L
        )
        assertEquals(PlaybackOutcome.COMPLETED, outcome90)
    }

    @Test
    fun testAutoplayProgressionNearCreditsThreshold() {
        val decoupler = WatchHistoryDecoupler()

        // 60% watched of a 3-minute clip, but only 45s left (<= 90s remaining)
        val outcomeNearEnd = decoupler.evaluateProgress(
            positionMs = 135_000L,
            durationMs = 180_000L
        )
        assertEquals(PlaybackOutcome.COMPLETED, outcomeNearEnd)
    }

    @Test
    fun testDecoupledProgressSavedToRoomDb() = runBlocking {
        val repo = FakeDeckRepository()
        val decoupler = WatchHistoryDecoupler(repo)

        val ep = EpisodeItem(
            id = "ep_10",
            name = "Test Ep 10",
            seriesId = "series_1",
            seasonId = "season_1",
            seasonNumber = 1,
            indexNumber = 10
        )
        val card = MediaCard(
            id = "card_10",
            seriesId = "series_1",
            seasonId = "season_1",
            categoryKey = "season_1",
            items = listOf(ep)
        )

        decoupler.recordDeckProgress(
            deckId = "deck_test_1",
            card = card,
            currentIndex = 3,
            positionMs = 450_000L,
            durationMs = 1_200_000L
        )

        val saved = decoupler.getDeckProgress("deck_test_1")
        assertNotNull(saved)
        assertEquals("deck_test_1", saved?.deckId)
        assertEquals("card_10", saved?.currentCardId)
        assertEquals("ep_10", saved?.currentEpisodeId)
        assertEquals(3, saved?.currentIndex)
        assertEquals(450_000L, saved?.midwayResumeMs)
    }
}
