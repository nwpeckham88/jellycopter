package org.jellycopter.shuffle.model

import kotlinx.serialization.Serializable
import org.jellycopter.core.model.MediaCard

@Serializable
data class DeckState(
    val deckId: String,
    val cards: List<MediaCard>,
    val currentIndex: Int,
    val exhaustedCardIds: Set<String> = emptySet(),
    val cooldownHistory: List<String> = emptyList()
)
