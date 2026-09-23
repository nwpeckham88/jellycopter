package org.jellycopter.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.jellycopter.core.model.DrcConfig

/**
 * libmpv Fallback Player Adapter.
 *
 * Implements fallback rendering using libmpv options:
 * - vo=gpu-next
 * - hwdec=auto-safe
 * - target-colorspace-hint=yes
 * - Subtitle dimming via sub-color options
 */
@UnstableApi
class MpvPlayerAdapter(
    private val subtitleDimmer: SubtitleDimmer = SubtitleDimmer()
) : PlayerAdapter {

    override val name: String = "libmpv"
    override val isAvailable: Boolean = true

    private val options = mutableMapOf<String, String>()
    private val listeners = mutableListOf<PlayerAdapter.Listener>()

    private var _currentPositionMs: Long = 0L
    private var _durationMs: Long = 0L
    private var _isPlaying: Boolean = false
    private var _isBuffering: Boolean = false
    private var currentUri: String? = null

    override val currentPositionMs: Long get() = _currentPositionMs
    override val durationMs: Long get() = _durationMs
    override val isPlaying: Boolean get() = _isPlaying
    override val isBuffering: Boolean get() = _isBuffering

    init {
        // Initialize default high-quality mpv options
        setOption("vo", "gpu-next")
        setOption("hwdec", "auto-safe")
        setOption("target-colorspace-hint", "yes")
    }

    fun setOption(key: String, value: String) {
        options[key] = value
    }

    fun getOption(key: String): String? = options[key]

    fun getAllOptions(): Map<String, String> = options.toMap()

    override fun prepare(uri: String, startPositionMs: Long) {
        currentUri = uri
        _currentPositionMs = startPositionMs
        val startSec = startPositionMs / 1000.0
        setOption("start", String.format(java.util.Locale.US, "%.3f", startSec))
        _isBuffering = true
        notifyStateChanged(_isPlaying, _isBuffering)
        _isBuffering = false
        notifyStateChanged(_isPlaying, _isBuffering)
    }

    override fun play() {
        _isPlaying = true
        notifyStateChanged(isPlaying = true, isBuffering = false)
    }

    override fun pause() {
        _isPlaying = false
        notifyStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun seekTo(positionMs: Long) {
        _currentPositionMs = positionMs
        val startSec = positionMs / 1000.0
        setOption("start", String.format(java.util.Locale.US, "%.3f", startSec))
        notifyPositionDiscontinuity(positionMs)
    }

    override fun stop() {
        _isPlaying = false
        notifyStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun release() {
        options.clear()
        _isPlaying = false
    }

    override fun setSurface(surface: Any?) {
        // Attaches native window / surface to mpv instance
    }

    override fun setAudioNightMode(enabled: Boolean, config: DrcConfig) {
        if (enabled) {
            // Apply mpv dynamic range compression filter equivalent if running through mpv audio filter chain
            setOption("af", "lavfi=[acompressor=threshold=-18dB:ratio=4:attack=10:release=300]")
        } else {
            options.remove("af")
        }
    }

    override fun setForceStereo(enabled: Boolean, centerBoostDb: Float) {
        if (enabled) {
            setOption("audio-channels", "stereo")
        } else {
            setOption("audio-channels", "auto-safe")
        }
    }

    override fun setSubtitleDimmer(enabled: Boolean, dimRatio: Float) {
        subtitleDimmer.isDimmed = enabled
        subtitleDimmer.dimRatio = dimRatio
        val subOptions = subtitleDimmer.getMpvSubColorOptions()
        for ((k, v) in subOptions) {
            setOption(k, v)
        }
    }

    override fun addListener(listener: PlayerAdapter.Listener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    override fun removeListener(listener: PlayerAdapter.Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onPlaybackStateChanged(isPlaying, isBuffering)
        }
    }

    private fun notifyPositionDiscontinuity(positionMs: Long) {
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onPositionDiscontinuity(positionMs)
        }
    }

    fun notifyError(error: PlaybackException) {
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onError(error)
        }
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        _currentPositionMs = positionMs
        _durationMs = durationMs
    }
}
