package org.jellycopter.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.shuffle.DefaultSmartShuffleEngine
import org.jellycopter.shuffle.PlaybackOutcome
import org.jellycopter.shuffle.SmartShuffleEngine
import org.jellycopter.shuffle.WatchHistoryDecoupler

/**
 * Source of a playback session — determines scrobble suppression and episode-end behavior.
 */
enum class CoordinatorSource {
    /** Standard Jellyfin playback. Scrobbles normally. Auto-play next via Jellyfin. */
    NORMAL,
    /** Show shuffle deck. Suppress scrobble. Advance deck on end. */
    SHUFFLE,
    /** Channel playback. Suppress scrobble. Advance channel deck on end. */
    CHANNEL
}

/**
 * What the coordinator should do after the current episode ends.
 */
sealed class NextAction {
    /** Play the given episode next. */
    data class PlayNext(val episode: EpisodeItem, val startPositionMs: Long = 0L) : NextAction()
    /** Session is finished (end of normal playback with no auto-play). */
    object Stop : NextAction()
    /** Let Jellyfin's own auto-play mechanism handle the next episode (NORMAL source). */
    object JellyfinAutoPlay : NextAction()
}

/**
 * Active playback session context.
 */
data class PlaybackContext(
    val itemId: String,
    val episode: EpisodeItem,
    val source: CoordinatorSource = CoordinatorSource.NORMAL,
    val deckId: String? = null,
    val channelId: String? = null,
    val startPositionMs: Long = 0L
)

/**
 * Coordinator that sits above [PlaybackManager] and decides:
 * 1. Whether to suppress Jellyfin scrobble calls (for SHUFFLE/CHANNEL sources)
 * 2. What to do when an episode ends or is skipped
 *
 * NORMAL source → scrobbles normally, delegates next-episode handling to Jellyfin.
 * SHUFFLE/CHANNEL sources → suppress scrobble, advance local deck state, and queue next card.
 */
class PlaybackCoordinator(
    private val watchHistoryDecoupler: WatchHistoryDecoupler,
    private val deckRepository: DeckRepository,
    private val mediaRepository: MediaRepository,
    private val shuffleEngine: SmartShuffleEngine = DefaultSmartShuffleEngine(deckRepository),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val _currentContext = MutableStateFlow<PlaybackContext?>(null)
    val currentContext: StateFlow<PlaybackContext?> = _currentContext.asStateFlow()

    private val _scrobbleSuppressed = MutableStateFlow(false)
    /** True when the active session should not report progress to Jellyfin server. */
    val scrobbleSuppressed: StateFlow<Boolean> = _scrobbleSuppressed.asStateFlow()

    /**
     * Called when a new playback session starts.
     * Configures scrobble suppression based on source.
     */
    fun onPlaybackStarted(context: PlaybackContext) {
        _currentContext.value = context
        _scrobbleSuppressed.value = when (context.source) {
            CoordinatorSource.NORMAL -> false
            CoordinatorSource.SHUFFLE, CoordinatorSource.CHANNEL -> true
        }
    }

    /**
     * Called when the current episode ends (watched >= 80% or <= 90s remaining).
     * Returns what the UI/player should do next.
     */
    suspend fun onEpisodeCompleted(positionMs: Long, durationMs: Long): NextAction {
        val ctx = _currentContext.value ?: return NextAction.Stop
        val outcome = watchHistoryDecoupler.evaluateProgress(positionMs, durationMs)

        return when (ctx.source) {
            CoordinatorSource.NORMAL -> {
                // Normal playback: advance to next up if available, else delegate to Jellyfin
                val seriesId = ctx.episode.seriesId
                val nextUp = if (seriesId.isNotBlank()) mediaRepository.getNextUpForSeries(seriesId) else null
                if (nextUp != null && nextUp.id != ctx.episode.id) {
                    val nextCtx = ctx.copy(itemId = nextUp.id, episode = nextUp, startPositionMs = 0L)
                    _currentContext.value = nextCtx
                    NextAction.PlayNext(nextUp)
                } else {
                    NextAction.JellyfinAutoPlay
                }
            }

            CoordinatorSource.SHUFFLE -> {
                val deckId = ctx.deckId ?: return NextAction.Stop
                if (outcome == PlaybackOutcome.COMPLETED) {
                    advanceShuffleDeck(deckId, ctx.episode)
                } else {
                    saveCurrentProgress(positionMs, durationMs)
                    NextAction.Stop
                }
            }

            CoordinatorSource.CHANNEL -> {
                val deckId = ctx.deckId
                    ?: ctx.channelId?.let { if (it.startsWith("channel:")) it else "channel:$it" }
                    ?: return NextAction.Stop
                if (outcome == PlaybackOutcome.COMPLETED) {
                    advanceShuffleDeck(deckId, ctx.episode)
                } else {
                    saveCurrentProgress(positionMs, durationMs)
                    NextAction.Stop
                }
            }
        }
    }

    /**
     * Persists midway playback progress to Room DB without triggering server scrobbles.
     */
    suspend fun saveCurrentProgress(positionMs: Long, durationMs: Long) {
        val ctx = _currentContext.value ?: return
        if (ctx.source == CoordinatorSource.SHUFFLE || ctx.source == CoordinatorSource.CHANNEL) {
            val deckId = ctx.deckId
                ?: ctx.channelId?.let { if (it.startsWith("channel:")) it else "channel:$it" }
                ?: return
            val resolvedDeckId = if (deckRepository.getDeck(deckId) != null) {
                deckId
            } else if (deckId.startsWith("channel:") && deckRepository.getDeck(deckId.removePrefix("channel:")) != null) {
                deckId.removePrefix("channel:")
            } else if (!deckId.startsWith("channel:") && deckRepository.getDeck("channel:$deckId") != null) {
                "channel:$deckId"
            } else {
                deckId
            }

            val deck = deckRepository.getDeck(resolvedDeckId)
            val card = deck?.currentCard ?: org.jellycopter.core.model.MediaCard(
                id = ctx.episode.id,
                seriesId = ctx.episode.seriesId,
                seasonId = ctx.episode.seasonId,
                seasonNumber = ctx.episode.seasonNumber,
                categoryKey = "s${ctx.episode.seasonNumber}",
                items = listOf(ctx.episode)
            )
            watchHistoryDecoupler.recordDeckProgress(
                deckId = resolvedDeckId,
                card = card,
                currentIndex = deck?.currentIndex ?: 0,
                positionMs = positionMs,
                durationMs = durationMs
            )
            deck?.let {
                deckRepository.saveDeck(it.copy(midwayResumeMs = positionMs, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    /**
     * Called when the user skips the current episode.
     */
    suspend fun onEpisodeSkipped(): NextAction {
        val ctx = _currentContext.value ?: return NextAction.Stop
        return when (ctx.source) {
            CoordinatorSource.NORMAL -> {
                val seriesId = ctx.episode.seriesId
                val nextUp = if (seriesId.isNotBlank()) mediaRepository.getNextUpForSeries(seriesId) else null
                if (nextUp != null && nextUp.id != ctx.episode.id) {
                    val nextCtx = ctx.copy(itemId = nextUp.id, episode = nextUp, startPositionMs = 0L)
                    _currentContext.value = nextCtx
                    NextAction.PlayNext(nextUp)
                } else {
                    NextAction.JellyfinAutoPlay
                }
            }
            CoordinatorSource.SHUFFLE, CoordinatorSource.CHANNEL -> {
                val deckId = ctx.deckId
                    ?: ctx.channelId?.let { if (it.startsWith("channel:")) it else "channel:$it" }
                    ?: return NextAction.Stop
                val resolvedDeckId = if (deckRepository.getDeck(deckId) != null) {
                    deckId
                } else if (deckId.startsWith("channel:") && deckRepository.getDeck(deckId.removePrefix("channel:")) != null) {
                    deckId.removePrefix("channel:")
                } else if (!deckId.startsWith("channel:") && deckRepository.getDeck("channel:$deckId") != null) {
                    "channel:$deckId"
                } else {
                    deckId
                }

                val deck = deckRepository.getDeck(resolvedDeckId) ?: return NextAction.Stop
                val currentCard = deck.currentCard ?: deck.cards.firstOrNull() ?: return NextAction.Stop

                val updatedDeck = (shuffleEngine as? DefaultSmartShuffleEngine)?.recycleSkipped(deck, currentCard.id)
                    ?: run {
                        val nextIndex = (deck.currentIndex + 1) % deck.cards.size.coerceAtLeast(1)
                        deck.copy(currentIndex = nextIndex)
                    }
                deckRepository.saveDeck(updatedDeck)
                val nextEpisode = updatedDeck.currentCard?.items?.firstOrNull() ?: return NextAction.Stop
                val nextCtx = ctx.copy(itemId = nextEpisode.id, episode = nextEpisode, startPositionMs = 0L)
                _currentContext.value = nextCtx
                NextAction.PlayNext(nextEpisode)
            }
        }
    }

    /**
     * Advances a shuffle/channel deck to the next card and returns the next episode to play.
     */
    private suspend fun advanceShuffleDeck(deckId: String, completedEpisode: EpisodeItem): NextAction {
        val resolvedDeckId = if (deckRepository.getDeck(deckId) != null) {
            deckId
        } else if (deckId.startsWith("channel:") && deckRepository.getDeck(deckId.removePrefix("channel:")) != null) {
            deckId.removePrefix("channel:")
        } else if (!deckId.startsWith("channel:") && deckRepository.getDeck("channel:$deckId") != null) {
            "channel:$deckId"
        } else {
            deckId
        }

        val deck = deckRepository.getDeck(resolvedDeckId) ?: return NextAction.Stop
        if (deck.cards.isEmpty()) return NextAction.Stop

        val currentCard = deck.currentCard ?: deck.cards.first()
        val durationMs = if (completedEpisode.runTimeTicks > 0) completedEpisode.runTimeTicks / 10_000L else 1_800_000L

        val updatedDeck = (shuffleEngine as? DefaultSmartShuffleEngine)?.advancePlayback(
            deck = deck,
            currentCardId = currentCard.id,
            positionMs = durationMs,
            durationMs = durationMs
        )?.first ?: run {
            val nextIndex = (deck.currentIndex + 1) % deck.cards.size.coerceAtLeast(1)
            val newCycle = if (nextIndex == 0) deck.cycleNumber + 1 else deck.cycleNumber
            deck.copy(currentIndex = nextIndex, cycleNumber = newCycle)
        }

        deckRepository.saveDeck(updatedDeck)

        val nextEpisode = updatedDeck.currentCard?.items?.firstOrNull() ?: return NextAction.Stop
        _currentContext.value?.let { ctx ->
            _currentContext.value = ctx.copy(itemId = nextEpisode.id, episode = nextEpisode, startPositionMs = 0L)
        }
        return NextAction.PlayNext(nextEpisode)
    }

    fun clearSession() {
        _currentContext.value = null
        _scrobbleSuppressed.value = false
    }
}
