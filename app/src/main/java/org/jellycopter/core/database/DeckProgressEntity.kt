package org.jellycopter.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deck_progress")
data class DeckProgressEntity(
    @PrimaryKey
    val deckId: String,
    val currentCardId: String,
    val currentEpisodeId: String,
    val currentIndex: Int,
    val midwayResumeMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)
