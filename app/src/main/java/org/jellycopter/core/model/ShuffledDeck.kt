package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ShuffledDeck(
    val deckId: String,
    val name: String,
    val seriesId: String? = null,
    val channelId: String? = null,
    val cards: List<MediaCard> = emptyList(),
    val currentIndex: Int = 0,
    val cycleNumber: Int = 1,
    val midwayResumeMs: Long = 0L,
    val strictQueueMode: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val currentCard: MediaCard? get() = cards.getOrNull(currentIndex)
    val isExhausted: Boolean get() = currentIndex >= cards.size
    val progressPercent: Float get() = if (cards.isEmpty()) 0f else (currentIndex.toFloat() / cards.size.toFloat()) * 100f
}
