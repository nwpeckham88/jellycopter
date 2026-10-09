package org.jellycopter.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.flow.flowOf
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.ExhaustionBadge
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.components.MetadataChip
import org.jellycopter.ui.components.MidwayResumeIndicator
import org.jellycopter.ui.theme.JellycopterTheme

data class ContinueWatchingItem(
    val id: String,
    val seriesTitle: String,
    val episodeTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val resumePositionMs: Long,
    val durationMs: Long,
    val overview: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null
)

data class ActiveShuffleDeckItem(
    val deckId: String,
    val title: String,
    val cycleNumber: Int,
    val exhaustedCount: Int,
    val totalCount: Int,
    val lastResumeMs: Long,
    val durationMs: Long,
    val isChannelMix: Boolean = false,
    val seriesId: String? = null,
    val overview: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null
)

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository? = null,
    deckRepository: DeckRepository? = null,
    continueWatching: List<ContinueWatchingItem>? = null,
    activeDecks: List<ActiveShuffleDeckItem>? = null,
    onPlayContinueWatching: (ContinueWatchingItem) -> Unit = {},
    onResumeDeck: (ActiveShuffleDeckItem) -> Unit = {},
    onSmartShuffleHero: (seriesId: String) -> Unit = {},
    onOpenSeriesDetails: (seriesId: String) -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Resolve live continue watching from MediaRepository and DeckRepository if not explicitly provided
    val liveContinueWatching by produceState<List<ContinueWatchingItem>>(
        initialValue = emptyList(),
        mediaRepository,
        deckRepository
    ) {
        if (mediaRepository != null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val folders = mediaRepository.getVirtualFolders()
                    val allSeries = folders.flatMap { folder ->
                        runCatching { mediaRepository.getSeriesInFolder(folder.id) }.getOrDefault(emptyList())
                    }.distinctBy { it.id }

                    if (allSeries.isNotEmpty()) {
                        allSeries.take(10).map { series ->
                            val nextUp = mediaRepository.getNextUpForSeries(series.id)
                                ?: mediaRepository.getFirstEpisodeForSeries(series.id)
                            val deckProgress = deckRepository?.getProgress("series:${series.id}")
                            val resumeMs = deckProgress?.midwayResumeMs ?: 0L
                            val duration = if ((nextUp?.runTimeTicks ?: 0L) > 0L) {
                                nextUp!!.runTimeTicks / 10_000L
                            } else if ((deckProgress?.durationMs ?: 0L) > 0L) {
                                deckProgress!!.durationMs
                            } else {
                                2_700_000L
                            }

                            ContinueWatchingItem(
                                id = series.id,
                                seriesTitle = series.name,
                                episodeTitle = nextUp?.name ?: "Episode 1",
                                seasonNumber = nextUp?.seasonNumber ?: 1,
                                episodeNumber = nextUp?.indexNumber ?: 1,
                                resumePositionMs = resumeMs,
                                durationMs = duration,
                                overview = series.overview ?: nextUp?.overview,
                                backdropUrl = series.backdropUrl ?: mediaRepository.buildBackdropImageUrl(series.id),
                                posterUrl = series.posterUrl ?: mediaRepository.buildPrimaryImageUrl(series.id)
                            )
                        }
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    val displayContinueWatching = continueWatching ?: liveContinueWatching

    // Resolve live active decks from DeckRepository if not explicitly provided
    val rawDecks by (deckRepository?.getAllDecks() ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val displayDecks = remember(rawDecks, activeDecks) {
        if (activeDecks != null) {
            activeDecks
        } else if (rawDecks.isNotEmpty()) {
            rawDecks.map { deck ->
                val isChannel = deck.channelId != null || deck.deckId.startsWith("channel:")
                val currentItem = deck.currentCard?.items?.firstOrNull()
                val firstItem = deck.cards.firstOrNull()?.items?.firstOrNull()
                val rawSeriesId = currentItem?.seriesId ?: firstItem?.seriesId ?: deck.seriesId ?: deck.deckId.removePrefix("series:")
                val effectiveSeriesId = rawSeriesId?.removePrefix("series:")?.removePrefix("channel:")?.trim()
                val deckTitle = if (isChannel && deck.name.isNotBlank()) {
                    deck.name
                } else {
                    currentItem?.seriesName ?: firstItem?.seriesName ?: deck.name.ifBlank { deck.deckId }
                }

                val epTicks = currentItem?.runTimeTicks ?: firstItem?.runTimeTicks ?: 0L
                val durationMs = if (epTicks > 0L) epTicks / 10_000L else 1_800_000L

                ActiveShuffleDeckItem(
                    deckId = deck.deckId,
                    title = deckTitle,
                    cycleNumber = deck.cycleNumber,
                    exhaustedCount = deck.cards.count { it.isExhausted },
                    totalCount = deck.cards.size,
                    lastResumeMs = deck.midwayResumeMs,
                    durationMs = durationMs,
                    isChannelMix = isChannel,
                    seriesId = effectiveSeriesId,
                    overview = currentItem?.overview ?: firstItem?.overview,
                    backdropUrl = effectiveSeriesId?.let { mediaRepository?.buildBackdropImageUrl(it) },
                    posterUrl = effectiveSeriesId?.let { mediaRepository?.buildPrimaryImageUrl(it) }
                )
            }
        } else {
            emptyList()
        }
    }

    // Dynamic Spotlight: track remote focus on continue watching or deck cards
    var spotlightContinueWatching by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    var spotlightDeckItem by remember { mutableStateOf<ActiveShuffleDeckItem?>(null) }

    val activeSpotlight = spotlightContinueWatching
    val activeDeckSpotlight = spotlightDeckItem

    // Primary hero resolution: focused item -> first continue watching -> first active deck -> welcome banner
    val featuredDeck = displayDecks.firstOrNull()
    val firstContinueWatching = displayContinueWatching.firstOrNull()

    val heroTitle: String
    val heroSubtitle: String
    val heroDescription: String
    val heroBackdropUrl: String?
    val heroSeriesId: String?
    val isRealMediaItem: Boolean
    val isDeckItem: Boolean

    if (activeSpotlight != null) {
        heroTitle = activeSpotlight.seriesTitle
        heroSubtitle = "S${activeSpotlight.seasonNumber}:E${activeSpotlight.episodeNumber} • ${activeSpotlight.episodeTitle}"
        heroDescription = activeSpotlight.overview
            ?: "Smart Shuffle continuously balances episode pacing, preventing character repetition while preserving canon arcs."
        heroBackdropUrl = activeSpotlight.backdropUrl ?: activeSpotlight.posterUrl
        heroSeriesId = activeSpotlight.id
        isRealMediaItem = true
        isDeckItem = false
    } else if (activeDeckSpotlight != null) {
        heroTitle = activeDeckSpotlight.title
        heroSubtitle = if (activeDeckSpotlight.isChannelMix) {
            "Virtual Broadcast Channel • ${activeDeckSpotlight.totalCount} Episodes"
        } else {
            "Smart Shuffle • Cycle ${activeDeckSpotlight.cycleNumber} (${activeDeckSpotlight.exhaustedCount}/${activeDeckSpotlight.totalCount} Played)"
        }
        heroDescription = activeDeckSpotlight.overview
            ?: "Intelligent automated shuffle engine with anti-clustering pacing and cross-cycle exhaustion tracking."
        heroBackdropUrl = activeDeckSpotlight.backdropUrl ?: activeDeckSpotlight.posterUrl
        heroSeriesId = activeDeckSpotlight.seriesId
        isRealMediaItem = true
        isDeckItem = true
    } else if (firstContinueWatching != null) {
        heroTitle = firstContinueWatching.seriesTitle
        heroSubtitle = "S${firstContinueWatching.seasonNumber}:E${firstContinueWatching.episodeNumber} • ${firstContinueWatching.episodeTitle}"
        heroDescription = firstContinueWatching.overview
            ?: "Smart Shuffle continuously balances episode pacing, preventing character repetition while preserving canon arcs."
        heroBackdropUrl = firstContinueWatching.backdropUrl ?: firstContinueWatching.posterUrl
        heroSeriesId = firstContinueWatching.id
        isRealMediaItem = true
        isDeckItem = false
    } else if (featuredDeck != null) {
        heroTitle = featuredDeck.title
        heroSubtitle = if (featuredDeck.isChannelMix) {
            "Virtual Broadcast Channel • ${featuredDeck.totalCount} Episodes"
        } else {
            "Smart Shuffle • Cycle ${featuredDeck.cycleNumber} (${featuredDeck.exhaustedCount}/${featuredDeck.totalCount} Played)"
        }
        heroDescription = featuredDeck.overview
            ?: "Intelligent automated shuffle engine with anti-clustering pacing and cross-cycle exhaustion tracking."
        heroBackdropUrl = featuredDeck.backdropUrl ?: featuredDeck.posterUrl
        heroSeriesId = featuredDeck.seriesId
        isRealMediaItem = true
        isDeckItem = true
    } else {
        heroTitle = "Welcome to Jellycopter"
        heroSubtitle = "Smart Television for Jellyfin"
        heroDescription = "Experience intelligent smart shuffle playback and virtual broadcast channels configured directly from your media library."
        heroBackdropUrl = null
        heroSeriesId = null
        isRealMediaItem = false
        isDeckItem = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Hero Spotlight Banner (Apple TV / JellyWatch Cinematic Stage)
        item {
            HeroBanner(
                title = heroTitle,
                subtitle = heroSubtitle,
                description = heroDescription,
                backdropUrl = heroBackdropUrl,
                isRealMedia = isRealMediaItem,
                seriesId = heroSeriesId,
                showResume = isRealMediaItem,
                showShuffle = isRealMediaItem || displayDecks.isNotEmpty(),
                onResumeClick = {
                    when {
                        activeSpotlight != null -> onPlayContinueWatching(activeSpotlight)
                        activeDeckSpotlight != null -> onResumeDeck(activeDeckSpotlight)
                        firstContinueWatching != null -> onPlayContinueWatching(firstContinueWatching)
                        featuredDeck != null -> onResumeDeck(featuredDeck)
                    }
                },
                onShuffleClick = {
                    when {
                        activeSpotlight != null -> onSmartShuffleHero(activeSpotlight.id)
                        activeDeckSpotlight != null -> onResumeDeck(activeDeckSpotlight)
                        firstContinueWatching != null -> onSmartShuffleHero(firstContinueWatching.id)
                        featuredDeck != null -> onResumeDeck(featuredDeck)
                    }
                },
                onDetailsClick = {
                    heroSeriesId?.let { onOpenSeriesDetails(it) }
                }
            )
        }

        // Dedicated 'Active Shuffles' shelf with JellyWatch vertical accent bar
        item {
            Column {
                SectionHeader(
                    title = "Active Shuffles",
                    subtitle = "Independent Smart Shuffle State"
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (displayDecks.isEmpty()) {
                    InteractiveEmptyBanner(
                        icon = Icons.Rounded.Shuffle,
                        title = "No active shuffle decks",
                        subtitle = "Launch Smart Shuffle on any series or create a broadcast channel to generate intelligent shuffle decks.",
                        actionLabel = "Create Channel",
                        onAction = onNavigateToChannels
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
                    ) {
                        items(
                            items = displayDecks,
                            key = { it.deckId },
                            contentType = { "ActiveShuffleDeckCard" }
                        ) { deck ->
                            ActiveShuffleDeckCard(
                                deck = deck,
                                onClick = { onResumeDeck(deck) },
                                onLongClick = {
                                    deck.seriesId?.let { onOpenSeriesDetails(it) }
                                },
                                onFocusChanged = { isFocused ->
                                    if (isFocused) {
                                        spotlightDeckItem = deck
                                        spotlightContinueWatching = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sequential Continue Watching row with 16:9 landscape cards
        item {
            Column {
                SectionHeader(
                    title = "Continue Watching",
                    subtitle = "Jump back in where you left off"
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (displayContinueWatching.isEmpty()) {
                    InteractiveEmptyBanner(
                        icon = Icons.Rounded.Tv,
                        title = "Nothing in progress yet",
                        subtitle = "Start watching any series from your library to track progress and next-up episodes here.",
                        actionLabel = "Browse Library",
                        onAction = onNavigateToLibrary
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
                    ) {
                        items(
                            items = displayContinueWatching,
                            key = { it.id },
                            contentType = { "ContinueWatchingCard" }
                        ) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onPlayContinueWatching(item) },
                                onLongClick = { onOpenSeriesDetails(item.id) },
                                onFocusChanged = { isFocused ->
                                    if (isFocused) {
                                        spotlightContinueWatching = item
                                        spotlightDeckItem = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern editorial section header with category eyebrow and crisp typography.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        // Vertical brand accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(JellycopterTheme.colors.primaryAccent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "•",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

/**
 * Hero Spotlight Banner (Apple TV / JellyWatch Cinematic Stage)
 * Features an optimized 248dp height to allow below-the-fold content to peek into view
 * on 1080p Android TV viewports.
 */
@Composable
private fun HeroBanner(
    title: String,
    subtitle: String,
    description: String,
    backdropUrl: String? = null,
    isRealMedia: Boolean = true,
    seriesId: String? = null,
    showResume: Boolean = true,
    showShuffle: Boolean = true,
    onResumeClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onDetailsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(248.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(JellycopterTheme.colors.surface)
            .border(BorderStroke(1.dp, Color(0x18FFFFFF)), RoundedCornerShape(14.dp))
    ) {
        Crossfade(
            targetState = backdropUrl,
            label = "HeroBackdropCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { url ->
            if (!url.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = "$title Backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(JellycopterTheme.colors.surfaceVariant)
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            JellycopterTheme.colors.surfaceVariant,
                                            JellycopterTheme.colors.surface
                                        )
                                    )
                                )
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    JellycopterTheme.colors.surfaceVariant,
                                    JellycopterTheme.colors.surface
                                )
                            )
                        )
                )
            }
        }

        val bg = JellycopterTheme.colors.background
        // Multi-stop directional scrim for cinematic contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            bg.copy(alpha = 0.96f),
                            bg.copy(alpha = 0.88f),
                            bg.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bg.copy(alpha = 0.35f),
                            bg.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .align(Alignment.CenterStart)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            // Badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRealMedia) {
                    MetadataChip(
                        text = "FEATURED",
                        accentColor = Color(0xFFC084FC),
                        backgroundColor = Color(0x28A855F7)
                    )
                    MetadataChip(
                        text = "SMART SHUFFLE",
                        accentColor = Color(0xFF38BDF8),
                        backgroundColor = Color(0x2038BDF8)
                    )
                } else {
                    MetadataChip(
                        text = "JELLYCOPTER",
                        accentColor = Color(0xFF38BDF8),
                        backgroundColor = Color(0x2038BDF8)
                    )
                    MetadataChip(
                        text = "SMART TV",
                        accentColor = Color(0xFFC084FC),
                        backgroundColor = Color(0x28A855F7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = JellycopterTheme.colors.primaryAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Tech metadata chips row (only if real media)
            if (isRealMedia) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetadataChip(text = "4K")
                    MetadataChip(text = "DOLBY VISION")
                    MetadataChip(text = "5.1 AUDIO")
                    MetadataChip(text = "TV-MA")
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = description,
                fontSize = 12.5.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 2,
                lineHeight = 17.sp,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            if (showResume || showShuffle || (seriesId != null)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showResume) {
                        FocusCard(
                            onClick = onResumeClick,
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            val bgButton = if (isFocused) Color.White else JellycopterTheme.colors.primaryAccent
                            val fgButton = if (isFocused) Color.Black else Color.White

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgButton)
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Resume Playback",
                                        modifier = Modifier.size(18.dp),
                                        tint = fgButton
                                    )
                                    Text(
                                        text = "Resume Playback",
                                        color = fgButton,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (showShuffle) {
                        FocusCard(
                            onClick = onShuffleClick,
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            val bgButton = if (isFocused) Color.White else Color(0x22FFFFFF)
                            val fgButton = if (isFocused) Color.Black else Color.White
                            val borderCol = if (isFocused) Color.White else Color(0x35FFFFFF)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgButton)
                                    .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Smart Shuffle",
                                        modifier = Modifier.size(18.dp),
                                        tint = fgButton
                                    )
                                    Text(
                                        text = "Smart Shuffle",
                                        color = fgButton,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (seriesId != null) {
                        FocusCard(
                            onClick = onDetailsClick,
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            val bgButton = if (isFocused) Color.White else Color(0x18FFFFFF)
                            val fgButton = if (isFocused) Color.Black else Color(0xFFCBD5E1)
                            val borderCol = if (isFocused) Color.White else Color(0x28FFFFFF)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgButton)
                                    .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "Series Details",
                                        modifier = Modifier.size(16.dp),
                                        tint = fgButton
                                    )
                                    Text(
                                        text = "Details",
                                        color = fgButton,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 16:9 Landscape Card (260dp x 146dp) for Active Shuffle Decks
 */
@Composable
private fun ActiveShuffleDeckCard(
    deck: ActiveShuffleDeckItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    Column(modifier = Modifier.width(260.dp)) {
        FocusCard(
            onClick = onClick,
            onLongClick = onLongClick,
            onFocusChanged = onFocusChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp),
            shape = RoundedCornerShape(12.dp)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JellycopterTheme.colors.surface)
            ) {
                val imageUrl = deck.backdropUrl ?: deck.posterUrl
                if (!imageUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = "${deck.title} Backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.82f)
                                    ),
                                    startY = 40f
                                )
                            )
                    )
                }

                // Top header inside card: badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExhaustionBadge(
                        exhaustedCount = deck.exhaustedCount,
                        totalCount = deck.totalCount
                    )

                    MetadataChip(
                        text = if (deck.isChannelMix) "MIX CHANNEL" else "CYCLE ${deck.cycleNumber}",
                        accentColor = Color(0xFFCBD5E1),
                        backgroundColor = Color(0x70000000)
                    )
                }

                // Center visual indicator with frosted badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) JellycopterTheme.colors.primaryAccent.copy(alpha = 0.35f) else Color(0x40000000))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isFocused) JellycopterTheme.colors.primaryAccent else Color(0x30FFFFFF)
                            ),
                            CircleShape
                        )
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (deck.isChannelMix) Icons.Rounded.Tv else Icons.Rounded.Shuffle,
                        contentDescription = if (deck.isChannelMix) "Mix Channel" else "Shuffle Deck",
                        modifier = Modifier.size(20.dp),
                        tint = if (isFocused) Color.White else Color(0xFFE2E8F0)
                    )
                }

                // Bottom flush progress bar (inset 4dp from bottom edge to prevent border collision)
                MidwayResumeIndicator(
                    resumePositionMs = deck.lastResumeMs,
                    durationMs = deck.durationMs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
                )
            }
        }

        // Typography underneath the card with comfortable top padding to accommodate scale elevation
        Column(
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = deck.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (deck.isChannelMix) {
                    "Virtual Broadcast Station"
                } else {
                    "${deck.exhaustedCount}/${deck.totalCount} played • Cycle ${deck.cycleNumber}"
                },
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 16:9 Landscape Card (260dp x 146dp) for Continue Watching items
 */
@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    Column(modifier = Modifier.width(260.dp)) {
        FocusCard(
            onClick = onClick,
            onLongClick = onLongClick,
            onFocusChanged = onFocusChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp),
            shape = RoundedCornerShape(12.dp)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JellycopterTheme.colors.surface)
            ) {
                val imageUrl = item.backdropUrl ?: item.posterUrl
                if (!imageUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = "${item.seriesTitle} Backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.82f)
                                    ),
                                    startY = 40f
                                )
                            )
                    )
                }

                // Top-right duration chip
                val minutesLeft = ((item.durationMs - item.resumePositionMs) / 60000).coerceAtLeast(1)
                val durationText = if (item.resumePositionMs > 0L) {
                    "${minutesLeft}m left"
                } else {
                    "Next Up • ${minutesLeft}m"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    MetadataChip(
                        text = durationText,
                        accentColor = Color(0xFFE2E8F0),
                        backgroundColor = Color(0x70000000)
                    )
                }

                // Center frosted play button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) JellycopterTheme.colors.primaryAccent.copy(alpha = 0.35f) else Color(0x40000000))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isFocused) JellycopterTheme.colors.primaryAccent else Color(0x30FFFFFF)
                            ),
                            CircleShape
                        )
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isFocused) Color.White else Color(0xFFE2E8F0),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Bottom flush progress bar (inset 4dp from bottom edge to prevent border collision)
                MidwayResumeIndicator(
                    resumePositionMs = item.resumePositionMs,
                    durationMs = item.durationMs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
                )
            }
        }

        // Typography underneath the card with comfortable top padding to accommodate scale elevation
        Column(
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = item.seriesTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "S${item.seasonNumber}:E${item.episodeNumber} • ${item.episodeTitle}",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Focusable and Interactive Empty State Banner for Android TV D-Pad navigation.
 * Eliminates dead navigation traps by providing an actionable remote shortcut.
 */
@Composable
private fun InteractiveEmptyBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onAction,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isFocused) JellycopterTheme.colors.surfaceVariant
                    else JellycopterTheme.colors.surface
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x18FFFFFF))
                            .border(BorderStroke(1.dp, Color(0x22FFFFFF)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isFocused) Color.White else JellycopterTheme.colors.primaryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.5.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) Color.White else Color(0x20FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = if (isFocused) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isFocused) Color.Black else Color.White
                    )
                }
            }
        }
    }
}
