package org.jellycopter.playback

import android.content.Context
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.jellycopter.core.model.DrcConfig
import org.jellycopter.playback.dsp.PlaybackAudioProcessorChain
import org.jellycopter.playback.dv.DolbyVisionTrackSelector

/**
 * Media3 ExoPlayer implementation of [PlayerAdapter].
 */
@UnstableApi
class ExoPlayerAdapter(
    private val context: Context? = null,
    injectedPlayer: ExoPlayer? = null,
    val audioProcessorChain: PlaybackAudioProcessorChain = PlaybackAudioProcessorChain(),
    val dvTrackSelector: DolbyVisionTrackSelector = DolbyVisionTrackSelector()
) : PlayerAdapter {

    override val name: String = "Media3 ExoPlayer"
    override val isAvailable: Boolean = true

    private val player: ExoPlayer? = injectedPlayer ?: run {
        if (context != null) {
            val audioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(audioProcessorChain.getAudioProcessors())
                .build()

            val trackSelector = DefaultTrackSelector(context)

            ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build()
        } else {
            null
        }
    }

    val exoPlayer: ExoPlayer?
        get() = player

    private val listeners = mutableListOf<PlayerAdapter.Listener>()
    private var simulatedPositionMs: Long = 0L
    private var simulatedDurationMs: Long = 0L
    private var simulatedIsPlaying: Boolean = false
    private var simulatedIsBuffering: Boolean = false

    override val currentPositionMs: Long
        get() = player?.currentPosition ?: simulatedPositionMs

    override val durationMs: Long
        get() = player?.duration?.takeIf { it > 0 } ?: simulatedDurationMs

    override val isPlaying: Boolean
        get() = player?.isPlaying ?: simulatedIsPlaying

    override val isBuffering: Boolean
        get() = player?.playbackState == Player.STATE_BUFFERING || simulatedIsBuffering

    init {
        setupPlayerListeners()
    }

    private fun setupPlayerListeners() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                notifyStateChanged(isPlaying, isBuffering)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                notifyStateChanged(isPlaying, buffering)
                if (playbackState == Player.STATE_ENDED) {
                    notifyPlaybackEnded()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                notifyPositionDiscontinuity(newPosition.positionMs)
            }

            override fun onPlayerError(error: PlaybackException) {
                notifyError(error)
            }
        })

        player?.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                notifyDroppedFrames(droppedFrames, elapsedMs)
            }
        })
    }

    override fun prepare(uri: String, startPositionMs: Long) {
        simulatedPositionMs = startPositionMs
        if (player != null) {
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
            }
            player.prepare()
        }
    }

    override fun play() {
        simulatedIsPlaying = true
        player?.play()
        notifyStateChanged(isPlaying = true, isBuffering = false)
    }

    override fun pause() {
        simulatedIsPlaying = false
        player?.pause()
        notifyStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun seekTo(positionMs: Long) {
        simulatedPositionMs = positionMs
        player?.seekTo(positionMs)
        notifyPositionDiscontinuity(positionMs)
    }

    override fun stop() {
        simulatedIsPlaying = false
        player?.stop()
        notifyStateChanged(isPlaying = false, isBuffering = false)
    }

    override fun release() {
        player?.release()
    }

    override fun setSurface(surface: Any?) {
        if (surface is Surface) {
            player?.setVideoSurface(surface)
        } else if (surface == null) {
            player?.clearVideoSurface()
        }
    }

    override fun setAudioNightMode(enabled: Boolean, config: DrcConfig) {
        audioProcessorChain.setAudioNightMode(enabled, config)
    }

    override fun setForceStereo(enabled: Boolean, centerBoostDb: Float) {
        audioProcessorChain.setForceStereo(enabled, centerBoostDb)
    }

    override fun setSubtitleDimmer(enabled: Boolean, dimRatio: Float) {
        // Handled via SubtitleView / CaptionStyleCompat in UI layer
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

    fun notifyError(error: PlaybackException) {
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onError(error)
        }
    }

    fun notifyDroppedFrames(droppedCount: Int, elapsedMs: Long) {
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onDroppedFrames(droppedCount, elapsedMs)
        }
    }

    fun notifyStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        simulatedIsPlaying = isPlaying
        simulatedIsBuffering = isBuffering
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onPlaybackStateChanged(isPlaying, isBuffering)
        }
    }

    fun notifyPositionDiscontinuity(positionMs: Long) {
        simulatedPositionMs = positionMs
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onPositionDiscontinuity(positionMs)
        }
    }

    fun notifyPlaybackEnded() {
        simulatedIsPlaying = false
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            l.onPlaybackEnded()
        }
    }
}
