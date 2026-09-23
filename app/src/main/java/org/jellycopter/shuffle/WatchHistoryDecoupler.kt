package org.jellycopter.shuffle

import org.jellycopter.core.database.DeckProgressEntity
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.repository.DeckRepository

enum class PlaybackSource {
    SHUFFLE_DECK,
    MIX_CHANNEL,
    SEQUENTIAL_NEXT_UP
}

enum class PlaybackOutcome {
    MIDWAY_RESUME,
    COMPLETED
}

class WatchHistoryDecoupler(
    private val deckRepository: DeckRepository? = null
) {

    /**
     * Determines whether Jellyfin server scrobble requests (/Sessions/Playing/Stopped or /Progress)
     * should be suppressed to prevent clobbering the sequential "Next Up" milestone.
     */
    fun shouldSuppressJellyfinScrobble(source: PlaybackSource): Boolean {
        return when (source) {
            PlaybackSource.SHUFFLE_DECK, PlaybackSource.MIX_CHANNEL -> true
            PlaybackSource.SEQUENTIAL_NEXT_UP -> false
        }
    }

    /**
     * Sanitizes an episode's metadata before handing it off to the player queue,
     * ensuring that player telemetry cannot accidentally overwrite server sequential milestones.
     */
    fun sanitizeEpisodeForPlayback(episode: EpisodeItem): EpisodeItem {
        return episode.copy(
            playCount = 0.0
        )
    }

    /**
     * Evaluates continuous autoplay progression:
     * - If watched >= 80% or remaining <= 90 seconds (near credits), returns COMPLETED.
     * - Otherwise returns MIDWAY_RESUME.
     */
    fun evaluateProgress(positionMs: Long, durationMs: Long): PlaybackOutcome {
        if (durationMs <= 0L) return PlaybackOutcome.MIDWAY_RESUME

        val percentage = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
        val remainingMs = durationMs - positionMs
        val nearCredits = durationMs > 90_000L && remainingMs <= 90_000L && percentage >= 50.0

        return if (percentage >= 80.0 || nearCredits) {
            PlaybackOutcome.COMPLETED
        } else {
            PlaybackOutcome.MIDWAY_RESUME
        }
    }

    /**
     * Records deck playback progress in the local Room DB via DeckRepository without
     * sending any scrobble events to Jellyfin server.
     */
    suspend fun recordDeckProgress(
        deckId: String,
        card: MediaCard,
        currentIndex: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        val currentEpisodeId = card.items.firstOrNull()?.id ?: card.id
        deckRepository?.saveProgress(
            deckId = deckId,
            currentCardId = card.id,
            currentEpisodeId = currentEpisodeId,
            currentIndex = currentIndex,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    /**
     * Retrieves saved progress from Room DB.
     */
    suspend fun getDeckProgress(deckId: String): DeckProgressEntity? {
        return deckRepository?.getProgress(deckId)
    }

    /**
     * Clears progress for a given deck.
     */
    suspend fun clearDeckProgress(deckId: String) {
        deckRepository?.deleteProgress(deckId)
    }
}
