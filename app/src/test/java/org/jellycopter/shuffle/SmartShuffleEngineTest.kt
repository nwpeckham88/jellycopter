package org.jellycopter.shuffle

import org.jellycopter.core.model.DeckConfig
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.ShowConfig
import org.jellycopter.shuffle.model.DeckProgressionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartShuffleEngineTest {

    private fun createEpisodes(count: Int, seriesId: String = "series_100"): List<EpisodeItem> {
        return (1..count).map { idx ->
            EpisodeItem(
                id = "ep_$idx",
                name = "Episode $idx",
                seriesId = seriesId,
                seriesName = "Test Show",
                seasonId = "season_${((idx - 1) / 5) + 1}",
                seasonNumber = ((idx - 1) / 5) + 1,
                indexNumber = ((idx - 1) % 5) + 1,
                runTimeTicks = 12_000_000_000L
            )
        }
    }

    @Test
    fun testDeckGenerationAndRegistration() {
        val engine = DefaultSmartShuffleEngine()
        val episodes = createEpisodes(10)

        val deck = engine.generateDeck("series_100", episodes)
        assertEquals("series:series_100", deck.deckId)
        assertEquals(10, deck.cards.size)
        assertEquals(0, deck.currentIndex)
        assertEquals(1, deck.cycleNumber)

        val registered = engine.getDeck("series:series_100")
        assertNotNull(registered)
        assertEquals(deck.deckId, registered?.deckId)
    }

    @Test
    fun testAdvancePlaybackMidwayResume() {
        val engine = DefaultSmartShuffleEngine()
        val episodes = createEpisodes(5)
        val deck = engine.generateDeck("series_100", episodes)

        val currentCardId = deck.cards[0].id
        // 40% playback (400s of 1000s) -> Midway Resume
        val event = engine.advancePlayback(
            deckId = deck.deckId,
            currentCardId = currentCardId,
            positionMs = 400_000L,
            durationMs = 1_000_000L
        )

        assertTrue("Event should be MidwayResume", event is DeckProgressionEvent.MidwayResume)
        val resumeEvent = event as DeckProgressionEvent.MidwayResume
        assertEquals(currentCardId, resumeEvent.cardId)
        assertEquals(400_000L, resumeEvent.positionMs)

        // Deck pointer does not advance on midway resume
        val updatedDeck = engine.getDeck(deck.deckId)
        assertEquals(0, updatedDeck?.currentIndex)
        assertEquals(400_000L, updatedDeck?.midwayResumeMs)
    }

    @Test
    fun testAdvancePlaybackCompletionAndCardAdvance() {
        val engine = DefaultSmartShuffleEngine()
        val episodes = createEpisodes(5)
        val deck = engine.generateDeck("series_100", episodes)

        val firstCardId = deck.cards[0].id
        // 85% playback -> Card completed
        val event = engine.advancePlayback(
            deckId = deck.deckId,
            currentCardId = firstCardId,
            positionMs = 850_000L,
            durationMs = 1_000_000L
        )

        assertTrue("Event should be Advanced", event is DeckProgressionEvent.Advanced)
        val advanced = event as DeckProgressionEvent.Advanced
        assertEquals(firstCardId, advanced.completedCardId)
        assertEquals(1, advanced.newIndex)
        assertFalse(advanced.cycleReset)

        val updatedDeck = engine.getDeck(deck.deckId)
        assertEquals(1, updatedDeck?.currentIndex)
        assertEquals(0L, updatedDeck?.midwayResumeMs)
        assertTrue(updatedDeck?.cards?.get(0)?.isExhausted == true)
    }

    @Test
    fun testCycleResetAt75PercentThreshold() {
        val engine = DefaultSmartShuffleEngine(random = Random(42))
        // 4 cards in deck: 3 exhausted = 75% threshold
        val episodes = createEpisodes(4)
        val config = DeckConfig(cooldownThresholdRatio = 0.75f)
        val deck = engine.generateDeck("series_100", episodes, config)

        // Play card 0 to completion
        val ev1 = engine.advancePlayback(deck.deckId, deck.cards[0].id, 900_000L, 1_000_000L)
        assertFalse((ev1 as DeckProgressionEvent.Advanced).cycleReset)

        // Play card 1 to completion
        val deck1 = engine.getDeck(deck.deckId)!!
        val ev2 = engine.advancePlayback(deck.deckId, deck1.cards[1].id, 900_000L, 1_000_000L)
        assertFalse((ev2 as DeckProgressionEvent.Advanced).cycleReset)

        // Play card 2 to completion (exhausted count reaches 3 out of 4 = 75%)
        val deck2 = engine.getDeck(deck.deckId)!!
        val ev3 = engine.advancePlayback(deck.deckId, deck2.cards[2].id, 900_000L, 1_000_000L)
        val advanced3 = ev3 as DeckProgressionEvent.Advanced

        assertTrue("Cycle reset should trigger at 75% exhaustion", advanced3.cycleReset)
        assertEquals(2, advanced3.newCycleNumber)
        assertEquals(0, advanced3.newIndex)

        val resetDeck = engine.getDeck(deck.deckId)!!
        assertEquals(2, resetDeck.cycleNumber)
        assertEquals(0, resetDeck.currentIndex)
        // All cards should be fresh (isExhausted == false)
        assertTrue("All cards should be fresh after reset", resetDeck.cards.none { it.isExhausted })
    }

    @Test
    fun testRecycleSkippedDefaultMode() {
        val engine = DefaultSmartShuffleEngine()
        val episodes = createEpisodes(10)
        val deck = engine.generateDeck("series_100", episodes, DeckConfig(strictQueueMode = false))

        val skippedCardId = deck.cards[0].id
        engine.recycleSkipped(deck.deckId, skippedCardId)

        val updatedDeck = engine.getDeck(deck.deckId)!!
        assertEquals(0, updatedDeck.currentIndex)
        // Card at index 0 should now be what was formerly index 1
        assertEquals(deck.cards[1].id, updatedDeck.cards[0].id)
        // Skipped card should still exist in deck and NOT be exhausted
        val recycledPos = updatedDeck.cards.indexOfFirst { it.id == skippedCardId }
        assertTrue("Skipped card should be moved forward (index: $recycledPos)", recycledPos > 0)
        assertFalse("Recycled card should not be exhausted", updatedDeck.cards[recycledPos].isExhausted)
    }

    @Test
    fun testRecycleSkippedStrictMode() {
        val engine = DefaultSmartShuffleEngine()
        val episodes = createEpisodes(5)
        val deck = engine.generateDeck("series_100", episodes, DeckConfig(strictQueueMode = true))

        val skippedCardId = deck.cards[0].id
        engine.recycleSkipped(deck.deckId, skippedCardId)

        val updatedDeck = engine.getDeck(deck.deckId)!!
        assertEquals(1, updatedDeck.currentIndex)
        assertTrue("Skipped card in strict mode should be exhausted", updatedDeck.cards[0].isExhausted)
    }

    @Test
    fun testMixChannelGenerationViaEngine() {
        val engine = DefaultSmartShuffleEngine(random = Random(123))
        val showA = ShowConfig("show_1", "Show 1", 1)
        val showB = ShowConfig("show_2", "Show 2", 1)

        val deck = engine.generateMixChannelDeck(
            channelId = "mixed_channel",
            shows = listOf(showA, showB),
            episodeProvider = { seriesId ->
                (1..5).map {
                    EpisodeItem(
                        id = "${seriesId}_ep$it",
                        name = "$seriesId Episode $it",
                        seriesId = seriesId,
                        seasonId = "${seriesId}_s1",
                        seasonNumber = 1,
                        indexNumber = it,
                        runTimeTicks = 1000L
                    )
                }
            }
        )

        assertEquals("mixed_channel", deck.channelId)
        assertTrue(deck.cards.isNotEmpty())
        // Verify no adjacent collisions
        for (i in 0 until deck.cards.size - 1) {
            assertFalse(
                deck.cards[i].seriesId == deck.cards[i + 1].seriesId
            )
        }
    }
}
