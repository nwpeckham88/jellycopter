package org.jellycopter.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jellycopter.core.model.DrcConfig
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.model.PlaybackState
import org.jellycopter.playback.dsp.PassthroughHandoffManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * PlaybackManager coordinates primary (Media3 ExoPlayer) and fallback (libmpv) playback engines.
 *
 * Implements automated seamless fallback to libmpv when:
 * 1. A DecoderInitializationException or decoder initialization/query failure occurs.
 * 2. >30 dropped frames occur within a sliding 5000 ms window during playback.
 *
 * Preserves playback position and play/pause state during handoff.
 */
@UnstableApi
class PlaybackManager(
    val primaryPlayer: PlayerAdapter,
    val fallbackPlayer: PlayerAdapter = MpvPlayerAdapter(),
    val passthroughManager: PassthroughHandoffManager = PassthroughHandoffManager(),
    val autoNightScheduler: AutoNightScheduler = AutoNightScheduler(),
    val sessionStorage: org.jellycopter.core.session.EncryptedSessionStorage? = null
) : PlaybackEngine {

    override val exoPlayer: androidx.media3.exoplayer.ExoPlayer?
        get() = (primaryPlayer as? ExoPlayerAdapter)?.exoPlayer

    enum class ActiveEngine {
        MEDIA3_EXOPLAYER,
        LIBMPV
    }

    data class DroppedFrameRecord(
        val timestampMs: Long,
        val count: Int
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _playerState = MutableStateFlow(PlaybackState())
    override val playerState: StateFlow<PlaybackState> = _playerState.asStateFlow()

    private val _activeEngine = MutableStateFlow(ActiveEngine.MEDIA3_EXOPLAYER)
    val activeEngine: StateFlow<ActiveEngine> = _activeEngine.asStateFlow()

    val currentAdapter: PlayerAdapter
        get() = if (_activeEngine.value == ActiveEngine.MEDIA3_EXOPLAYER) primaryPlayer else fallbackPlayer

    private var currentCard: MediaCard? = null
    private var currentStreamUri: String? = null
    private var isNightModeActive: Boolean = false
    private var isForceStereoActive: Boolean = false
    private var currentCenterBoostDb: Float = 3.0f
    private var currentDrcConfig: DrcConfig = DrcConfig()

    var isShufflePlayback: Boolean = false
    val scrobbleSuppressionInterceptor: ScrobbleSuppressionInterceptor = ScrobbleSuppressionInterceptor { isShufflePlayback }

    private val droppedFrameRecords = ConcurrentLinkedQueue<DroppedFrameRecord>()
    private var lastFallbackReason: String? = null

    init {
        setupPrimaryListeners()
        setupFallbackListeners()
    }

    private fun setupPrimaryListeners() {
        primaryPlayer.addListener(object : PlayerAdapter.Listener {
            override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
                if (_activeEngine.value == ActiveEngine.MEDIA3_EXOPLAYER) {
                    _playerState.update {
                        it.copy(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionMs = primaryPlayer.currentPositionMs,
                            durationMs = primaryPlayer.durationMs
                        )
                    }
                }
            }

            override fun onPositionDiscontinuity(positionMs: Long) {
                if (_activeEngine.value == ActiveEngine.MEDIA3_EXOPLAYER) {
                    _playerState.update { it.copy(positionMs = positionMs) }
                }
            }

            override fun onError(error: PlaybackException) {
                if (_activeEngine.value == ActiveEngine.MEDIA3_EXOPLAYER) {
                    handlePrimaryError(error)
                }
            }

            override fun onDroppedFrames(count: Int, elapsedMs: Long) {
                if (_activeEngine.value == ActiveEngine.MEDIA3_EXOPLAYER) {
                    handlePrimaryDroppedFrames(count)
                }
            }

            override fun onPlaybackEnded() {
                if (_activeEngine.value == ActiveEngine.MEDIA3_EXOPLAYER) {
                    _playerState.update { it.copy(isPlaying = false, isEnded = true) }
                }
            }
        })
    }

    private fun setupFallbackListeners() {
        fallbackPlayer.addListener(object : PlayerAdapter.Listener {
            override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
                if (_activeEngine.value == ActiveEngine.LIBMPV) {
                    _playerState.update {
                        it.copy(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionMs = fallbackPlayer.currentPositionMs,
                            durationMs = fallbackPlayer.durationMs
                        )
                    }
                }
            }

            override fun onPositionDiscontinuity(positionMs: Long) {
                if (_activeEngine.value == ActiveEngine.LIBMPV) {
                    _playerState.update { it.copy(positionMs = positionMs) }
                }
            }

            override fun onError(error: PlaybackException) {
                if (_activeEngine.value == ActiveEngine.LIBMPV) {
                    _playerState.update { it.copy(error = error.message) }
                }
            }

            override fun onPlaybackEnded() {
                if (_activeEngine.value == ActiveEngine.LIBMPV) {
                    _playerState.update { it.copy(isPlaying = false, isEnded = true) }
                }
            }
        })
    }

    override fun play(item: MediaCard, startPositionMs: Long) {
        currentCard = item
        val episode = item.items.firstOrNull()
        val targetItemId = episode?.id ?: item.id
        val isJellyfinId = targetItemId.length in 32..36 && targetItemId.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '-' }
        val session = sessionStorage?.getSession()
        val uri = if (isJellyfinId && session != null && session.serverUrl.isNotBlank()) {
            val base = session.serverUrl.trim().removeSuffix("/")
            val tokenParam = if (!session.accessToken.isNullOrBlank()) "&api_key=${session.accessToken}" else ""
            "$base/Videos/$targetItemId/stream?static=true$tokenParam"
        } else {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        }
        currentStreamUri = uri

        _playerState.update {
            it.copy(
                deckId = item.id,
                currentCardId = item.id,
                currentEpisodeId = episode?.id,
                positionMs = startPositionMs,
                isPlaying = true,
                isBuffering = true,
                isEnded = false,
                error = null
            )
        }

        // Apply audio settings to current adapter
        currentAdapter.setAudioNightMode(isNightModeActive, currentDrcConfig)
        currentAdapter.setForceStereo(isForceStereoActive, currentCenterBoostDb)

        currentAdapter.prepare(uri, startPositionMs)
        currentAdapter.play()
    }

    override fun pause() {
        currentAdapter.pause()
        _playerState.update { it.copy(isPlaying = false) }
    }

    override fun resume() {
        currentAdapter.play()
        _playerState.update { it.copy(isPlaying = true) }
    }

    override fun stop() {
        currentAdapter.stop()
        _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    override fun seekTo(positionMs: Long) {
        currentAdapter.seekTo(positionMs)
        _playerState.update { it.copy(positionMs = positionMs) }
    }

    override fun setAudioNightMode(enabled: Boolean, config: DrcConfig) {
        isNightModeActive = enabled
        currentDrcConfig = config
        passthroughManager.setNightMode(enabled)
        currentAdapter.setAudioNightMode(enabled, config)
    }

    override fun setForceStereo(enabled: Boolean, centerBoostDb: Float) {
        isForceStereoActive = enabled
        currentCenterBoostDb = centerBoostDb
        passthroughManager.setForceStereo(enabled)
        currentAdapter.setForceStereo(enabled, centerBoostDb)
    }

    override fun setSubtitleDimmer(enabled: Boolean, dimRatio: Float) {
        currentAdapter.setSubtitleDimmer(enabled, dimRatio)
    }

    /**
     * Checks if a [PlaybackException] represents a decoder initialization failure,
     * triggering immediate fallback to libmpv.
     */
    fun isDecoderInitializationFailure(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED) {
            return true
        }

        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is MediaCodecRenderer.DecoderInitializationException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun handlePrimaryError(error: PlaybackException) {
        if (isDecoderInitializationFailure(error)) {
            triggerFallback("DecoderInitializationException encountered: ${error.message}")
        } else {
            _playerState.update { it.copy(error = error.message) }
        }
    }

    /**
     * Records dropped frames and triggers fallback if >30 dropped frames occur in a sliding 5000 ms window.
     */
    fun handlePrimaryDroppedFrames(count: Int, nowMs: Long = System.currentTimeMillis()) {
        droppedFrameRecords.add(DroppedFrameRecord(nowMs, count))

        // Purge records older than 5000 ms
        val cutoff = nowMs - 5000L
        droppedFrameRecords.removeIf { it.timestampMs < cutoff }

        val totalDropped = droppedFrameRecords.sumOf { it.count }
        if (totalDropped > 30) {
            triggerFallback("Sustained frame drop threshold exceeded: $totalDropped dropped frames in 5s (>30)")
        }
    }

    /**
     * Seamlessly hands off playback from Media3 to libmpv at the exact resume position.
     */
    fun triggerFallback(reason: String) {
        if (_activeEngine.value == ActiveEngine.LIBMPV) return

        lastFallbackReason = reason
        val resumePosition = primaryPlayer.currentPositionMs
        val wasPlaying = _playerState.value.isPlaying
        val uri = currentStreamUri

        // Stop primary engine
        primaryPlayer.stop()

        // Switch to fallback engine
        _activeEngine.value = ActiveEngine.LIBMPV

        // Reapply audio / subtitle settings
        fallbackPlayer.setAudioNightMode(isNightModeActive, currentDrcConfig)
        fallbackPlayer.setForceStereo(isForceStereoActive, currentCenterBoostDb)

        if (uri != null) {
            fallbackPlayer.prepare(uri, resumePosition)
            if (wasPlaying) {
                fallbackPlayer.play()
            }
        }

        _playerState.update {
            it.copy(
                positionMs = resumePosition,
                isPlaying = wasPlaying,
                isBuffering = false,
                error = null
            )
        }
    }

    fun getLastFallbackReason(): String? = lastFallbackReason

    fun play(item: MediaCard, startPositionMs: Long = 0L, isShuffle: Boolean) {
        this.isShufflePlayback = isShuffle
        play(item, startPositionMs)
    }

    fun release() {
        primaryPlayer.release()
        fallbackPlayer.release()
        autoNightScheduler.stopMonitoring()
    }

    /**
     * Explicit OkHttp network interceptor that drops outbound scrobble requests to
     * /Sessions/Playing/Progress and /Sessions/Playing/Stopped whenever isShufflePlayback is active.
     * Guarantees zero remote Next Up mutations on the Jellyfin server.
     */
    class ScrobbleSuppressionInterceptor(
        val isShufflePlaybackActive: () -> Boolean
    ) : Interceptor {

        companion object {
            val SUPPRESSED_ENDPOINTS = listOf(
                "/Sessions/Playing/Progress",
                "/Sessions/Playing/Stopped"
            )

            fun isScrobbleEndpoint(path: String): Boolean {
                return SUPPRESSED_ENDPOINTS.any { endpoint ->
                    path.endsWith(endpoint, ignoreCase = true) || path.contains(endpoint, ignoreCase = true)
                }
            }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val path = request.url.encodedPath

            if (isShufflePlaybackActive() && isScrobbleEndpoint(path)) {
                // Drop outbound request to prevent remote Next Up mutations.
                // Return synthetic HTTP 204 No Content response.
                return Response.Builder()
                    .code(204)
                    .message("Scrobble suppressed for shuffle playback")
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }

            return chain.proceed(request)
        }
    }
}
