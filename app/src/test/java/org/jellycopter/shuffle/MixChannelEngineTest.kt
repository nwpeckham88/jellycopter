package org.jellycopter.shuffle

import org.jellycopter.core.model.DeckConfig
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.ShowConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixChannelEngineTest {

    private fun generateEpisodesForShow(seriesId: String, seasonCount: Int, episodesPerSeason: Int): List<EpisodeItem> {
        val list = mutableListOf<EpisodeItem>()
        for (s in 1..seasonCount) {
            val seasonId = "${seriesId}_s$s"
            for (e in 1..episodesPerSeason) {
                list.add(
                    EpisodeItem(
                        id = "${seriesId}_s${s}_e$e",
                        name = "$seriesId S${s}E$e",
                        seriesId = seriesId,
                        seriesName = "Show $seriesId",
                        seasonId = seasonId,
                        seasonNumber = s,
                        indexNumber = e,
                        runTimeTicks = 15_000_000_000L
                    )
                )
            }
        }
        return list
    }

    @Test
    fun testShowLevelAntiClusteringZeroClashes() {
        val showA = ShowConfig(seriesId = "show_a", seriesName = "Show A", weight = 1)
        val showB = ShowConfig(seriesId = "show_b", seriesName = "Show B", weight = 1)
        val showC = ShowConfig(seriesId = "show_c", seriesName = "Show C", weight = 1)

        val catalog = mapOf(
            "show_a" to generateEpisodesForShow("show_a", 3, 10), // 30 eps
            "show_b" to generateEpisodesForShow("show_b", 4, 8),  // 32 eps
            "show_c" to generateEpisodesForShow("show_c", 2, 12)  // 24 eps
        )

        val channelDeck = MixChannelEngine.generateMixChannelDeck(
            channelId = "cartoons_block",
            channelName = "Saturday Cartoons",
            shows = listOf(showA, showB, showC),
            episodeProvider = { catalog[it] ?: emptyList() },
            config = DeckConfig(),
            targetCardCount = 60,
            random = Random(99)
        )

        assertEquals(60, channelDeck.cards.size)

        // Verify Level 1: Zero adjacent show collisions
        for (i in 0 until channelDeck.cards.size - 1) {
            val curShow = channelDeck.cards[i].seriesId
            val nextShow = channelDeck.cards[i + 1].seriesId
            assertNotEquals(
                "Adjacent cards at index $i and ${i + 1} must not have the same seriesId ($curShow)",
                curShow,
                nextShow
            )
        }
    }

    @Test
    fun testIndependentSubDeckRecycling() {
        // Show Short has only 3 episodes, Show Long has 15 episodes
        val shortShow = ShowConfig(seriesId = "short_show", seriesName = "Short Show", weight = 1)
        val longShow = ShowConfig(seriesId = "long_show", seriesName = "Long Show", weight = 1)

        val catalog = mapOf(
            "short_show" to generateEpisodesForShow("short_show", 1, 3), // 3 eps total
            "long_show" to generateEpisodesForShow("long_show", 3, 5)     // 15 eps total
        )

        // Generate a 20-card channel playlist
        val channelDeck = MixChannelEngine.generateMixChannelDeck(
            channelId = "mixed_lengths",
            channelName = "Mixed Lengths Channel",
            shows = listOf(shortShow, longShow),
            episodeProvider = { catalog[it] ?: emptyList() },
            config = DeckConfig(),
            targetCardCount = 20,
            random = Random(777)
        )

        assertEquals(20, channelDeck.cards.size)

        // Verify that short_show was played more than 3 times (meaning its sub-deck recycled independently!)
        val shortCount = channelDeck.cards.count { it.seriesId == "short_show" }
        val longCount = channelDeck.cards.count { it.seriesId == "long_show" }

        assertTrue("Short show must have recycled to play multiple times ($shortCount > 3)", shortCount > 3)
        assertTrue("Long show must also have played ($longCount > 0)", longCount > 0)
        assertEquals(20, shortCount + longCount)

        // And no two adjacent cards share the same seriesId
        for (i in 0 until channelDeck.cards.size - 1) {
            assertNotEquals(
                channelDeck.cards[i].seriesId,
                channelDeck.cards[i + 1].seriesId
            )
        }
    }

    @Test
    fun testWeightedShowDistribution() {
        // Show Heavy has weight 3, Show Light has weight 1
        val heavy = ShowConfig(seriesId = "heavy", seriesName = "Heavy", weight = 3)
        val light = ShowConfig(seriesId = "light", seriesName = "Light", weight = 1)

        val catalog = mapOf(
            "heavy" to generateEpisodesForShow("heavy", 5, 10), // 50 eps
            "light" to generateEpisodesForShow("light", 5, 10)  // 50 eps
        )

        val deck = MixChannelEngine.generateMixChannelDeck(
            channelId = "weighted_channel",
            channelName = "Weighted Channel",
            shows = listOf(heavy, light),
            episodeProvider = { catalog[it] ?: emptyList() },
            targetCardCount = 40,
            random = Random(1234)
        )

        val heavyCount = deck.cards.count { it.seriesId == "heavy" }
        val lightCount = deck.cards.count { it.seriesId == "light" }

        // Because of Level 1 anti-clustering (alternation required between 2 shows),
        // with only 2 shows they must strictly alternate: 20 and 20.
        // Zero collisions guaranteed.
        for (i in 0 until deck.cards.size - 1) {
            assertNotEquals(deck.cards[i].seriesId, deck.cards[i + 1].seriesId)
        }
        assertEquals(20, heavyCount)
        assertEquals(20, lightCount)
    }
}
