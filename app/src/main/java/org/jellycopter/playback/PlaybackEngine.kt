package org.jellycopter.playback

import kotlinx.coroutines.flow.StateFlow
import org.jellycopter.core.model.DrcConfig
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.PlaybackState

typealias PlayerState = PlaybackState

/**
 * Authoritative interface contract for the Jellycopter Playback Subsystem.
 */
interface PlaybackEngine {
    val playerState: StateFlow<PlaybackState>
    val exoPlayer: androidx.media3.exoplayer.ExoPlayer? get() = null

    fun play(item: MediaCard, startPositionMs: Long = 0L)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)

    fun setAudioNightMode(enabled: Boolean, config: DrcConfig = DrcConfig())
    fun setForceStereo(enabled: Boolean, centerBoostDb: Float = 3.0f)
    fun setSubtitleDimmer(enabled: Boolean, dimRatio: Float = 0.5f)
}
