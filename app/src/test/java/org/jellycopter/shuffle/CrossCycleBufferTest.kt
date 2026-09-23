package org.jellycopter.shuffle

import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CrossCycleBufferTest {

    private fun makeCard(id: String, categoryKey: String): MediaCard {
        val ep = EpisodeItem(
            id = id,
            name = "Ep $id",
            seriesId = "series_1",
            seasonId = categoryKey,
            seasonNumber = 1,
            indexNumber = 1,
            runTimeTicks = 1000L
        )
        return MediaCard(
            id = id,
            seriesId = "series_1",
            seasonId = categoryKey,
            categoryKey = categoryKey,
            items = listOf(ep)
        )
    }

    @Test
    fun testTailItemsSwappedOutOfHead() {
        val n = 20
        // K = floor(0.20 * 20) = 4
        val categories = listOf("s1", "s2", "s3", "s4")
        val prevDeck = (0 until n).map { "ep_$it" }
        val tailItems = prevDeck.takeLast(4).toSet() // ep_16, ep_17, ep_18, ep_19

        // Create new cards with tail item ep_19 at index 0 and ep_18 at index 1
        val newCards = mutableListOf<MediaCard>()
        newCards.add(makeCard("ep_19", "s1"))
        newCards.add(makeCard("ep_18", "s2"))
        for (i in 0 until (n - 2)) {
            val cat = categories[i % categories.size]
            newCards.add(makeCard("ep_$i", cat))
        }

        val buffered = CrossCycleBuffer.bufferCrossCycle(newCards, prevDeck, 0.20f, Random(42))
        assertEquals(n, buffered.size)

        // Inspect head K = 4 slots: none should be from tailItems
        val headIds = buffered.take(4).map { it.id }
        for (tailId in tailItems) {
            assertFalse(
                "Head slots $headIds should not contain tail item $tailId",
                headIds.contains(tailId)
            )
        }
    }

    @Test
    fun testBufferKProportionalBounds() {
        // Test N=10 -> K=2
        val cards10 = (0 until 10).map { makeCard("ep_$it", "s1") }
        val prev10 = cards10.map { it.id }
        val tail10 = prev10.takeLast(2).toSet()

        // Place tail item at index 0
        val perm10 = mutableListOf(cards10[9], cards10[0], cards10[1], cards10[2], cards10[3],
            cards10[4], cards10[5], cards10[6], cards10[7], cards10[8])

        val res10 = CrossCycleBuffer.bufferCrossCycle(perm10, prev10, 0.20f, Random(1))
        val head10 = res10.take(2).map { it.id }
        assertFalse("Head of 10 items should not have tail ep_9", head10.contains("ep_9"))
    }

    @Test
    fun testSmallDeckBypass() {
        val cards = listOf(makeCard("1", "s1"), makeCard("2", "s1"))
        val prev = listOf("1", "2")
        val result = CrossCycleBuffer.bufferCrossCycle(cards, prev, 0.20f)
        assertEquals(cards, result)
    }

    @Test
    fun testEmptyPreviousDeckBypass() {
        val cards = (1..5).map { makeCard("$it", "s1") }
        val result = CrossCycleBuffer.bufferCrossCycle(cards, emptyList(), 0.20f)
        assertEquals(cards, result)
    }
}
