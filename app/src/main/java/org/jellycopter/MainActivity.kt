package org.jellycopter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.jellycopter.core.database.ChannelEntity
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.model.AuthState
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaDiscoverItem
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.model.ShowConfig
import org.jellycopter.core.repository.AchievementRepository
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.repository.ChannelRepository
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.core.settings.SettingsRepository
import org.jellycopter.discovery.JellyseerrClient
import org.jellycopter.playback.CoordinatorSource
import org.jellycopter.playback.PlaybackActivity
import org.jellycopter.shuffle.SmartShuffleEngine
import org.jellycopter.ui.navigation.NavScreen
import org.jellycopter.ui.navigation.ResponsiveScaffold
import org.jellycopter.ui.navigation.rememberDeviceType
import org.jellycopter.ui.screens.ChannelEditorScreen
import org.jellycopter.ui.screens.DiscoverScreen
import org.jellycopter.ui.screens.HomeScreen
import org.jellycopter.ui.screens.LibraryScreen
import org.jellycopter.ui.screens.LoginScreen
import org.jellycopter.ui.screens.PostPlaybackScreen
import org.jellycopter.ui.screens.SeriesDetailScreen
import org.jellycopter.ui.screens.SettingsScreen
import org.jellycopter.ui.screens.ShuffleChannelsScreen
import org.jellycopter.ui.screens.TrophyRoomScreen
import org.jellycopter.ui.theme.BaseThemeMode
import org.jellycopter.ui.theme.JellycopterTheme
import org.jellycopter.ui.viewmodel.MainViewModel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val authRepository: AuthRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val mediaRepository: MediaRepository by inject()
    private val channelRepository: ChannelRepository by inject()
    private val deckRepository: DeckRepository by inject()
    private val smartShuffleEngine: SmartShuffleEngine by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JellycopterAppContent(
                authRepository = authRepository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepository,
                channelRepository = channelRepository,
                deckRepository = deckRepository,
                smartShuffleEngine = smartShuffleEngine
            )
        }
    }
}

/** Sealed state for the channel editor overlay */
sealed interface ChannelEditorState {
    object Hidden : ChannelEditorState
    data class CreatingNew(
        val preSelectedSeriesIds: List<String> = emptyList(),
        val preSelectedChannelNumber: Int? = null
    ) : ChannelEditorState
    data class Editing(val channel: ChannelEntity) : ChannelEditorState
}

@Composable
fun JellycopterAppContent(
    authRepository: AuthRepository? = null,
    settingsRepository: SettingsRepository? = null,
    mediaRepository: MediaRepository? = null,
    channelRepository: ChannelRepository? = null,
    deckRepository: DeckRepository? = null,
    smartShuffleEngine: SmartShuffleEngine? = null,
    baseTheme: BaseThemeMode? = null
) {
    val resolvedAuthRepo = authRepository ?: remember {
        try { org.koin.java.KoinJavaComponent.get(AuthRepository::class.java) } catch (_: Exception) { null }
    }
    val resolvedSettingsRepo = settingsRepository ?: remember {
        try { org.koin.java.KoinJavaComponent.get(SettingsRepository::class.java) } catch (_: Exception) { null }
    }
    val resolvedMediaRepo = mediaRepository ?: remember {
        try { org.koin.java.KoinJavaComponent.get(MediaRepository::class.java) } catch (_: Exception) { null }
    }
    val resolvedChannelRepo = channelRepository ?: remember {
        try { org.koin.java.KoinJavaComponent.get(ChannelRepository::class.java) } catch (_: Exception) { null }
    }
    val resolvedDeckRepo: DeckRepository? = deckRepository ?: remember {
        try { org.koin.java.KoinJavaComponent.get(DeckRepository::class.java) } catch (_: Exception) { null }
    }
    val resolvedShuffleEngine: SmartShuffleEngine? = smartShuffleEngine ?: remember {
        try { org.koin.java.KoinJavaComponent.get(SmartShuffleEngine::class.java) } catch (_: Exception) { null }
    }
    val resolvedAchievementRepo: AchievementRepository? = remember {
        try { org.koin.java.KoinJavaComponent.get(AchievementRepository::class.java) } catch (_: Exception) { null }
    }
    val resolvedJellyseerrClient: JellyseerrClient? = remember {
        try { org.koin.java.KoinJavaComponent.get(JellyseerrClient::class.java) } catch (_: Exception) { null }
    }
    val json: kotlinx.serialization.json.Json = remember {
        try { org.koin.java.KoinJavaComponent.get(kotlinx.serialization.json.Json::class.java) }
        catch (_: Exception) { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    }

    val authState = resolvedAuthRepo?.authState?.collectAsState()?.value ?: AuthState.Unauthenticated

    val themeMode = baseTheme
        ?: resolvedSettingsRepo?.themeModeFlow?.collectAsState()?.value
        ?: resolvedSettingsRepo?.getThemeMode()
        ?: BaseThemeMode.WARM_DARK

    val jellyseerrUrl = resolvedSettingsRepo?.jellyseerrUrlFlow?.collectAsState()?.value
        ?: resolvedSettingsRepo?.getJellyseerrUrl()
    val isDiscoverEnabled = !jellyseerrUrl.isNullOrBlank()

    // Live channels from Room DB
    val liveChannels by (resolvedChannelRepo?.getAllChannels()
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    // All available series for the channel editor picker (loads all TV series via getAllSeries)
    val availableSeries by produceState<List<SeriesDetail>>(initialValue = emptyList(), resolvedMediaRepo) {
        if (resolvedMediaRepo != null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { resolvedMediaRepo.getAllSeries() }.getOrDefault(emptyList())
            }
        }
    }

    // Live achievements from Room DB
    val achievements by (resolvedAchievementRepo?.getAllAchievements()
        ?: kotlinx.coroutines.flow.flowOf(emptyList<Achievement>())).collectAsState(initial = emptyList())

    // Live Jellyseerr discover feeds
    val trendingItems by produceState<List<MediaDiscoverItem>>(initialValue = emptyList(), resolvedJellyseerrClient) {
        if (resolvedJellyseerrClient != null) {
            value = runCatching { resolvedJellyseerrClient.getTrending() }.getOrDefault(emptyList())
        }
    }
    val popularItems by produceState<List<MediaDiscoverItem>>(initialValue = emptyList(), resolvedJellyseerrClient) {
        if (resolvedJellyseerrClient != null) {
            value = runCatching { resolvedJellyseerrClient.getPopular() }.getOrDefault(emptyList())
        }
    }
    val upcomingItems by produceState<List<MediaDiscoverItem>>(initialValue = emptyList(), resolvedJellyseerrClient) {
        if (resolvedJellyseerrClient != null) {
            value = runCatching { resolvedJellyseerrClient.getUpcoming(mediaType = "tv") }.getOrDefault(emptyList())
        }
    }

    var currentScreen by remember { mutableStateOf(NavScreen.HOME) }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var channelEditorState by remember { mutableStateOf<ChannelEditorState>(ChannelEditorState.Hidden) }

    val deviceType = rememberDeviceType()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainViewModel = remember(resolvedChannelRepo, resolvedDeckRepo, resolvedMediaRepo, resolvedJellyseerrClient) {
        MainViewModel(resolvedChannelRepo, resolvedDeckRepo, resolvedMediaRepo, resolvedJellyseerrClient)
    }

    val launchChannelPlayback: (ChannelEntity) -> Unit = { channel ->
        scope.launch {
            val deckId = "channel:${channel.channelId}"
            var deck = resolvedDeckRepo?.getDeck(deckId)
            if (deck == null || deck.cards.isEmpty()) {
                val showIds = runCatching {
                    json.decodeFromString<List<String>>(channel.showsJson)
                }.getOrDefault(emptyList())
                if (showIds.isNotEmpty()) {
                    val showConfigs = showIds.map { ShowConfig(seriesId = it, seriesName = it) }
                    val showEpisodesMap = showIds.associateWith { seriesId ->
                        resolvedMediaRepo?.getAllEpisodesForSeries(seriesId) ?: emptyList()
                    }
                    deck = resolvedShuffleEngine?.generateMixChannelDeck(
                        channelId = channel.channelId,
                        shows = showConfigs,
                        episodeProvider = { seriesId -> showEpisodesMap[seriesId] ?: emptyList() }
                    )
                    if (deck != null) {
                        resolvedDeckRepo?.saveDeck(deck)
                    }
                }
            }

            val card = deck?.currentCard ?: deck?.cards?.firstOrNull()
            val ep = card?.items?.firstOrNull()
            if (ep != null) {
                val resumePos = when (channel.resumeBehavior) {
                    "BEGINNING" -> 0L
                    "EXACT_POSITION" -> deck?.midwayResumeMs ?: 0L
                    else -> 0L
                }
                context.startActivity(
                    PlaybackActivity.buildIntent(
                        context = context,
                        episode = ep,
                        source = CoordinatorSource.CHANNEL,
                        channelId = channel.channelId,
                        deckId = deckId,
                        startPositionMs = resumePos
                    )
                )
            }
        }
    }

    // Redirect to HOME if Discover disabled while on that tab
    if (!isDiscoverEnabled && currentScreen == NavScreen.DISCOVER) {
        currentScreen = NavScreen.HOME
    }

    JellycopterTheme(baseTheme = themeMode) {
        when (authState) {
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(JellycopterTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = JellycopterTheme.colors.primaryAccent
                    )
                }
            }

            is AuthState.Unauthenticated, is AuthState.Error -> {
                LoginScreen(
                    authRepository = resolvedAuthRepo,
                    onLoginSuccess = { currentScreen = NavScreen.HOME }
                )
            }

            is AuthState.Authenticated -> {
                // Channel editor overlay (create or edit)
                when (val editorState = channelEditorState) {
                    is ChannelEditorState.Hidden -> { /* fall through to main content */ }
                    is ChannelEditorState.CreatingNew -> {
                        ChannelEditorScreen(
                            existingChannel = null,
                            availableSeries = availableSeries,
                            mediaRepository = resolvedMediaRepo,
                            initialSeriesIds = editorState.preSelectedSeriesIds,
                            initialChannelNumber = editorState.preSelectedChannelNumber,
                            existingChannels = liveChannels,
                            onSave = { entity ->
                                mainViewModel.executeMutation {
                                    resolvedChannelRepo?.saveChannel(entity)
                                    val showIds = runCatching {
                                        json.decodeFromString<List<String>>(entity.showsJson)
                                    }.getOrDefault(emptyList())
                                    if (showIds.isNotEmpty()) {
                                        val showConfigs = showIds.map { ShowConfig(seriesId = it, seriesName = it) }
                                        val showEpisodesMap = showIds.associateWith { seriesId ->
                                            resolvedMediaRepo?.getAllEpisodesForSeries(seriesId) ?: emptyList()
                                        }
                                        val deck = resolvedShuffleEngine?.generateMixChannelDeck(
                                            channelId = entity.channelId,
                                            shows = showConfigs,
                                            episodeProvider = { seriesId -> showEpisodesMap[seriesId] ?: emptyList() }
                                        )
                                        if (deck != null) {
                                            resolvedDeckRepo?.saveDeck(deck)
                                        }
                                    }
                                }
                                channelEditorState = ChannelEditorState.Hidden
                            },
                            onCancel = { channelEditorState = ChannelEditorState.Hidden }
                        )
                        return@JellycopterTheme
                    }
                    is ChannelEditorState.Editing -> {
                        ChannelEditorScreen(
                            existingChannel = editorState.channel,
                            availableSeries = availableSeries,
                            mediaRepository = resolvedMediaRepo,
                            existingChannels = liveChannels,
                            onSave = { entity ->
                                mainViewModel.executeMutation {
                                    resolvedChannelRepo?.saveChannel(entity)
                                    val showIds = runCatching {
                                        json.decodeFromString<List<String>>(entity.showsJson)
                                    }.getOrDefault(emptyList())
                                    if (showIds.isNotEmpty()) {
                                        val showConfigs = showIds.map { ShowConfig(seriesId = it, seriesName = it) }
                                        val showEpisodesMap = showIds.associateWith { seriesId ->
                                            resolvedMediaRepo?.getAllEpisodesForSeries(seriesId) ?: emptyList()
                                        }
                                        val deck = resolvedShuffleEngine?.generateMixChannelDeck(
                                            channelId = entity.channelId,
                                            shows = showConfigs,
                                            episodeProvider = { seriesId -> showEpisodesMap[seriesId] ?: emptyList() }
                                        )
                                        if (deck != null) {
                                            resolvedDeckRepo?.saveDeck(deck)
                                        }
                                    }
                                }
                                channelEditorState = ChannelEditorState.Hidden
                            },
                            onDelete = { channelId ->
                                mainViewModel.deleteChannel(channelId)
                                channelEditorState = ChannelEditorState.Hidden
                            },
                            onCancel = { channelEditorState = ChannelEditorState.Hidden }
                        )
                        return@JellycopterTheme
                    }
                }

                // Series Detail overlay
                if (selectedSeriesId != null) {
                    SeriesDetailScreen(
                        seriesId = selectedSeriesId,
                        mediaRepository = resolvedMediaRepo,
                        channels = liveChannels,
                        onResumeNextUp = { series, episode ->
                            episode?.let {
                                context.startActivity(
                                    PlaybackActivity.buildIntent(
                                        context = context,
                                        episode = it,
                                        source = CoordinatorSource.NORMAL
                                    )
                                )
                            }
                        },
                        onRestartShow = { series, s1e1 ->
                            s1e1?.let {
                                context.startActivity(
                                    PlaybackActivity.buildIntent(
                                        context = context,
                                        episode = it,
                                        source = CoordinatorSource.NORMAL,
                                        startPositionMs = 0L
                                    )
                                )
                            }
                        },
                        onSmartShuffle = { series ->
                            scope.launch {
                                val deckId = "series:${series.id}"
                                var deck = resolvedDeckRepo?.getDeck(deckId)
                                if (deck == null || deck.cards.isEmpty()) {
                                    val episodes = resolvedMediaRepo?.getAllEpisodesForSeries(series.id)
                                        ?: emptyList()
                                    if (episodes.isNotEmpty()) {
                                        deck = resolvedShuffleEngine?.generateDeck(series.id, episodes)
                                            ?: org.jellycopter.core.model.ShuffledDeck(
                                                deckId = deckId,
                                                seriesId = series.id,
                                                name = series.name
                                            )
                                        resolvedDeckRepo?.saveDeck(deck)
                                    }
                                }
                                val card = deck?.currentCard ?: deck?.cards?.firstOrNull()
                                val episodeToPlay = card?.items?.firstOrNull()
                                if (episodeToPlay != null) {
                                    context.startActivity(
                                        PlaybackActivity.buildIntent(
                                            context = context,
                                            episode = episodeToPlay,
                                            source = CoordinatorSource.SHUFFLE,
                                            deckId = deck?.deckId ?: deckId,
                                            startPositionMs = deck?.midwayResumeMs ?: 0L
                                        )
                                    )
                                }
                            }
                        },
                        onAddToChannel = { series ->
                            // Navigate to Channels screen with the editor pre-seeded
                            currentScreen = NavScreen.CHANNELS
                            selectedSeriesId = null
                            channelEditorState = ChannelEditorState.CreatingNew(listOf(series.id))
                        },
                        onAddToExistingChannel = { series, channel ->
                            scope.launch {
                                val existingIds = runCatching {
                                    json.decodeFromString<List<String>>(channel.showsJson)
                                }.getOrDefault(emptyList()).toMutableList()
                                if (!existingIds.contains(series.id)) {
                                    existingIds.add(series.id)
                                    val updated = channel.copy(
                                        showsJson = json.encodeToString<List<String>>(existingIds),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    resolvedChannelRepo?.saveChannel(updated)
                                }
                            }
                        },
                        onPlayEpisode = { series, episode ->
                            context.startActivity(
                                PlaybackActivity.buildIntent(
                                    context = context,
                                    episode = episode,
                                    source = CoordinatorSource.NORMAL
                                )
                            )
                        },
                        onBack = { selectedSeriesId = null }
                    )
                } else {
                    ResponsiveScaffold(
                        deviceType = deviceType,
                        currentScreen = currentScreen,
                        onScreenSelected = { currentScreen = it },
                        isDiscoverEnabled = isDiscoverEnabled,
                        liveChannels = liveChannels,
                        onTuneSmartChannel = launchChannelPlayback,
                        onCreateSmartChannelAt = { chNum ->
                            channelEditorState = ChannelEditorState.CreatingNew(preSelectedChannelNumber = chNum)
                        }
                    ) {
                        when (currentScreen) {
                            NavScreen.HOME -> HomeScreen(
                                mediaRepository = resolvedMediaRepo,
                                deckRepository = resolvedDeckRepo,
                                onPlayContinueWatching = { item ->
                                    scope.launch {
                                        val ep = resolvedMediaRepo?.getNextUpForSeries(item.id)
                                            ?: resolvedMediaRepo?.getFirstEpisodeForSeries(item.id)
                                            ?: EpisodeItem(
                                                id = item.id,
                                                name = item.episodeTitle,
                                                seriesId = item.id,
                                                seriesName = item.seriesTitle,
                                                seasonId = "",
                                                seasonNumber = item.seasonNumber,
                                                indexNumber = item.episodeNumber
                                            )
                                        context.startActivity(
                                            PlaybackActivity.buildIntent(
                                                context = context,
                                                episode = ep,
                                                source = CoordinatorSource.NORMAL,
                                                startPositionMs = item.resumePositionMs
                                            )
                                        )
                                    }
                                },
                                onResumeDeck = { deckItem ->
                                    scope.launch {
                                        val deck = resolvedDeckRepo?.getDeck(deckItem.deckId)
                                        val card = deck?.currentCard ?: deck?.cards?.firstOrNull()
                                        val ep = card?.items?.firstOrNull()
                                            ?: resolvedMediaRepo?.getNextUpForSeries(deckItem.deckId.removePrefix("series:"))
                                            ?: resolvedMediaRepo?.getFirstEpisodeForSeries(deckItem.deckId.removePrefix("series:"))
                                            ?: EpisodeItem(
                                                id = deckItem.deckId,
                                                name = deckItem.title,
                                                seriesId = deckItem.deckId,
                                                seriesName = deckItem.title,
                                                seasonId = "",
                                                seasonNumber = 1,
                                                indexNumber = 1
                                            )
                                        context.startActivity(
                                            PlaybackActivity.buildIntent(
                                                context = context,
                                                episode = ep,
                                                source = if (deckItem.isChannelMix) CoordinatorSource.CHANNEL else CoordinatorSource.SHUFFLE,
                                                deckId = deckItem.deckId,
                                                channelId = if (deckItem.isChannelMix) deckItem.deckId.removePrefix("channel:") else null,
                                                startPositionMs = deck?.midwayResumeMs ?: deckItem.lastResumeMs
                                            )
                                        )
                                    }
                                },
                                onSmartShuffleHero = { seriesId ->
                                    scope.launch {
                                        val deckId = "series:$seriesId"
                                        var deck = resolvedDeckRepo?.getDeck(deckId)
                                        if (deck == null || deck.cards.isEmpty()) {
                                            val episodes: List<EpisodeItem> = resolvedMediaRepo?.getAllEpisodesForSeries(seriesId) ?: emptyList()
                                            if (episodes.isNotEmpty()) {
                                                deck = resolvedShuffleEngine?.generateDeck(seriesId, episodes)
                                                    ?: org.jellycopter.core.model.ShuffledDeck(
                                                        deckId = deckId,
                                                        seriesId = seriesId,
                                                        name = episodes.firstOrNull()?.seriesName ?: "Shuffled"
                                                    )
                                                resolvedDeckRepo?.saveDeck(deck)
                                            }
                                        }
                                        val card = deck?.currentCard ?: deck?.cards?.firstOrNull()
                                        val episodeToPlay = card?.items?.firstOrNull()
                                            ?: resolvedMediaRepo?.getNextUpForSeries(seriesId)
                                            ?: resolvedMediaRepo?.getFirstEpisodeForSeries(seriesId)
                                        if (episodeToPlay != null) {
                                            context.startActivity(
                                                PlaybackActivity.buildIntent(
                                                    context = context,
                                                    episode = episodeToPlay,
                                                    source = CoordinatorSource.SHUFFLE,
                                                    deckId = deck?.deckId ?: deckId,
                                                    startPositionMs = deck?.midwayResumeMs ?: 0L
                                                )
                                            )
                                        }
                                    }
                                },
                                onOpenSeriesDetails = { seriesId ->
                                    selectedSeriesId = seriesId
                                },
                                onNavigateToChannels = {
                                    currentScreen = NavScreen.CHANNELS
                                },
                                onNavigateToLibrary = {
                                    currentScreen = NavScreen.LIBRARY
                                }
                            )
                            NavScreen.CHANNELS -> ShuffleChannelsScreen(
                                liveChannels = liveChannels,
                                mediaRepository = resolvedMediaRepo,
                                availableSeries = availableSeries,
                                onLaunchChannel = launchChannelPlayback,
                                onCreateChannel = { channelEditorState = ChannelEditorState.CreatingNew() },
                                onEditChannel = { channel -> channelEditorState = ChannelEditorState.Editing(channel) }
                            )
                            NavScreen.DISCOVER -> DiscoverScreen(
                                trending = trendingItems,
                                popular = popularItems,
                                upcoming = upcomingItems,
                                onRequestMedia = { item ->
                                    mainViewModel.requestMedia(item)
                                }
                            )
                            NavScreen.LIBRARY -> LibraryScreen(
                                mediaRepository = resolvedMediaRepo,
                                onOpenSeriesId = { selectedSeriesId = it },
                                onRequestMissingSeason = { item, seasonNum ->
                                    mainViewModel.requestMissingSeason(item.tmdbId, seasonNum)
                                }
                            )
                            NavScreen.TROPHY_ROOM -> TrophyRoomScreen(
                                achievements = achievements.ifEmpty {
                                    // Fall back to default preview data until DB is seeded
                                    org.jellycopter.telemetry.MilestoneDefinitions.ALL_MILESTONES
                                }
                            )
                            NavScreen.POST_PLAYBACK -> PostPlaybackScreen(
                                session = org.jellycopter.telemetry.PlaybackSessionStats(
                                    mediaId = "default"
                                ),
                                onPlayNext = { currentScreen = NavScreen.HOME },
                                onReplay = { currentScreen = NavScreen.HOME },
                                onBackToGuide = { currentScreen = NavScreen.HOME },
                                onViewTrophies = { currentScreen = NavScreen.TROPHY_ROOM }
                            )
                            NavScreen.SETTINGS -> SettingsScreen(
                                settingsRepository = resolvedSettingsRepo,
                                authRepository = resolvedAuthRepo,
                                onSignOut = { currentScreen = NavScreen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }
}
