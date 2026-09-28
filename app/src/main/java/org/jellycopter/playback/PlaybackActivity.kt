package org.jellycopter.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import org.jellycopter.ui.components.FocusCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.core.settings.SettingsRepository
import org.jellycopter.ui.theme.JellycopterTheme

/**
 * Fullscreen playback activity. Receives a [PlaybackContext] via intent extras.
 *
 * Hosts the Media3 PlayerView (via ExoPlayerAdapter) behind a Compose controls overlay.
 * Controls auto-hide after 5 seconds of inactivity when playing, and stay visible when paused.
 *
 * Remote D-pad Navigation:
 *  - When controls are visible: Left/Right navigates between controls, OK activates the focused control.
 *  - Back button: dismisses controls if visible, exits playback if already hidden.
 *  - When controls are hidden: D-pad center toggles play/pause; Left/Right seeks; Up/Down reveals controls.
 *  - Hardware media keys: handle play/pause, rewind, fast-forward, and skip next directly.
 */
class PlaybackActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_EPISODE_TITLE = "episode_title"
        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SEASON_ID = "season_id"
        const val EXTRA_SEASON_NUMBER = "season_number"
        const val EXTRA_EPISODE_NUMBER = "episode_number"
        const val EXTRA_RUNTIME_TICKS = "runtime_ticks"
        const val EXTRA_START_POSITION_MS = "start_position_ms"
        const val EXTRA_SOURCE = "playback_source"       // "NORMAL" | "SHUFFLE" | "CHANNEL"
        const val EXTRA_DECK_ID = "deck_id"
        const val EXTRA_CHANNEL_ID = "channel_id"

        fun buildIntent(context: Context, episode: EpisodeItem, source: CoordinatorSource = CoordinatorSource.NORMAL, deckId: String? = null, channelId: String? = null, startPositionMs: Long = 0L): Intent {
            return Intent(context, PlaybackActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, episode.id)
                putExtra(EXTRA_EPISODE_TITLE, episode.name)
                putExtra(EXTRA_SERIES_TITLE, episode.seriesName)
                putExtra(EXTRA_SERIES_ID, episode.seriesId)
                putExtra(EXTRA_SEASON_ID, episode.seasonId)
                putExtra(EXTRA_SEASON_NUMBER, episode.seasonNumber)
                putExtra(EXTRA_EPISODE_NUMBER, episode.indexNumber)
                putExtra(EXTRA_RUNTIME_TICKS, episode.runTimeTicks)
                putExtra(EXTRA_START_POSITION_MS, startPositionMs)
                putExtra(EXTRA_SOURCE, source.name)
                deckId?.let { putExtra(EXTRA_DECK_ID, it) }
                channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
            }
        }
    }

    private val playbackEngine: PlaybackEngine? by lazy {
        try { org.koin.java.KoinJavaComponent.get(PlaybackEngine::class.java) } catch (_: Exception) { null }
    }
    private val playbackCoordinator: PlaybackCoordinator? by lazy {
        try { org.koin.java.KoinJavaComponent.get(PlaybackCoordinator::class.java) } catch (_: Exception) { null }
    }
    private val mediaRepository: MediaRepository? by lazy {
        try { org.koin.java.KoinJavaComponent.get(MediaRepository::class.java) } catch (_: Exception) { null }
    }
    private val settingsRepository: SettingsRepository? by lazy {
        try { org.koin.java.KoinJavaComponent.get(SettingsRepository::class.java) } catch (_: Exception) { null }
    }
    private val channelRepository: org.jellycopter.core.repository.ChannelRepository? by lazy {
        try { org.koin.java.KoinJavaComponent.get(org.jellycopter.core.repository.ChannelRepository::class.java) } catch (_: Exception) { null }
    }

    private var currentEpisodeItem by mutableStateOf<EpisodeItem?>(null)
    private var currentSource by mutableStateOf(CoordinatorSource.NORMAL)
    private var currentDeckId: String? = null
    private var currentChannelId: String? = null

    private var currentEpisodeTitle = mutableStateOf("Episode")
    private var currentSeriesTitle = mutableStateOf("")
    private var currentSeasonNumber = mutableIntStateOf(1)
    private var currentEpisodeNumber = mutableIntStateOf(1)

    private var showControls = mutableStateOf(true)
    private var isPlaying = mutableStateOf(false)
    private var progress = mutableFloatStateOf(0f)
    private var positionMs = mutableLongStateOf(0L)
    private var durationMs = mutableLongStateOf(0L)
    private var nightModeEnabled = mutableStateOf(false)
    private var userInteractionToken = mutableLongStateOf(0L)

    private var isAdvancing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during playback; go fullscreen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
        val episodeTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE) ?: "Episode"
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: ""
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID)?.takeIf { it.isNotBlank() } ?: seriesTitle
        val seasonId = intent.getStringExtra(EXTRA_SEASON_ID) ?: ""
        val seasonNum = intent.getIntExtra(EXTRA_SEASON_NUMBER, 1)
        val episodeNum = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)
        val runTimeTicks = intent.getLongExtra(EXTRA_RUNTIME_TICKS, 0L)
        val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        currentDeckId = intent.getStringExtra(EXTRA_DECK_ID)
        currentChannelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        currentSource = intent.getStringExtra(EXTRA_SOURCE)?.let {
            runCatching { CoordinatorSource.valueOf(it) }.getOrDefault(CoordinatorSource.NORMAL)
        } ?: CoordinatorSource.NORMAL

        val initialEpisode = EpisodeItem(
            id = itemId,
            name = episodeTitle,
            seriesId = seriesId,
            seriesName = seriesTitle,
            seasonId = seasonId,
            seasonNumber = seasonNum,
            indexNumber = episodeNum,
            runTimeTicks = runTimeTicks
        )

        currentEpisodeItem = initialEpisode
        currentEpisodeTitle.value = episodeTitle
        currentSeriesTitle.value = seriesTitle
        currentSeasonNumber.intValue = seasonNum
        currentEpisodeNumber.intValue = episodeNum
        durationMs.longValue = if (runTimeTicks > 0L) runTimeTicks / 10_000L else 0L

        val mediaCard = MediaCard(
            id = itemId,
            seriesId = seriesId,
            seasonId = seasonId,
            categoryKey = "s$seasonNum",
            items = listOf(initialEpisode)
        )

        val pbContext = PlaybackContext(
            itemId = itemId,
            episode = initialEpisode,
            source = currentSource,
            deckId = currentDeckId,
            channelId = currentChannelId,
            startPositionMs = startPositionMs
        )
        playbackCoordinator?.onPlaybackStarted(pbContext)
        playbackEngine?.play(mediaCard, startPositionMs)

        // Apply configured defaults from SettingsRepository (DRC Night Mode, Auto-Night quiet hours, Force Stereo, Subtitle Dimmer)
        val dsp = settingsRepository?.getAudioDspSettings()
        val subSettings = settingsRepository?.getSubtitleSettings()
        val autoNight = settingsRepository?.getAutoNightSettings()

        var initialNightMode = dsp?.nightModeDrcEnabled ?: false
        if (autoNight?.autoNightModeEnabled == true) {
            val now = java.time.LocalTime.now()
            val start = runCatching { java.time.LocalTime.parse(autoNight.quietHoursStart) }.getOrDefault(java.time.LocalTime.of(22, 30))
            val end = runCatching { java.time.LocalTime.parse(autoNight.quietHoursEnd) }.getOrDefault(java.time.LocalTime.of(7, 0))
            if (AutoNightScheduler.isNightTime(now, start, end)) {
                initialNightMode = true
            }
            val scheduler = (playbackEngine as? PlaybackManager)?.autoNightScheduler
            scheduler?.autoEnabled = true
            runCatching { scheduler?.setQuietHoursFromString(autoNight.quietHoursStart, autoNight.quietHoursEnd) }
        }
        // Channel audio night mode override
        val chanId = currentChannelId
        if (chanId != null) {
            val channel = runCatching {
                kotlinx.coroutines.runBlocking { channelRepository?.getChannel(chanId) }
            }.getOrNull()
            if (channel?.audioNightMode == "ON") {
                initialNightMode = true
            } else if (channel?.audioNightMode == "OFF") {
                initialNightMode = false
            }
        }
        nightModeEnabled.value = initialNightMode
        playbackEngine?.setAudioNightMode(initialNightMode)
        if (dsp?.forceStereoEnabled == true) {
            playbackEngine?.setForceStereo(true, dsp.centerBoostDb)
        }
        if (subSettings?.subtitleDimmerEnabled == true) {
            playbackEngine?.setSubtitleDimmer(true, subSettings.subtitleDimRatio)
        }

        setContent {
            // Ticker to poll ExoPlayer position & duration during steady playback
            LaunchedEffect(isPlaying.value) {
                while (isPlaying.value) {
                    val cur = playbackEngine?.exoPlayer?.currentPosition ?: positionMs.longValue
                    val dur = playbackEngine?.exoPlayer?.duration?.takeIf { it > 0 } ?: durationMs.longValue
                    if (dur > 0L) {
                        positionMs.longValue = cur
                        durationMs.longValue = dur
                        progress.floatValue = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                    delay(500L)
                }
            }

            LaunchedEffect(playbackEngine) {
                playbackEngine?.playerState?.collect { state ->
                    isPlaying.value = state.isPlaying
                    positionMs.longValue = state.positionMs
                    durationMs.longValue = state.durationMs
                    if (state.durationMs > 0L) {
                        progress.floatValue = (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                    }
                    if (state.isEnded) {
                        advanceToNextEpisode(isManualSkip = false)
                    }
                }
            }

            JellycopterTheme {
                PlaybackScreen(
                    exoPlayer = playbackEngine?.exoPlayer,
                    episodeTitle = currentEpisodeTitle.value,
                    seriesTitle = currentSeriesTitle.value,
                    seasonNumber = currentSeasonNumber.intValue,
                    episodeNumber = currentEpisodeNumber.intValue,
                    source = currentSource,
                    showControls = showControls.value,
                    onDismissControls = { showControls.value = false },
                    isPlaying = isPlaying.value,
                    progress = progress.floatValue,
                    positionMs = positionMs.longValue,
                    durationMs = durationMs.longValue,
                    nightModeEnabled = nightModeEnabled.value,
                    userInteractionToken = userInteractionToken.longValue,
                    onUserInteraction = { userInteractionToken.longValue++ },
                    onPlayPause = { togglePlayPause() },
                    onRewind = { seekRelative(-10_000L) },
                    onFastForward = { seekRelative(10_000L) },
                    onSkipNext = { advanceToNextEpisode(isManualSkip = true) },
                    onSeek = { targetMs -> playbackEngine?.seekTo(targetMs) },
                    onToggleNightMode = {
                        val nextState = !nightModeEnabled.value
                        nightModeEnabled.value = nextState
                        playbackEngine?.setAudioNightMode(nextState)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun playEpisode(episode: EpisodeItem, startPositionMs: Long = 0L) {
        currentEpisodeItem = episode
        currentEpisodeTitle.value = episode.name
        if (episode.seriesName.isNotBlank()) {
            currentSeriesTitle.value = episode.seriesName
        }
        currentSeasonNumber.intValue = episode.seasonNumber
        currentEpisodeNumber.intValue = episode.indexNumber
        progress.floatValue = 0f
        positionMs.longValue = startPositionMs
        durationMs.longValue = if (episode.runTimeTicks > 0) episode.runTimeTicks / 10_000L else 0L

        val mediaCard = MediaCard(
            id = episode.id,
            seriesId = episode.seriesId.ifBlank { currentSeriesTitle.value },
            seasonId = episode.seasonId,
            categoryKey = "s${episode.seasonNumber}",
            items = listOf(episode)
        )

        val newContext = PlaybackContext(
            itemId = episode.id,
            episode = episode,
            source = currentSource,
            deckId = currentDeckId,
            channelId = currentChannelId,
            startPositionMs = startPositionMs
        )
        playbackCoordinator?.onPlaybackStarted(newContext)
        playbackEngine?.play(mediaCard, startPositionMs)
    }

    private fun advanceToNextEpisode(isManualSkip: Boolean) {
        if (isAdvancing) return
        isAdvancing = true
        lifecycleScope.launch {
            try {
                val action = if (isManualSkip) {
                    playbackCoordinator?.onEpisodeSkipped()
                } else {
                    val state = playbackEngine?.playerState?.value
                    val cur = playbackEngine?.exoPlayer?.currentPosition ?: positionMs.longValue
                    val dur = playbackEngine?.exoPlayer?.duration?.takeIf { it > 0 }
                        ?: state?.durationMs?.takeIf { it > 0 }
                        ?: durationMs.longValue
                    val effectivePos = if (dur > 0L) maxOf(cur, state?.positionMs ?: 0L, dur) else maxOf(cur, state?.positionMs ?: 0L)
                    playbackCoordinator?.onEpisodeCompleted(effectivePos, dur)
                }

                when (action) {
                    is NextAction.PlayNext -> {
                        playEpisode(action.episode, action.startPositionMs)
                    }
                    is NextAction.JellyfinAutoPlay -> {
                        val currentEp = currentEpisodeItem
                        val nextUp = currentEp?.seriesId?.takeIf { it.isNotBlank() }?.let {
                            mediaRepository?.getNextUpForSeries(it)
                        }
                        if (nextUp != null && nextUp.id != currentEp?.id) {
                            playEpisode(nextUp, 0L)
                        } else {
                            finish()
                        }
                    }
                    is NextAction.Stop, null -> {
                        finish()
                    }
                }
            } finally {
                isAdvancing = false
            }
        }
    }

    private fun savePlaybackProgress() {
        val cur = playbackEngine?.exoPlayer?.currentPosition ?: positionMs.longValue
        val dur = playbackEngine?.exoPlayer?.duration?.takeIf { it > 0 } ?: durationMs.longValue
        if (cur > 0L) {
            positionMs.longValue = cur
            durationMs.longValue = dur
            lifecycleScope.launch {
                playbackCoordinator?.saveCurrentProgress(cur, dur)
            }
        }
    }

    private fun togglePlayPause() {
        if (isPlaying.value) {
            playbackEngine?.pause()
            isPlaying.value = false
            savePlaybackProgress()
        } else {
            playbackEngine?.resume()
            isPlaying.value = true
        }
    }

    private fun seekRelative(offsetMs: Long) {
        val currentMs = playbackEngine?.playerState?.value?.positionMs ?: positionMs.longValue
        val targetMs = maxOf(0L, currentMs + offsetMs)
        playbackEngine?.seekTo(targetMs)
    }

    override fun onPause() {
        super.onPause()
        playbackEngine?.pause()
        savePlaybackProgress()
    }

    override fun onStop() {
        super.onStop()
        playbackEngine?.pause()
        savePlaybackProgress()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        userInteractionToken.longValue++

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (showControls.value) {
                showControls.value = false
                return true
            } else {
                finish()
                return true
            }
        }

        // Dedicated remote media keys
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                advanceToNextEpisode(isManualSkip = true)
                showControls.value = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                showControls.value = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playbackEngine?.resume()
                isPlaying.value = true
                showControls.value = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playbackEngine?.pause()
                isPlaying.value = false
                showControls.value = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekRelative(-10_000L)
                showControls.value = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekRelative(10_000L)
                showControls.value = true
                return true
            }
        }

        // When controls are hidden, D-pad actions show controls and/or perform action
        if (!showControls.value) {
            showControls.value = true
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    seekRelative(-10_000L)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    seekRelative(10_000L)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                    return true
                }
                else -> return super.onKeyDown(keyCode, event)
            }
        }

        // When controls ARE visible:
        // Allow DPAD_LEFT, DPAD_RIGHT, DPAD_UP, DPAD_DOWN, DPAD_CENTER, ENTER to reach Compose
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        savePlaybackProgress()
        super.onDestroy()
        playbackEngine?.stop()
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMinutes = minutes % 60
        "%d:%02d:%02d".format(hours, remMinutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlaybackScreen(
    exoPlayer: ExoPlayer?,
    episodeTitle: String,
    seriesTitle: String,
    seasonNumber: Int,
    episodeNumber: Int,
    source: CoordinatorSource,
    showControls: Boolean,
    onDismissControls: () -> Unit = {},
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    nightModeEnabled: Boolean,
    userInteractionToken: Long,
    onUserInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleNightMode: () -> Unit,
    onBack: () -> Unit
) {
    val playPauseFocusRequester = remember { FocusRequester() }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var hudMessage by remember { mutableStateOf<String?>(null) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }

    // Request focus on Play/Pause when controls appear
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(50)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide controls after 5 seconds of inactivity (only when playing and not in picker dialogs)
    LaunchedEffect(showControls, isPlaying, userInteractionToken, showAudioPicker, showSubtitlePicker) {
        if (showControls && isPlaying && !showAudioPicker && !showSubtitlePicker) {
            delay(5_000)
            onDismissControls()
        }
    }

    // Auto-dismiss HUD after 2 seconds
    val currentHud = hudMessage
    if (currentHud != null) {
        LaunchedEffect(currentHud) {
            delay(2000)
            hudMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player surface: renders live ExoPlayer video frames
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls overlay (fades in/out)
        if (showControls) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: series/episode info + source badge (using vector icons, zero raw emojis)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = seriesTitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp
                        )
                        if (source != CoordinatorSource.NORMAL) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (source == CoordinatorSource.SHUFFLE) Icons.Rounded.Shuffle else Icons.Rounded.Tv,
                                    contentDescription = if (source == CoordinatorSource.SHUFFLE) "Smart Shuffle" else "Channel",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (source == CoordinatorSource.SHUFFLE) "Smart Shuffle" else "Channel",
                                    color = Color(0xFF818CF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "S${seasonNumber}E${episodeNumber} · $episodeTitle",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bottom: D-pad scrubber + time labels + controls
                Column {
                    TvScrubber(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = onSeek,
                        onUserInteraction = onUserInteraction
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Time labels row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (durationMs > 0) formatTime(durationMs) else "--:--",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Control buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Night mode toggle
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                val willEnable = !nightModeEnabled
                                onToggleNightMode()
                                hudMessage = if (willEnable) "Night Mode DRC: ON" else "Night Mode DRC: OFF"
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.NightsStay,
                                contentDescription = "Night Mode",
                                modifier = Modifier.size(26.dp),
                                tint = if (nightModeEnabled) Color(0xFF818CF8) else if (isFocused) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 2. Audio track picker
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                showAudioPicker = true
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.Audiotrack,
                                contentDescription = "Audio Track",
                                modifier = Modifier.size(26.dp),
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 3. Rewind 10s
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                onRewind()
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.FastRewind,
                                contentDescription = "Rewind 10s",
                                modifier = Modifier.size(32.dp),
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 4. Play/Pause (large center button)
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                onPlayPause()
                            },
                            onFocused = onUserInteraction,
                            focusRequester = playPauseFocusRequester,
                            modifier = Modifier.size(64.dp)
                        ) { isFocused ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(44.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 5. Forward 10s
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                onFastForward()
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Forward 10s",
                                modifier = Modifier.size(32.dp),
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 6. Skip to next episode
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                onSkipNext()
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Episode",
                                modifier = Modifier.size(30.dp),
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 7. Subtitles picker
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                showSubtitlePicker = true
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = "Subtitles",
                                modifier = Modifier.size(26.dp),
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // 8. Aspect Ratio toggle
                        TvIconButton(
                            onClick = {
                                onUserInteraction()
                                val nextMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                resizeMode = nextMode
                                val label = when (nextMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit (16:9)"
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill (Stretch)"
                                    else -> "Fit"
                                }
                                hudMessage = "Aspect Ratio: $label"
                            },
                            onFocused = onUserInteraction
                        ) { isFocused ->
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                modifier = Modifier.size(26.dp),
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // HUD notification overlay pill (centered near top)
        if (currentHud != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = currentHud,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Audio Track Picker Dialog
        val audioTracks = remember(exoPlayer?.currentTracks, showAudioPicker) {
            val list = mutableListOf<AudioTrackInfo>()
            val currentTracks = exoPlayer?.currentTracks ?: return@remember emptyList()
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language?.uppercase() ?: "Track ${i + 1}"
                        val label = format.label?.takeIf { it.isNotBlank() } ?: lang
                        val channels = when (format.channelCount) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            6 -> "5.1"
                            8 -> "7.1"
                            else -> if (format.channelCount > 0) "${format.channelCount}ch" else null
                        }
                        val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase()
                        val details = listOfNotNull(channels, codec).joinToString(" · ")
                        val displayName = if (details.isNotBlank()) "$label ($details)" else label
                        list.add(
                            AudioTrackInfo(
                                group = group,
                                trackIndex = i,
                                name = displayName,
                                isSelected = group.isTrackSelected(i)
                            )
                        )
                    }
                }
            }
            list
        }

        if (showAudioPicker) {
            TrackPickerDialog(
                title = "Audio Track",
                tracks = audioTracks.map { it.name to it.isSelected },
                onSelectTrack = { index ->
                    val track = audioTracks.getOrNull(index)
                    if (track != null && exoPlayer != null) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
                            )
                            .build()
                        hudMessage = "Audio: ${track.name}"
                    }
                    showAudioPicker = false
                },
                onDismiss = { showAudioPicker = false }
            )
        }

        // Subtitle Track Picker Dialog
        val subtitleTracks = remember(exoPlayer?.currentTracks, showSubtitlePicker) {
            val list = mutableListOf<SubtitleTrackInfo>()
            val currentTracks = exoPlayer?.currentTracks ?: return@remember emptyList()
            var anySelected = false
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val isSelected = group.isTrackSelected(i)
                        if (isSelected) anySelected = true
                        val format = group.getTrackFormat(i)
                        val lang = format.language?.uppercase() ?: "Subtitle ${i + 1}"
                        val label = format.label?.takeIf { it.isNotBlank() } ?: lang
                        list.add(
                            SubtitleTrackInfo(
                                group = group,
                                trackIndex = i,
                                name = label,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
            val result = mutableListOf<SubtitleTrackInfo>()
            result.add(
                SubtitleTrackInfo(
                    group = null,
                    trackIndex = -1,
                    name = "Off",
                    isSelected = !anySelected
                )
            )
            result.addAll(list)
            result
        }

        if (showSubtitlePicker) {
            TrackPickerDialog(
                title = "Subtitle Track",
                tracks = subtitleTracks.map { it.name to it.isSelected },
                onSelectTrack = { index ->
                    val track = subtitleTracks.getOrNull(index)
                    if (track != null && exoPlayer != null) {
                        if (track.group == null) {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .build()
                            hudMessage = "Subtitles: Off"
                        } else {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
                                )
                                .build()
                            hudMessage = "Subtitles: ${track.name}"
                        }
                    }
                    showSubtitlePicker = false
                },
                onDismiss = { showSubtitlePicker = false }
            )
        }
    }
}

/**
 * Interactive, focusable TV scrubber for D-pad navigation.
 */
@Composable
private fun TvScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var localScrubMs by remember { mutableLongStateOf(positionMs) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(positionMs, isScrubbing) {
        if (!isScrubbing) {
            localScrubMs = positionMs
        }
    }

    val displayProgress = if (durationMs > 0L) {
        val ms = if (isScrubbing) localScrubMs else positionMs
        (ms.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                val was = isFocused
                isFocused = it.isFocused
                if (it.isFocused && !was) {
                    onUserInteraction()
                }
                if (!it.isFocused && isScrubbing) {
                    onSeek(localScrubMs)
                    isScrubbing = false
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onUserInteraction()
                            isScrubbing = true
                            localScrubMs = maxOf(0L, localScrubMs - 10_000L)
                            true
                        }
                        Key.DirectionRight -> {
                            onUserInteraction()
                            isScrubbing = true
                            localScrubMs = minOf(if (durationMs > 0) durationMs else Long.MAX_VALUE, localScrubMs + 10_000L)
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onUserInteraction()
                            if (isScrubbing) {
                                onSeek(localScrubMs)
                                isScrubbing = false
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(vertical = 4.dp)
    ) {
        if (isScrubbing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF818CF8))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Seek: ${formatTime(localScrubMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 10.dp else 5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.25f))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(5.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayProgress)
                    .height(if (isFocused) 10.dp else 5.dp)
                    .background(Color(0xFF818CF8))
            )
        }
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TvIconButtonScale"
    )

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(animatedScale)
            .onFocusChanged {
                val was = isFocused
                isFocused = it.isFocused
                if (it.isFocused && !was) {
                    onFocused()
                }
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(
                if (isFocused) Color(0xFF818CF8).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color(0xFF818CF8) else Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        content(isFocused)
    }
}

@Composable
private fun TrackPickerDialog(
    title: String,
    tracks: List<Pair<String, Boolean>>,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2235),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            text = "No tracks available",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(tracks.size) { index ->
                        val (label, isSelected) = tracks[index]
                        FocusCard(
                            onClick = { onSelectTrack(index) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            isFocused -> Color(0xFF818CF8).copy(alpha = 0.35f)
                                            isSelected -> Color.White.copy(alpha = 0.1f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF818CF8) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF818CF8))
            }
        }
    )
}

@UnstableApi
private data class AudioTrackInfo(
    val group: androidx.media3.common.Tracks.Group,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean
)

@UnstableApi
private data class SubtitleTrackInfo(
    val group: androidx.media3.common.Tracks.Group?,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean
)

