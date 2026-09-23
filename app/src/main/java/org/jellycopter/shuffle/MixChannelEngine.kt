package org.jellycopter.shuffle

import org.jellycopter.core.model.DeckConfig
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShowConfig
import org.jellycopter.core.model.ShuffledDeck
import kotlin.math.max
import kotlin.random.Random

object MixChannelEngine {

    class ShowSubDeck(
        val showConfig: ShowConfig,
        private val episodeProvider: (String) -> List<EpisodeItem>,
        private val config: DeckConfig,
        private val random: Random
    ) {
        var cycleNumber: Int = 1
            private set
        var currentSubIndex: Int = 0
            private set
        private var subDeckCards: List<MediaCard> = emptyList()
        private var previousSubDeckOrder: List<String> = emptyList()

        init {
            initializeSubDeck()
        }

        private fun initializeSubDeck() {
            val rawEpisodes = episodeProvider(showConfig.seriesId)
            val filtered = if (showConfig.seasons != null) {
                rawEpisodes.filter { it.seasonNumber in showConfig.seasons }
            } else {
                rawEpisodes
            }

            val cards = MultiPartGrouper.groupEpisodes(filtered)
            if (cards.isEmpty()) {
                subDeckCards = emptyList()
                return
            }

            // Level 2: Season-level anti-clustering within each show
            val balanced = AntiClusteringEngine.balanceAndShuffle(cards, random) { it.categoryKey }
            subDeckCards = if (previousSubDeckOrder.isNotEmpty()) {
                CrossCycleBuffer.bufferCrossCycle(
                    balanced,
                    previousSubDeckOrder,
                    config.crossCycleBoundaryBufferRatio,
                    random
                )
            } else {
                balanced
            }
            currentSubIndex = 0
        }

        fun hasCards(): Boolean = subDeckCards.isNotEmpty()

        val totalEpisodesInShow: Int get() = subDeckCards.size

        fun nextCard(): MediaCard? {
            if (subDeckCards.isEmpty()) return null
            if (currentSubIndex >= subDeckCards.size) {
                // Independent sub-deck recycling with cross-cycle buffering
                previousSubDeckOrder = subDeckCards.flatMap { it.itemIds }
                cycleNumber++
                initializeSubDeck()
                if (subDeckCards.isEmpty()) return null
            }
            val card = subDeckCards[currentSubIndex]
            currentSubIndex++
            return card
        }
    }

    /**
     * Generates a multi-show mix channel deck with 2-level anti-clustering:
     * - Level 1: Show-level anti-clustering ensuring no two adjacent episodes from the same show.
     * - Level 2: Season-level anti-clustering within each participating show's sub-deck.
     * Each show recycles its own sub-deck independently when exhausted.
     */
    fun generateMixChannelDeck(
        channelId: String,
        channelName: String,
        shows: List<ShowConfig>,
        episodeProvider: (String) -> List<EpisodeItem>,
        config: DeckConfig = DeckConfig(),
        strategy: String = "ROUND_ROBIN",
        targetCardCount: Int? = null,
        random: Random = Random.Default
    ): ShuffledDeck {
        val cleanChannelId = channelId.removePrefix("channel:")
        val fullDeckId = if (channelId.startsWith("channel:")) channelId else "channel:$channelId"

        if (shows.isEmpty()) {
            return ShuffledDeck(
                deckId = fullDeckId,
                name = channelName,
                channelId = cleanChannelId
            )
        }

        // Initialize sub-decks for each participating show
        val subDecks = shows.map {
            ShowSubDeck(it, episodeProvider, config, random)
        }.filter { it.hasCards() }

        if (subDecks.isEmpty()) {
            return ShuffledDeck(
                deckId = fullDeckId,
                name = channelName,
                channelId = cleanChannelId
            )
        }

        // Determine total playlist length
        val sumShowEpisodes = subDecks.sumOf { it.totalEpisodesInShow }
        val maxShowEpisodes = subDecks.maxOf { it.totalEpisodesInShow }
        val totalLength = targetCardCount ?: max(sumShowEpisodes, maxShowEpisodes * subDecks.size)

        val interleavedCards = ArrayList<MediaCard>(totalLength)
        var lastSeriesId = ""
        var roundRobinIndex = 0

        for (step in 0 until totalLength) {
            // Level 1 candidates: active shows whose seriesId != lastSeriesId
            var candidates = subDecks.filter { it.showConfig.seriesId != lastSeriesId }
            if (candidates.isEmpty()) {
                candidates = subDecks
            }

            val selectedSubDeck: ShowSubDeck = if (candidates.size == 1) {
                candidates[0]
            } else {
                when (strategy.uppercase()) {
                    "ROUND_ROBIN" -> {
                        // Find next candidate in round-robin order through subDecks
                        var chosen: ShowSubDeck? = null
                        for (offset in subDecks.indices) {
                            val candidate = subDecks[(roundRobinIndex + offset) % subDecks.size]
                            if (candidate in candidates) {
                                chosen = candidate
                                roundRobinIndex = (roundRobinIndex + offset + 1) % subDecks.size
                                break
                            }
                        }
                        chosen ?: candidates.first()
                    }

                    "RANDOM" -> {
                        candidates[random.nextInt(candidates.size)]
                    }

                    "WEIGHTED" -> {
                        val weights = candidates.map { max(1, it.showConfig.weight) }
                        val totalWeight = weights.sum()
                        var roll = random.nextInt(totalWeight)
                        var chosen = candidates.last()
                        for (i in candidates.indices) {
                            roll -= weights[i]
                            if (roll < 0) {
                                chosen = candidates[i]
                                break
                            }
                        }
                        chosen
                    }

                    else -> {
                        // Default to round-robin for predictable variety
                        candidates[random.nextInt(candidates.size)]
                    }
                }
            }

            val card = selectedSubDeck.nextCard()
            if (card != null) {
                interleavedCards.add(card)
                lastSeriesId = card.seriesId
            }
        }

        return ShuffledDeck(
            deckId = fullDeckId,
            name = channelName,
            channelId = cleanChannelId,
            cards = interleavedCards,
            currentIndex = 0,
            cycleNumber = 1,
            midwayResumeMs = 0L,
            strictQueueMode = config.strictQueueMode,
            updatedAt = System.currentTimeMillis()
        )
    }
}
