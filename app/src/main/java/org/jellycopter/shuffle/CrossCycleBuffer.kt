package org.jellycopter.shuffle

import org.jellycopter.core.model.MediaCard
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object CrossCycleBuffer {

    /**
     * Applies dynamic decoupled cross-cycle buffering with category-safe floor swaps.
     * Prevents items played near the end of the previous cycle from repeating near the start
     * of the new cycle.
     *
     * @param cards Shuffled media cards for the new cycle.
     * @param prevDeckOrder List of item IDs (or card IDs) in the exact order of the previous deck.
     * @param bufferRatio Proportion of previous deck size used for buffer K (default 0.20).
     * @param random Random generator for swap selection.
     */
    fun bufferCrossCycle(
        cards: List<MediaCard>,
        prevDeckOrder: List<String>,
        bufferRatio: Float = 0.20f,
        random: Random = Random.Default
    ): List<MediaCard> {
        val n = prevDeckOrder.size
        if (n == 0 || cards.size <= 3) {
            return cards
        }

        // 1. Calculate proportional boundary buffer K = min(30, max(2, floor(bufferRatio * N)))
        var k = min(30, max(2, floor(bufferRatio * n).toInt()))
        if (k >= cards.size) {
            k = cards.size / 2
        }
        if (k <= 0) return cards

        // 2. Map item positions from previous cycle
        val prevIndexMap = HashMap<String, Int>(n)
        for (i in prevDeckOrder.indices) {
            prevIndexMap[prevDeckOrder[i]] = i
        }
        val tailThreshold = n - k

        fun isTailCard(card: MediaCard): Boolean {
            // Check by card ID
            val cardPos = prevIndexMap[card.id]
            if (cardPos != null && cardPos >= tailThreshold) return true
            // Check by individual item IDs
            for (id in card.itemIds) {
                val pos = prevIndexMap[id]
                if (pos != null && pos >= tailThreshold) return true
            }
            return false
        }

        val result = cards.toMutableList()
        val hasMultipleCategories = result.map { it.categoryKey }.distinct().size > 1

        // 3. Inspect first K slots of the new deck
        for (i in 0 until min(k, result.size)) {
            if (isTailCard(result[i])) {
                var targetIdx = -1

                // Attempt random non-tail swap partner with category safety
                for (attempt in 0 until 50) {
                    val j = random.nextInt(k, result.size)
                    if (!isTailCard(result[j]) && isCategorySafeSwap(result, i, j, hasMultipleCategories)) {
                        targetIdx = j
                        break
                    }
                }

                // If random sampling did not find one, perform deterministic scan
                if (targetIdx == -1) {
                    for (j in k until result.size) {
                        if (!isTailCard(result[j]) && isCategorySafeSwap(result, i, j, hasMultipleCategories)) {
                            targetIdx = j
                            break
                        }
                    }
                }

                // If still not found and single category, swap with any non-tail slot >= K
                if (targetIdx == -1 && !hasMultipleCategories) {
                    for (j in k until result.size) {
                        if (!isTailCard(result[j])) {
                            targetIdx = j
                            break
                        }
                    }
                }

                if (targetIdx != -1) {
                    val temp = result[i]
                    result[i] = result[targetIdx]
                    result[targetIdx] = temp
                }
            }
        }

        return result
    }

    private fun isCategorySafeSwap(
        list: List<MediaCard>,
        i: Int,
        j: Int,
        checkCategories: Boolean
    ): Boolean {
        if (!checkCategories) return true

        val catI = list[i].categoryKey
        val catJ = list[j].categoryKey

        // Safety for placing card I at slot J
        val safeIAtJ = (j == 0 || (j - 1 != i && list[j - 1].categoryKey != catI)) &&
                (j + 1 >= list.size || list[j + 1].categoryKey != catI)

        // Safety for placing card J at slot I
        val safeJAtI = (i == 0 || list[i - 1].categoryKey != catJ) &&
                (i + 1 >= list.size || (i + 1 != j && list[i + 1].categoryKey != catJ))

        return safeIAtJ && safeJAtI
    }
}
