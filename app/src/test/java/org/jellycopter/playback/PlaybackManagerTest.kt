package org.jellycopter.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellycopter.core.model.DrcConfig
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.playback.dsp.PassthroughHandoffManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class PlaybackManagerTest {

    private fun createSampleCard(cardId: String = "card_1", episodeId: String = "ep_1"): MediaCard {
        val episode = EpisodeItem(
            id = episodeId,
            name = "Pilot Episode",
            seriesId = "series_1",
            seriesName = "Test Series",
            seasonId = "season_1",
            seasonNumber = 1,
            indexNumber = 1,
            runTimeTicks = 36000000000L
        )
        return MediaCard(
            id = cardId,
            seriesId = "series_1",
            seasonId = "season_1",
            categoryKey = "comedy",
            items = listOf(episode)
        )
    }

    @Test
    fun testNormalPlaybackFlow() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)

        val card = createSampleCard()
        manager.play(card, startPositionMs = 12000L)

        val state = manager.playerState.value
        assertEquals(card.id, state.deckId)
        assertEquals("ep_1", state.currentEpisodeId)
        assertEquals(12000L, state.positionMs)
        assertTrue(state.isPlaying)
        assertEquals(PlaybackManager.ActiveEngine.MEDIA3_EXOPLAYER, manager.activeEngine.value)

        // Pause
        manager.pause()
        assertTrue(!manager.playerState.value.isPlaying)

        // Resume
        manager.resume()
        assertTrue(manager.playerState.value.isPlaying)

        // Seek
        manager.seekTo(25000L)
        assertEquals(25000L, manager.playerState.value.positionMs)
    }

    @Test
    fun testPlaybackEndedState() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)

        val card = createSampleCard()
        manager.play(card, startPositionMs = 0L)
        assertFalse(manager.playerState.value.isEnded)
        assertTrue(manager.playerState.value.isPlaying)

        primaryAdapter.notifyPlaybackEnded()
        assertTrue(manager.playerState.value.isEnded)
        assertFalse(manager.playerState.value.isPlaying)

        // Starting a new play session resets isEnded
        manager.play(card, startPositionMs = 0L)
        assertFalse(manager.playerState.value.isEnded)
        assertTrue(manager.playerState.value.isPlaying)
    }

    @Test
    fun testDecoderInitializationFailureTriggersFallbackToLibmpv() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)

        val card = createSampleCard()
        manager.play(card, startPositionMs = 0L)
        primaryAdapter.notifyPositionDiscontinuity(42000L)

        assertEquals(PlaybackManager.ActiveEngine.MEDIA3_EXOPLAYER, manager.activeEngine.value)

        // Mock SystemClock.elapsedRealtime to allow real PlaybackException instantiation on JVM
        io.mockk.mockkStatic(android.os.SystemClock::class)
        io.mockk.every { android.os.SystemClock.elapsedRealtime() } returns 1000L

        // Primary throws DecoderInitializationException error code
        val decoderError = PlaybackException(
            "Decoder init failed",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        primaryAdapter.notifyError(decoderError)

        // Must seamlessly hand off to libmpv
        assertEquals(PlaybackManager.ActiveEngine.LIBMPV, manager.activeEngine.value)
        assertEquals(42000L, fallbackAdapter.currentPositionMs)
        assertEquals(42000L, manager.playerState.value.positionMs)
        assertTrue("Playback must continue playing after failover", manager.playerState.value.isPlaying)
        assertNotNull(manager.getLastFallbackReason())
        assertTrue(manager.getLastFallbackReason()!!.contains("DecoderInitializationException"))
    }

    @Test
    fun testSustainedDroppedFramesTriggersFallbackToLibmpv() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)

        val card = createSampleCard()
        manager.play(card, startPositionMs = 10000L)
        primaryAdapter.notifyPositionDiscontinuity(15000L)

        val baseTime = 100000L
        // 1. Report 15 dropped frames
        manager.handlePrimaryDroppedFrames(15, nowMs = baseTime)
        assertEquals(PlaybackManager.ActiveEngine.MEDIA3_EXOPLAYER, manager.activeEngine.value)

        // 2. Report 20 more dropped frames at baseTime + 2000 ms (total = 35 > 30 within 5s)
        manager.handlePrimaryDroppedFrames(20, nowMs = baseTime + 2000L)

        // Threshold of >30 dropped frames in 5s exceeded -> must trigger fallback!
        assertEquals(PlaybackManager.ActiveEngine.LIBMPV, manager.activeEngine.value)
        assertEquals(15000L, fallbackAdapter.currentPositionMs)
        assertTrue(manager.getLastFallbackReason()!!.contains("Sustained frame drop threshold exceeded"))
    }

    @Test
    fun testIsolatedDroppedFramesDoNotTriggerFallback() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)

        val card = createSampleCard()
        manager.play(card, startPositionMs = 0L)

        val baseTime = 100000L
        // 10 dropped frames
        manager.handlePrimaryDroppedFrames(10, nowMs = baseTime)
        assertEquals(PlaybackManager.ActiveEngine.MEDIA3_EXOPLAYER, manager.activeEngine.value)

        // 10 more dropped frames 7 seconds later (outside sliding 5000ms window)
        manager.handlePrimaryDroppedFrames(10, nowMs = baseTime + 7000L)

        // Total in sliding window is only 10 -> no failover
        assertEquals(PlaybackManager.ActiveEngine.MEDIA3_EXOPLAYER, manager.activeEngine.value)
    }

    @Test
    fun testAudioDspControlPropagation() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val passthrough = PassthroughHandoffManager(initialPassthroughSupported = true)
        val manager = PlaybackManager(
            primaryPlayer = primaryAdapter,
            fallbackPlayer = fallbackAdapter,
            passthroughManager = passthrough
        )

        passthrough.setCurrentStream("audio/true-hd")
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, passthrough.currentMode.value)

        // Enabling Night Mode switches passthrough to PCM_DSP
        manager.setAudioNightMode(true, DrcConfig(dialogueFloorBoostDb = 6.0f))
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, passthrough.currentMode.value)

        // Disabling Night Mode returns to PASSTHROUGH_BITSTREAM
        manager.setAudioNightMode(false)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PASSTHROUGH_BITSTREAM, passthrough.currentMode.value)

        // Enabling Force Stereo switches passthrough to PCM_DSP
        manager.setForceStereo(true, centerBoostDb = 4.5f)
        assertEquals(PassthroughHandoffManager.AudioRoutingMode.PCM_DSP, passthrough.currentMode.value)
    }

    @Test
    fun testScrobbleSuppressionInterceptorDropsProgressAndStoppedOnShuffle() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)
        manager.isShufflePlayback = true

        val interceptor = manager.scrobbleSuppressionInterceptor

        for (endpoint in listOf("/Sessions/Playing/Progress", "/Sessions/Playing/Stopped")) {
            val request = Request.Builder()
                .url("http://localhost:8096$endpoint")
                .build()

            val chain = mockk<Interceptor.Chain>()
            every { chain.request() } returns request

            val response = interceptor.intercept(chain)

            // Dropped -> returns synthetic 204 No Content
            assertEquals(204, response.code)
            assertEquals("Scrobble suppressed for shuffle playback", response.message)
            // Network chain.proceed was NEVER called
            verify(exactly = 0) { chain.proceed(any()) }
        }
    }

    @Test
    fun testScrobbleSuppressionInterceptorAllowsRequestsWhenShuffleDisabled() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)
        manager.isShufflePlayback = false

        val interceptor = manager.scrobbleSuppressionInterceptor

        val request = Request.Builder()
            .url("http://localhost:8096/Sessions/Playing/Stopped")
            .build()

        val dummyResponse = Response.Builder()
            .code(204)
            .message("Real server response")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body("".toResponseBody(null))
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns dummyResponse

        val response = interceptor.intercept(chain)

        assertEquals("Real server response", response.message)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun testScrobbleSuppressionInterceptorAllowsNonScrobbleRequestsEvenOnShuffle() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)
        manager.isShufflePlayback = true

        val interceptor = manager.scrobbleSuppressionInterceptor

        val request = Request.Builder()
            .url("http://localhost:8096/Items/12345/PlaybackInfo")
            .build()

        val dummyResponse = Response.Builder()
            .code(200)
            .message("OK")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body("{}".toResponseBody(null))
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns dummyResponse

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun testPlayOverloadSetsIsShufflePlayback() {
        val primaryAdapter = ExoPlayerAdapter()
        val fallbackAdapter = MpvPlayerAdapter()
        val manager = PlaybackManager(primaryAdapter, fallbackAdapter)

        assertFalse(manager.isShufflePlayback)

        val card = createSampleCard()
        manager.play(card, startPositionMs = 5000L, isShuffle = true)

        assertTrue(manager.isShufflePlayback)
        assertEquals(5000L, manager.playerState.value.positionMs)
    }
}
