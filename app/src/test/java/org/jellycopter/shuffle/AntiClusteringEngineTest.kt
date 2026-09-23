package org.jellycopter.shuffle

import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.random.Random

class AntiClusteringEngineTest {

    private fun createSyntheticCards(seasonSizes: Map<Int, Int>): List<MediaCard> {
        val cards = mutableListOf<MediaCard>()
        for ((seasonNum, count) in seasonSizes) {
            val seasonId = "season_$seasonNum"
            for (idx in 1..count) {
                val epId = "ep_s${seasonNum}_e$idx"
                val ep = EpisodeItem(
                    id = epId,
                    name = "Episode S${seasonNum}E$idx",
                    seriesId = "series_synthetic",
                    seasonId = seasonId,
                    seasonNumber = seasonNum,
                    indexNumber = idx,
                    runTimeTicks = 12_000_000_000L
                )
                cards.add(
                    MediaCard(
                        id = epId,
                        seriesId = "series_synthetic",
                        seasonId = seasonId,
                        categoryKey = seasonId,
                        items = listOf(ep),
                        seasonNumber = seasonNum
                    )
                )
            }
        }
        return cards
    }

    private fun calculateClashRate(shuffled: List<MediaCard>): Double {
        if (shuffled.size <= 1) return 0.0
        var clashes = 0
        for (i in 0 until shuffled.size - 1) {
            if (shuffled[i].categoryKey == shuffled[i + 1].categoryKey) {
                clashes++
            }
        }
        return clashes.toDouble() / (shuffled.size - 1).toDouble()
    }

    private fun shannonEntropy(counts: Map<*, Int>, total: Int): Double {
        var entropy = 0.0
        for ((_, count) in counts) {
            if (count > 0) {
                val p = count.toDouble() / total.toDouble()
                entropy -= p * log2(p)
            }
        }
        return entropy
    }

    @Test
    fun testUnevenSeasonsZeroClash() {
        // Highly uneven distribution: S1: 6 episodes, S2: 24 episodes, S3: 24 episodes (N=54)
        val cards = createSyntheticCards(mapOf(1 to 6, 2 to 24, 3 to 24))
        val trials = 100
        var totalClashes = 0

        for (t in 0 until trials) {
            val random = Random(1000 + t)
            val shuffled = AntiClusteringEngine.balanceAndShuffle(cards, random)
            assertEquals(54, shuffled.size)
            val clashRate = calculateClashRate(shuffled)
            if (clashRate > 0.0) {
                totalClashes++
            }
        }

        // Guarantees 0.00% adjacent clashes
        assertEquals("Clash rate must be exactly 0.00%", 0, totalClashes)
    }

    @Test
    fun testBobsBurgersZeroClash() {
        // Bob's Burgers catalog: 14 seasons, ~315 episodes
        val bobsBurgersSeasons = mapOf(
            1 to 13, 2 to 9, 3 to 23, 4 to 22, 5 to 21, 6 to 19, 7 to 22,
            8 to 21, 9 to 22, 10 to 22, 11 to 22, 12 to 22, 13 to 22, 14 to 16
        )
        val cards = createSyntheticCards(bobsBurgersSeasons)
        val expectedTotal = bobsBurgersSeasons.values.sum()
        assertEquals(expectedTotal, cards.size)

        val trials = 100
        var totalClashes = 0

        for (t in 0 until trials) {
            val random = Random(2000 + t)
            val shuffled = AntiClusteringEngine.balanceAndShuffle(cards, random)
            assertEquals(expectedTotal, shuffled.size)
            val clashRate = calculateClashRate(shuffled)
            if (clashRate > 0.0) {
                totalClashes++
            }
        }

        assertEquals("Bob's Burgers must have 0.00% season clashes", 0, totalClashes)
    }

    @Test
    fun testShannonEntropyBenchmark() {
        // Benchmark from test_shuffle_entropy.py with 60 trials on a 315-episode catalog
        val seasons = (1..14).associateWith { 22 } // 14 seasons of 22 episodes = 308 episodes
        val cards = createSyntheticCards(seasons)
        val trials = 60
        val n = cards.size

        val firstItems = mutableListOf<String>()
        val allRuns = mutableListOf<List<String>>()

        for (t in 0 until trials) {
            val random = Random(42 + t)
            val shuffled = AntiClusteringEngine.balanceAndShuffle(cards, random)
            val ids = shuffled.map { it.id }
            firstItems.add(ids.first())
            allRuns.add(ids)
        }

        // 1. First Item Entropy
        val firstCounts = firstItems.groupingBy { it }.eachCount()
        val firstEntropy = shannonEntropy(firstCounts, firstItems.size)
        val maxFirstEntropy = log2(min(trials, n).toDouble())
        val firstRatio = firstEntropy / maxFirstEntropy

        // 2. Average Positional Entropy
        val itemPositions = mutableMapOf<String, MutableList<Int>>()
        for (run in allRuns) {
            for ((idx, id) in run.withIndex()) {
                itemPositions.getOrPut(id) { mutableListOf() }.add(idx)
            }
        }

        var sumPosEntropy = 0.0
        for ((_, positions) in itemPositions) {
            val posCounts = positions.groupingBy { it }.eachCount()
            sumPosEntropy += shannonEntropy(posCounts, positions.size)
        }
        val avgPosEntropy = sumPosEntropy / itemPositions.size
        val maxPosEntropy = log2(trials.toDouble())
        val posRatio = avgPosEntropy / maxPosEntropy

        // 3. Transition Entropy (adjacent pairs)
        val pairCounts = mutableMapOf<Pair<String, String>, Int>()
        var totalPairs = 0
        for (run in allRuns) {
            for (j in 0 until run.size - 1) {
                val pair = Pair(run[j], run[j + 1])
                pairCounts[pair] = (pairCounts[pair] ?: 0) + 1
                totalPairs++
            }
        }
        val transitionEntropy = shannonEntropy(pairCounts, totalPairs)
        val maxTransitionEntropy = log2(totalPairs.toDouble())
        val transitionRatio = transitionEntropy / maxTransitionEntropy

        println("Entropy Benchmark Results:")
        println("  First Item Entropy:     %.3f bits (Max: %.3f, Ratio: %.1f%%)".format(firstEntropy, maxFirstEntropy, firstRatio * 100))
        println("  Avg Positional Entropy: %.3f bits (Max: %.3f, Ratio: %.1f%%)".format(avgPosEntropy, maxPosEntropy, posRatio * 100))
        println("  Transition Entropy:     %.3f bits (Max: %.3f, Ratio: %.1f%%)".format(transitionEntropy, maxTransitionEntropy, transitionRatio * 100))

        // Requirement: First item entropy >= 90% of max
        assertTrue("First item entropy ratio ($firstRatio) should be >= 0.90", firstRatio >= 0.90)

        // Requirement: Average positional entropy >= 90% of max
        assertTrue("Positional entropy ratio ($posRatio) should be >= 0.90", posRatio >= 0.90)

        // Requirement: Transition entropy >= 85% of max
        assertTrue("Transition entropy ratio ($transitionRatio) should be >= 0.85", transitionRatio >= 0.85)
    }

    @Test
    fun testSingleSeasonGracefulFallback() {
        val cards = createSyntheticCards(mapOf(1 to 10))
        val shuffled = AntiClusteringEngine.balanceAndShuffle(cards, Random(123))
        assertEquals(10, shuffled.size)
    }
}
