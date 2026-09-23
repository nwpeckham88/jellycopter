package org.jellycopter.shuffle

import org.jellycopter.core.model.EpisodeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPartGrouperTest {

    private fun makeEp(
        id: String,
        name: String,
        seasonNumber: Int,
        indexNumber: Int,
        seasonId: String = "season_$seasonNumber",
        seriesId: String = "series_1",
        runTimeTicks: Long = 10_000_000_000L,
        locationType: String = "FileSystem",
        isMissing: Boolean = false,
        mediaType: String = "Video"
    ): EpisodeItem {
        return EpisodeItem(
            id = id,
            name = name,
            seriesId = seriesId,
            seriesName = "Star Trek",
            seasonId = seasonId,
            seasonNumber = seasonNumber,
            indexNumber = indexNumber,
            runTimeTicks = runTimeTicks,
            locationType = locationType,
            isMissing = isMissing,
            mediaType = mediaType
        )
    }

    @Test
    fun testBasicTwoPartGrouping() {
        val ep1 = makeEp("ep1", "The Best of Both Worlds, Part 1", 3, 25)
        val ep2 = makeEp("ep2", "The Best of Both Worlds, Part 2", 3, 26)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        assertEquals(1, cards.size)
        assertTrue(cards[0].isMultiPart)
        assertEquals(2, cards[0].items.size)
        assertEquals("ep1", cards[0].items[0].id)
        assertEquals("ep2", cards[0].items[1].id)
    }

    @Test
    fun testRomanNumeralGrouping() {
        val ep1 = makeEp("ep1", "Redemption: Part I", 4, 26)
        val ep2 = makeEp("ep2", "Redemption: Part II", 4, 27)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        assertEquals(1, cards.size)
        assertTrue(cards[0].isMultiPart)
        assertEquals(2, cards[0].items.size)
        assertEquals("Redemption: Part I", cards[0].items[0].name)
        assertEquals("Redemption: Part II", cards[0].items[1].name)
    }

    @Test
    fun testVolumeAndPtAbbreviation() {
        val ep1 = makeEp("ep1", "Chain of Command, Pt. 1", 6, 10)
        val ep2 = makeEp("ep2", "Chain of Command, Pt. 2", 6, 11)
        val ep3 = makeEp("ep3", "Ship in a Bottle", 6, 12)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2, ep3))
        assertEquals(2, cards.size)
        assertTrue(cards[0].isMultiPart)
        assertEquals(2, cards[0].items.size)
        assertFalse(cards[1].isMultiPart)
        assertEquals(1, cards[1].items.size)
    }

    @Test
    fun testParenthesesNumbering() {
        val ep1 = makeEp("ep1", "Way of the Warrior (1)", 4, 1)
        val ep2 = makeEp("ep2", "Way of the Warrior (2)", 4, 2)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        assertEquals(1, cards.size)
        assertTrue(cards[0].isMultiPart)
        assertEquals(2, cards[0].items.size)
    }

    @Test
    fun testCrossSeasonSpecialNotBonded() {
        // Star Trek TNG Best of Both Worlds cliffhanger spans Season 3 (finale) and Season 4 (premiere)
        val ep1 = makeEp("ep1", "The Best of Both Worlds, Part 1", 3, 26, seasonId = "season_3")
        val ep2 = makeEp("ep2", "The Best of Both Worlds, Part 2", 4, 1, seasonId = "season_4")

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        // Must NOT bond across seasons because seasonId differs and index is not sequential in same season
        assertEquals(2, cards.size)
        assertFalse(cards[0].isMultiPart)
        assertFalse(cards[1].isMultiPart)
    }

    @Test
    fun testNonSequentialIndexNotBonded() {
        val ep1 = makeEp("ep1", "Gambit, Part 1", 7, 4)
        val ep2 = makeEp("ep2", "Gambit, Part 2", 7, 6) // Non-consecutive episode index (skipped 5)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        assertEquals(2, cards.size)
        assertFalse(cards[0].isMultiPart)
        assertFalse(cards[1].isMultiPart)
    }

    @Test
    fun testNonSequentialPartNotBonded() {
        val ep1 = makeEp("ep1", "Story Arc, Part 1", 2, 5)
        val ep2 = makeEp("ep2", "Story Arc, Part 3", 2, 6) // Skipped Part 2

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        assertEquals(2, cards.size)
        assertFalse(cards[0].isMultiPart)
        assertFalse(cards[1].isMultiPart)
    }

    @Test
    fun testGenericBlacklistProtectsEpisodeNumbers() {
        val ep1 = makeEp("ep1", "Episode 1", 1, 1)
        val ep2 = makeEp("ep2", "Episode 2", 1, 2)
        val ep3 = makeEp("ep3", "Episode 3", 1, 3)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2, ep3))
        // Must NOT group into a single 3-part card
        assertEquals(3, cards.size)
        assertFalse(cards[0].isMultiPart)
        assertFalse(cards[1].isMultiPart)
        assertFalse(cards[2].isMultiPart)
    }

    @Test
    fun testGenericBlacklistProtectsChapterNumbers() {
        val ep1 = makeEp("ep1", "Chapter 1", 1, 1)
        val ep2 = makeEp("ep2", "Chapter 2", 1, 2)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2))
        assertEquals(2, cards.size)
        assertFalse(cards[0].isMultiPart)
        assertFalse(cards[1].isMultiPart)
    }

    @Test
    fun testHygieneFiltering() {
        val ep1 = makeEp("ep1", "Good Ep", 1, 1)
        val ep2 = makeEp("ep2", "Virtual Ep", 1, 2, locationType = "Virtual")
        val ep3 = makeEp("ep3", "Missing Ep", 1, 3, isMissing = true)
        val ep4 = makeEp("ep4", "Zero Runtime", 1, 4, runTimeTicks = 0L)
        val ep5 = makeEp("ep5", "Audio Ep", 1, 5, mediaType = "Audio")

        val playable = MultiPartGrouper.filterPlayableEpisodes(listOf(ep1, ep2, ep3, ep4, ep5))
        assertEquals(1, playable.size)
        assertEquals("ep1", playable[0].id)
    }

    @Test
    fun testThreePartBondingContiguity() {
        val ep1 = makeEp("ep1", "All Good Things..., Part 1", 7, 25)
        val ep2 = makeEp("ep2", "All Good Things..., Part 2", 7, 26)
        val ep3 = makeEp("ep3", "All Good Things..., Part 3", 7, 27)

        val cards = MultiPartGrouper.groupEpisodes(listOf(ep1, ep2, ep3))
        assertEquals(1, cards.size)
        assertEquals(3, cards[0].items.size)
        assertEquals("ep1", cards[0].items[0].id)
        assertEquals("ep2", cards[0].items[1].id)
        assertEquals("ep3", cards[0].items[2].id)
    }

    @Test
    fun testRomanNumeralParser() {
        assertEquals(1, MultiPartGrouper.parseNumber("I"))
        assertEquals(2, MultiPartGrouper.parseNumber("II"))
        assertEquals(3, MultiPartGrouper.parseNumber("III"))
        assertEquals(4, MultiPartGrouper.parseNumber("IV"))
        assertEquals(5, MultiPartGrouper.parseNumber("V"))
        assertEquals(6, MultiPartGrouper.parseNumber("VI"))
        assertEquals(9, MultiPartGrouper.parseNumber("IX"))
        assertEquals(10, MultiPartGrouper.parseNumber("X"))
        assertEquals(14, MultiPartGrouper.parseNumber("XIV"))
        assertEquals(42, MultiPartGrouper.parseNumber("42"))
        assertNull(MultiPartGrouper.parseNumber("invalid"))
    }
}
