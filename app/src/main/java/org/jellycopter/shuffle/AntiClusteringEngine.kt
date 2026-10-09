package org.jellycopter.shuffle

import org.jellycopter.core.model.MediaCard
import kotlin.math.ceil
import kotlin.random.Random

object AntiClusteringEngine {

    /**
     * Balances and shuffles MediaCards using Tightness-Aware Weighted Round-Robin.
     * Guarantees 0.00% adjacent category clashes whenever max(bucket_size) <= ceil(N / 2).
     *
     * @param cards The media cards to shuffle.
     * @param random Random generator for reproducible tests.
     * @param keySelector Function extracting the clustering category key (e.g. seasonId or seriesId).
     */
    fun balanceAndShuffle(
        cards: List<MediaCard>,
        random: Random = Random.Default,
        keySelector: (MediaCard) -> String = { it.categoryKey }
    ): List<MediaCard> {
        if (cards.size <= 1) return cards

        // 1. Group cards into buckets by category
        val buckets = LinkedHashMap<String, MutableList<MediaCard>>()
        for (card in cards) {
            val key = keySelector(card)
            buckets.getOrPut(key) { mutableListOf() }.add(card)
        }

        // 2. Pre-shuffle and sort cards within each bucket by playCount + random perturbation
        for ((_, bucketList) in buckets) {
            bucketList.shuffle(random)
            bucketList.sortBy { it.playCount + random.nextDouble() * 1.5 }
        }

        val shuffled = ArrayList<MediaCard>(cards.size)
        var lastCategoryKey = ""

        // 3. Tightness-aware selection loop
        while (true) {
            val activeKeys = buckets.entries.filter { it.value.isNotEmpty() }.map { it.key }
            if (activeKeys.isEmpty()) break

            // Eligible candidates are active keys different from the last emitted category
            var candidates = activeKeys.filter { it != lastCategoryKey }
            if (candidates.isEmpty()) {
                // Deadlock / unavoidable collision: only lastCategoryKey has cards remaining
                candidates = activeKeys
            }

            val remainingTotal = activeKeys.sumOf { buckets[it]?.size ?: 0 }
            val tightnessThreshold = ceil(remainingTotal / 2.0).toInt()

            // Check Tightness Constraint (Pigeonhole Threshold):
            // If any candidate has size >= ceil(remainingTotal / 2), it MUST be selected immediately
            val tightCandidate = candidates.find { (buckets[it]?.size ?: 0) >= tightnessThreshold }

            val selectedKey = if (tightCandidate != null) {
                tightCandidate
            } else {
                val weights = candidates.map { buckets[it]?.size ?: 0 }
                weightedRandomChoice(candidates, weights, random)
            }

            val chosenBucket = buckets[selectedKey]
            if (chosenBucket.isNullOrEmpty()) {
                // If selected bucket is empty, pick any non-empty active key
                val fallbackKey = activeKeys.firstOrNull { (buckets[it]?.size ?: 0) > 0 } ?: break
                val fallbackBucket = buckets[fallbackKey] ?: break
                if (fallbackBucket.isEmpty()) break
                val card = fallbackBucket.removeAt(0)
                shuffled.add(card)
                lastCategoryKey = fallbackKey
            } else {
                val card = chosenBucket.removeAt(0)
                shuffled.add(card)
                lastCategoryKey = selectedKey
            }
        }

        return shuffled
    }

    private fun weightedRandomChoice(
        candidates: List<String>,
        weights: List<Int>,
        random: Random
    ): String {
        val totalWeight = weights.sum()
        if (totalWeight <= 0) return candidates.random(random)
        var roll = random.nextInt(totalWeight)
        for (i in candidates.indices) {
            roll -= weights[i]
            if (roll < 0) {
                return candidates[i]
            }
        }
        return candidates.last()
    }
}
