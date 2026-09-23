package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackState(
    val deckId: String? = null,
    val currentCardId: String? = null,
    val currentEpisodeId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val error: String? = null
)
