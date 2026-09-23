package org.jellycopter.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.jellycopter.core.model.DrcConfig

/**
 * Unified abstraction interface for player engines (Media3 ExoPlayer and libmpv).
 */
@UnstableApi
interface PlayerAdapter {
    val name: String
    val isAvailable: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    val isBuffering: Boolean

    fun prepare(uri: String, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
    fun release()
    fun setSurface(surface: Any?)

    fun setAudioNightMode(enabled: Boolean, config: DrcConfig = DrcConfig())
    fun setForceStereo(enabled: Boolean, centerBoostDb: Float = 3.0f)
    fun setSubtitleDimmer(enabled: Boolean, dimRatio: Float = 0.5f)

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    interface Listener {
        fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {}
        fun onPositionDiscontinuity(positionMs: Long) {}
        fun onError(error: PlaybackException) {}
        fun onDroppedFrames(count: Int, elapsedMs: Long) {}
        fun onPlaybackEnded() {}
    }
}
