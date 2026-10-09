package org.jellycopter.ui

import org.jellycopter.ui.screens.ActiveShuffleDeckItem
import org.jellycopter.ui.screens.ContinueWatchingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenTest {

    @Test
    fun testContinueWatchingItemDefaultsAndContract() {
        val item = ContinueWatchingItem(
            id = "series_severance",
            seriesTitle = "Severance",
            episodeTitle = "The You You Are",
            seasonNumber = 1,
            episodeNumber = 4,
            resumePositionMs = 1_200_000L,
            durationMs = 3_000_000L
        )

        assertEquals("series_severance", item.id)
        assertEquals("Severance", item.seriesTitle)
        assertEquals("The You You Are", item.episodeTitle)
        assertEquals(1, item.seasonNumber)
        assertEquals(4, item.episodeNumber)
        assertEquals(1_200_000L, item.resumePositionMs)
        assertEquals(3_000_000L, item.durationMs)
        assertNull(item.overview)
        assertNull(item.backdropUrl)
        assertNull(item.posterUrl)
    }

    @Test
    fun testActiveShuffleDeckItemProperties() {
        val seriesDeck = ActiveShuffleDeckItem(
            deckId = "series:series_bear",
            title = "The Bear",
            cycleNumber = 2,
            exhaustedCount = 8,
            totalCount = 18,
            lastResumeMs = 450_000L,
            durationMs = 1_800_000L,
            isChannelMix = false,
            seriesId = "series_bear"
        )

        assertEquals("series:series_bear", seriesDeck.deckId)
        assertEquals("The Bear", seriesDeck.title)
        assertEquals(2, seriesDeck.cycleNumber)
        assertEquals(8, seriesDeck.exhaustedCount)
        assertEquals(18, seriesDeck.totalCount)
        assertEquals(450_000L, seriesDeck.lastResumeMs)
        assertEquals(1_800_000L, seriesDeck.durationMs)
        assertFalse(seriesDeck.isChannelMix)
        assertEquals("series_bear", seriesDeck.seriesId)
    }

    @Test
    fun testActiveShuffleChannelMixProperties() {
        val channelDeck = ActiveShuffleDeckItem(
            deckId = "channel:chan_evening_comedy",
            title = "Evening Comedy",
            cycleNumber = 1,
            exhaustedCount = 3,
            totalCount = 40,
            lastResumeMs = 0L,
            durationMs = 1_500_000L,
            isChannelMix = true,
            seriesId = null
        )

        assertEquals("channel:chan_evening_comedy", channelDeck.deckId)
        assertEquals("Evening Comedy", channelDeck.title)
        assertEquals(1, channelDeck.cycleNumber)
        assertTrue(channelDeck.isChannelMix)
        assertNull(channelDeck.seriesId)
    }

    @Test
    fun testDurationAndProgressFormattingCalculation() {
        // Unstarted episode: resumePositionMs = 0L
        val unstarted = ContinueWatchingItem(
            id = "ep_1",
            seriesTitle = "Fargo",
            episodeTitle = "The Crocodile's Dilemma",
            seasonNumber = 1,
            episodeNumber = 1,
            resumePositionMs = 0L,
            durationMs = 3_600_000L // 60 minutes
        )
        val minutesTotal = ((unstarted.durationMs - unstarted.resumePositionMs) / 60000).coerceAtLeast(1)
        assertEquals(60L, minutesTotal)
        val durationLabelUnstarted = if (unstarted.resumePositionMs > 0L) {
            "${minutesTotal}m left"
        } else {
            "Next Up • ${minutesTotal}m"
        }
        assertEquals("Next Up • 60m", durationLabelUnstarted)

        // Mid-progress episode: resumePositionMs = 15 minutes in (900,000 ms)
        val inProgress = unstarted.copy(resumePositionMs = 900_000L)
        val minutesRemaining = ((inProgress.durationMs - inProgress.resumePositionMs) / 60000).coerceAtLeast(1)
        assertEquals(45L, minutesRemaining)
        val durationLabelProgress = if (inProgress.resumePositionMs > 0L) {
            "${minutesRemaining}m left"
        } else {
            "Next Up • ${minutesRemaining}m"
        }
        assertEquals("45m left", durationLabelProgress)
    }

    @Test
    fun testExhaustionPercentageCalculations() {
        val total = 20
        val exhausted = 5
        val pct = if (total > 0) (exhausted * 100) / total else 0
        assertEquals(25, pct)
    }
}
