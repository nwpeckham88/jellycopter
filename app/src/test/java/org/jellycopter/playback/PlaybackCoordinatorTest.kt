package org.jellycopter.playback

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jellycopter.core.database.DeckProgressEntity
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShuffledDeck
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.shuffle.DefaultSmartShuffleEngine
import org.jellycopter.shuffle.WatchHistoryDecoupler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackCoordinatorTest {

    private class FakeDeckRepository : DeckRepository {
        private val decksMap = mutableMapOf<String, ShuffledDeck>()
        private val progressMap = mutableMapOf<String, DeckProgressEntity>()

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

    private lateinit var deckRepository: FakeDeckRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var watchHistoryDecoupler: WatchHistoryDecoupler
    private lateinit var shuffleEngine: DefaultSmartShuffleEngine
    private lateinit var coordinator: PlaybackCoordinator

    private fun createEpisode(id: String, name: String, index: Int, seriesId: String = "series_1"): EpisodeItem {
        return EpisodeItem(
            id = id,
            name = name,
            seriesId = seriesId,
            seriesName = "Test Series",
            seasonId = "season_1",
            seasonNumber = 1,
            indexNumber = index,
            runTimeTicks = 18_000_000_000L // 30 min
        )
    }

    private fun createCard(cardId: String, episode: EpisodeItem): MediaCard {
        return MediaCard(
            id = cardId,
            seriesId = episode.seriesId,
            seasonId = episode.seasonId,
            categoryKey = "default",
            items = listOf(episode)
        )
    }

    @Before
    fun setup() {
        deckRepository = FakeDeckRepository()
        mediaRepository = mockk(relaxed = true)
        watchHistoryDecoupler = WatchHistoryDecoupler(deckRepository)
        shuffleEngine = DefaultSmartShuffleEngine(deckRepository)
        coordinator = PlaybackCoordinator(
            watchHistoryDecoupler = watchHistoryDecoupler,
            deckRepository = deckRepository,
            mediaRepository = mediaRepository,
            shuffleEngine = shuffleEngine
        )
    }

    @Test
    fun testScrobbleSuppressionBySource() {
        val ep = createEpisode("ep1", "Ep 1", 1)
        val normalCtx = PlaybackContext(itemId = ep.id, episode = ep, source = CoordinatorSource.NORMAL)
        coordinator.onPlaybackStarted(normalCtx)
        assertFalse("NORMAL playback must not suppress scrobble", coordinator.scrobbleSuppressed.value)

        val shuffleCtx = PlaybackContext(itemId = ep.id, episode = ep, source = CoordinatorSource.SHUFFLE, deckId = "deck1")
        coordinator.onPlaybackStarted(shuffleCtx)
        assertTrue("SHUFFLE playback must suppress scrobble", coordinator.scrobbleSuppressed.value)

        val channelCtx = PlaybackContext(itemId = ep.id, episode = ep, source = CoordinatorSource.CHANNEL, channelId = "ch1")
        coordinator.onPlaybackStarted(channelCtx)
        assertTrue("CHANNEL playback must suppress scrobble", coordinator.scrobbleSuppressed.value)
    }

    @Test
    fun testShuffleEpisodeSkippedAdvancesToNextEpisode() = runTest {
        val ep1 = createEpisode("ep1", "Episode 1", 1)
        val ep2 = createEpisode("ep2", "Episode 2", 2)
        val ep3 = createEpisode("ep3", "Episode 3", 3)
        val ep4 = createEpisode("ep4", "Episode 4", 4)
        val ep5 = createEpisode("ep5", "Episode 5", 5)

        val deck = ShuffledDeck(
            deckId = "deck_test",
            name = "Test Deck",
            cards = listOf(
                createCard("c1", ep1),
                createCard("c2", ep2),
                createCard("c3", ep3),
                createCard("c4", ep4),
                createCard("c5", ep5)
            ),
            currentIndex = 0
        )
        deckRepository.saveDeck(deck)

        val ctx = PlaybackContext(
            itemId = ep1.id,
            episode = ep1,
            source = CoordinatorSource.SHUFFLE,
            deckId = "deck_test"
        )
        coordinator.onPlaybackStarted(ctx)

        // Skip ep1 -> should return PlayNext(ep2)
        val action1 = coordinator.onEpisodeSkipped()
        assertTrue("Skipping ep1 must return PlayNext", action1 is NextAction.PlayNext)
        val playNext1 = action1 as NextAction.PlayNext
        assertEquals("ep2", playNext1.episode.id)
        assertEquals("ep2", coordinator.currentContext.value?.episode?.id)

        // Skip ep2 -> should return PlayNext(ep3)
        val action2 = coordinator.onEpisodeSkipped()
        assertTrue("Skipping ep2 must return PlayNext", action2 is NextAction.PlayNext)
        val playNext2 = action2 as NextAction.PlayNext
        assertEquals("ep3", playNext2.episode.id)
        assertEquals("ep3", coordinator.currentContext.value?.episode?.id)
    }

    @Test
    fun testShuffleEpisodeCompletedAdvancesToNextEpisode() = runTest {
        val ep1 = createEpisode("ep1", "Episode 1", 1)
        val ep2 = createEpisode("ep2", "Episode 2", 2)

        val deck = ShuffledDeck(
            deckId = "deck_test",
            name = "Test Deck",
            cards = listOf(createCard("c1", ep1), createCard("c2", ep2)),
            currentIndex = 0
        )
        deckRepository.saveDeck(deck)

        val ctx = PlaybackContext(
            itemId = ep1.id,
            episode = ep1,
            source = CoordinatorSource.SHUFFLE,
            deckId = "deck_test"
        )
        coordinator.onPlaybackStarted(ctx)

        // Watched 85% of 100 seconds
        val action = coordinator.onEpisodeCompleted(positionMs = 85_000L, durationMs = 100_000L)
        assertTrue("Episode completion must return PlayNext", action is NextAction.PlayNext)
        val playNext = action as NextAction.PlayNext
        assertEquals("ep2", playNext.episode.id)
        assertEquals("ep2", coordinator.currentContext.value?.episode?.id)
    }

    @Test
    fun testNormalSourceAdvanceFetchesNextUp() = runTest {
        val ep1 = createEpisode("ep1", "Episode 1", 1, seriesId = "series_wire")
        val ep2 = createEpisode("ep2", "Episode 2", 2, seriesId = "series_wire")

        coEvery { mediaRepository.getNextUpForSeries("series_wire") } returns ep2

        val ctx = PlaybackContext(
            itemId = ep1.id,
            episode = ep1,
            source = CoordinatorSource.NORMAL
        )
        coordinator.onPlaybackStarted(ctx)

        val action = coordinator.onEpisodeSkipped()
        assertTrue("Normal mode with next up should return PlayNext", action is NextAction.PlayNext)
        assertEquals("ep2", (action as NextAction.PlayNext).episode.id)
    }

    @Test
    fun testSaveCurrentProgressPersistsMidwayPosition() = runTest {
        val ep1 = createEpisode("ep1", "Episode 1", 1)
        val deck = ShuffledDeck(
            deckId = "channel:ch1",
            name = "Channel 1",
            cards = listOf(createCard("c1", ep1)),
            currentIndex = 0
        )
        deckRepository.saveDeck(deck)

        val ctx = PlaybackContext(
            itemId = ep1.id,
            episode = ep1,
            source = CoordinatorSource.CHANNEL,
            channelId = "ch1",
            deckId = "channel:ch1"
        )
        coordinator.onPlaybackStarted(ctx)

        coordinator.saveCurrentProgress(positionMs = 45_000L, durationMs = 120_000L)

        val progress = deckRepository.getProgress("channel:ch1")
        org.junit.Assert.assertNotNull("Progress should be saved in repository", progress)
        assertEquals(45_000L, progress?.midwayResumeMs)
        assertEquals(120_000L, progress?.durationMs)
    }

    @Test
    fun testChannelSourceAdvanceToNextEpisode() = runTest {
        val ep1 = createEpisode("ep1", "Episode 1", 1, seriesId = "show_a")
        val ep2 = createEpisode("ep2", "Episode 2", 1, seriesId = "show_b")

        val deck = ShuffledDeck(
            deckId = "channel:mix_channel",
            name = "Mix Channel",
            cards = listOf(createCard("c1", ep1), createCard("c2", ep2)),
            currentIndex = 0
        )
        deckRepository.saveDeck(deck)

        val ctx = PlaybackContext(
            itemId = ep1.id,
            episode = ep1,
            source = CoordinatorSource.CHANNEL,
            channelId = "mix_channel",
            deckId = "channel:mix_channel"
        )
        coordinator.onPlaybackStarted(ctx)

        val action = coordinator.onEpisodeCompleted(positionMs = 95_000L, durationMs = 100_000L)
        assertTrue("Channel advance must return PlayNext", action is NextAction.PlayNext)
        assertEquals("ep2", (action as NextAction.PlayNext).episode.id)
    }
}
