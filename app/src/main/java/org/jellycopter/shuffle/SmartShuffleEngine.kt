package org.jellycopter.shuffle

import org.jellycopter.core.model.DeckConfig
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShowConfig
import org.jellycopter.core.model.ShuffledDeck
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.shuffle.model.DeckProgressionEvent
import org.jellycopter.shuffle.model.DeckState
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

interface SmartShuffleEngine {
    fun generateDeck(
        seriesId: String,
        episodes: List<EpisodeItem>,
        config: DeckConfig = DeckConfig()
    ): ShuffledDeck

    fun generateMixChannelDeck(
        channelId: String,
        shows: List<ShowConfig>,
        episodeProvider: (String) -> List<EpisodeItem>
    ): ShuffledDeck

    fun advancePlayback(
        deckId: String,
        currentCardId: String,
        positionMs: Long,
        durationMs: Long
    ): DeckProgressionEvent

    fun recycleSkipped(deckId: String, cardId: String)
}

class DefaultSmartShuffleEngine(
    private val deckRepository: DeckRepository? = null,
    private val random: Random = Random.Default
) : SmartShuffleEngine {

    private val activeDecks = ConcurrentHashMap<String, ShuffledDeck>()
    private val cachedConfigs = ConcurrentHashMap<String, DeckConfig>()
    private val watchHistoryDecoupler = WatchHistoryDecoupler(deckRepository)

    fun registerDeck(deck: ShuffledDeck, config: DeckConfig = DeckConfig()) {
        activeDecks[deck.deckId] = deck
        cachedConfigs[deck.deckId] = config
    }

    fun getDeck(deckId: String): ShuffledDeck? = activeDecks[deckId]

    fun getDeckState(deckId: String): DeckState? {
        val deck = activeDecks[deckId] ?: return null
        val exhaustedIds = deck.cards.filter { it.isExhausted }.map { it.id }.toSet()
        val cooldownHistory = deck.cards.take(deck.currentIndex).map { it.id }
        return DeckState(
            deckId = deck.deckId,
            cards = deck.cards,
            currentIndex = deck.currentIndex,
            exhaustedCardIds = exhaustedIds,
            cooldownHistory = cooldownHistory
        )
    }

    override fun generateDeck(
        seriesId: String,
        episodes: List<EpisodeItem>,
        config: DeckConfig
    ): ShuffledDeck {
        val deckId = "series:$seriesId"
        val previousDeck = activeDecks[deckId]

        // 1. Group episodes with 4-point invariant checks and hygiene filtering
        val cards = MultiPartGrouper.groupEpisodes(episodes)
        if (cards.isEmpty()) {
            val emptyDeck = ShuffledDeck(
                deckId = deckId,
                name = episodes.firstOrNull()?.seriesName ?: "Series $seriesId",
                seriesId = seriesId,
                strictQueueMode = config.strictQueueMode
            )
            registerDeck(emptyDeck, config)
            return emptyDeck
        }

        // 2. Anti-clustering round robin by season
        val balanced = AntiClusteringEngine.balanceAndShuffle(cards, random) { it.categoryKey }

        // 3. Dynamic decoupled cross-cycle buffering
        val finalCards = if (previousDeck != null && previousDeck.cards.isNotEmpty()) {
            val prevOrder = previousDeck.cards.flatMap { it.itemIds }
            CrossCycleBuffer.bufferCrossCycle(
                balanced,
                prevOrder,
                config.crossCycleBoundaryBufferRatio,
                random
            )
        } else {
            balanced
        }

        val deck = ShuffledDeck(
            deckId = deckId,
            name = episodes.firstOrNull()?.seriesName?.ifEmpty { "Series $seriesId" } ?: "Series $seriesId",
            seriesId = seriesId,
            cards = finalCards,
            currentIndex = 0,
            cycleNumber = (previousDeck?.cycleNumber ?: 0) + 1,
            midwayResumeMs = 0L,
            strictQueueMode = config.strictQueueMode,
            updatedAt = System.currentTimeMillis()
        )

        registerDeck(deck, config)
        return deck
    }

    override fun generateMixChannelDeck(
        channelId: String,
        shows: List<ShowConfig>,
        episodeProvider: (String) -> List<EpisodeItem>
    ): ShuffledDeck {
        val config = DeckConfig()
        val deck = MixChannelEngine.generateMixChannelDeck(
            channelId = channelId,
            channelName = channelId,
            shows = shows,
            episodeProvider = episodeProvider,
            config = config,
            strategy = "ROUND_ROBIN",
            random = random
        )
        registerDeck(deck, config)
        return deck
    }

    fun generateMixChannelDeck(
        channelId: String,
        channelName: String,
        shows: List<ShowConfig>,
        episodeProvider: (String) -> List<EpisodeItem>,
        config: DeckConfig = DeckConfig(),
        strategy: String = "ROUND_ROBIN",
        targetCardCount: Int? = null
    ): ShuffledDeck {
        val deck = MixChannelEngine.generateMixChannelDeck(
            channelId = channelId,
            channelName = channelName,
            shows = shows,
            episodeProvider = episodeProvider,
            config = config,
            strategy = strategy,
            targetCardCount = targetCardCount,
            random = random
        )
        registerDeck(deck, config)
        return deck
    }

    override fun advancePlayback(
        deckId: String,
        currentCardId: String,
        positionMs: Long,
        durationMs: Long
    ): DeckProgressionEvent {
        val deck = activeDecks[deckId] ?: throw IllegalArgumentException("Deck not found: $deckId")
        val config = cachedConfigs[deckId] ?: DeckConfig()

        val (updatedDeck, event) = advancePlayback(deck, currentCardId, positionMs, durationMs, config)
        activeDecks[deckId] = updatedDeck
        return event
    }

    fun advancePlayback(
        deck: ShuffledDeck,
        currentCardId: String,
        positionMs: Long,
        durationMs: Long,
        config: DeckConfig = cachedConfigs[deck.deckId] ?: DeckConfig()
    ): Pair<ShuffledDeck, DeckProgressionEvent> {
        val outcome = watchHistoryDecoupler.evaluateProgress(positionMs, durationMs)

        return when (outcome) {
            PlaybackOutcome.MIDWAY_RESUME -> {
                val updatedDeck = deck.copy(
                    midwayResumeMs = positionMs,
                    updatedAt = System.currentTimeMillis()
                )
                val event = DeckProgressionEvent.MidwayResume(
                    deckId = deck.deckId,
                    cardId = currentCardId,
                    currentIndex = deck.currentIndex,
                    positionMs = positionMs
                )
                Pair(updatedDeck, event)
            }

            PlaybackOutcome.COMPLETED -> {
                val updatedCards = deck.cards.mapIndexed { idx, card ->
                    if (idx == deck.currentIndex || card.id == currentCardId) {
                        card.copy(isExhausted = true)
                    } else {
                        card
                    }
                }

                val nextIndex = deck.currentIndex + 1
                val exhaustedCount = updatedCards.count { it.isExhausted }
                val totalCards = updatedCards.size
                val exhaustionRatio = if (totalCards > 0) exhaustedCount.toFloat() / totalCards else 0f

                // Cycle reset triggers when:
                // 1. currentIndex >= totalCards (100% exhaustion)
                // 2. OR exhaustionRatio >= cooldownThresholdRatio (>= 75% exhaustion)
                val isExhaustedThreshold = exhaustionRatio >= config.cooldownThresholdRatio
                val isEndReached = nextIndex >= totalCards

                if (isEndReached || isExhaustedThreshold) {
                    val prevOrder = updatedCards.flatMap { it.itemIds }
                    val freshCards = updatedCards.map { it.copy(isExhausted = false) }
                    val keySelector: (MediaCard) -> String = if (deck.channelId != null || deck.deckId.startsWith("channel:")) {
                        { card: MediaCard -> card.seriesId }
                    } else {
                        { card: MediaCard -> card.categoryKey }
                    }
                    val balanced = AntiClusteringEngine.balanceAndShuffle(freshCards, random, keySelector)
                    val buffered = CrossCycleBuffer.bufferCrossCycle(
                        balanced,
                        prevOrder,
                        config.crossCycleBoundaryBufferRatio,
                        random
                    )
                    val newCycleNumber = deck.cycleNumber + 1
                    val resetDeck = deck.copy(
                        cards = buffered,
                        currentIndex = 0,
                        cycleNumber = newCycleNumber,
                        midwayResumeMs = 0L,
                        updatedAt = System.currentTimeMillis()
                    )
                    val event = DeckProgressionEvent.Advanced(
                        deckId = deck.deckId,
                        completedCardId = currentCardId,
                        newIndex = 0,
                        nextCard = resetDeck.cards.firstOrNull(),
                        cycleReset = true,
                        newCycleNumber = newCycleNumber
                    )
                    Pair(resetDeck, event)
                } else {
                    val updatedDeck = deck.copy(
                        cards = updatedCards,
                        currentIndex = nextIndex,
                        midwayResumeMs = 0L,
                        updatedAt = System.currentTimeMillis()
                    )
                    val event = DeckProgressionEvent.Advanced(
                        deckId = deck.deckId,
                        completedCardId = currentCardId,
                        newIndex = nextIndex,
                        nextCard = updatedDeck.currentCard,
                        cycleReset = false,
                        newCycleNumber = deck.cycleNumber
                    )
                    Pair(updatedDeck, event)
                }
            }
        }
    }

    override fun recycleSkipped(deckId: String, cardId: String) {
        val deck = activeDecks[deckId] ?: return
        val updated = recycleSkipped(deck, cardId)
        activeDecks[deckId] = updated
    }

    fun recycleSkipped(deck: ShuffledDeck, cardId: String): ShuffledDeck {
        val curIdx = deck.currentIndex
        if (deck.cards.isEmpty() || curIdx !in deck.cards.indices) {
            return deck
        }

        val cardIndex = deck.cards.indexOfFirst { it.id == cardId }.takeIf { it >= 0 } ?: curIdx

        if (deck.strictQueueMode) {
            // Strict Queue Mode: consuming skips as exhausted
            val updatedCards = deck.cards.mapIndexed { idx, card ->
                if (idx == cardIndex) card.copy(isExhausted = true) else card
            }
            return deck.copy(
                cards = updatedCards,
                currentIndex = min(deck.cards.size, curIdx + 1),
                midwayResumeMs = 0L,
                updatedAt = System.currentTimeMillis()
            )
        }

        // Default Mode: card remains unexhausted and is recycled forward into the pool
        val mutableCards = deck.cards.toMutableList()
        val skippedCard = mutableCards.removeAt(cardIndex).copy(isExhausted = false)

        val remainingAhead = mutableCards.size - curIdx
        val offset = min(10, max(1, remainingAhead - 1))
        val targetIndex = min(mutableCards.size, curIdx + offset)

        mutableCards.add(targetIndex, skippedCard)

        return deck.copy(
            cards = mutableCards,
            currentIndex = curIdx, // Remains at curIdx because next card slid into slot curIdx
            midwayResumeMs = 0L,
            updatedAt = System.currentTimeMillis()
        )
    }
}
