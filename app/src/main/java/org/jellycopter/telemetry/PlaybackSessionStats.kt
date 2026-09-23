package org.jellycopter.telemetry

import java.util.Calendar
import java.util.UUID

/**
 * Encapsulates playback telemetry emitted when a media playback session terminates.
 */
data class PlaybackSessionStats(
    val sessionId: String = UUID.randomUUID().toString(),
    val mediaId: String,
    val seriesId: String? = null,
    val title: String = "",
    val durationMs: Long = 0L,
    val playedMs: Long = 0L,
    val completed: Boolean = true,
    val isShuffle: Boolean = false,
    val deckId: String? = null,
    val deckExhausted: Boolean = false,
    val activeDecksCount: Int = 1,
    val isForceStereo: Boolean = false,
    val isNightMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val pauseCount: Int = 0,
    val endedAtUtcMs: Long = System.currentTimeMillis(),
    val localHourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
)
