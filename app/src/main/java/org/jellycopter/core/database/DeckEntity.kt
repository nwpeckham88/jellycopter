package org.jellycopter.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey
    val deckId: String,
    val name: String,
    val seriesId: String? = null,
    val channelId: String? = null,
    val cycleNumber: Int = 1,
    val currentIndex: Int = 0,
    val cardsJson: String = "[]",
    val exhaustedCardIdsJson: String = "[]",
    val strictQueueMode: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAtUtc: String? = null
)
