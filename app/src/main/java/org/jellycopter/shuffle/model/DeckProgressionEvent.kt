package org.jellycopter.shuffle.model

import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.ShuffledDeck

sealed interface DeckProgressionEvent {
    data class MidwayResume(
        val deckId: String,
        val cardId: String,
        val currentIndex: Int,
        val positionMs: Long
    ) : DeckProgressionEvent

    data class Advanced(
        val deckId: String,
        val completedCardId: String,
        val newIndex: Int,
        val nextCard: MediaCard?,
        val cycleReset: Boolean = false,
        val newCycleNumber: Int = 1
    ) : DeckProgressionEvent

    data class CycleCompleted(
        val deckId: String,
        val cycleNumber: Int,
        val newDeck: ShuffledDeck
    ) : DeckProgressionEvent
}
