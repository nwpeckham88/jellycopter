package org.jellycopter.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
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
    modifier: Modifier = Modifier
) {
    // Resolve live continue watching from MediaRepository if not explicitly provided
    val liveContinueWatching by produceState<List<ContinueWatchingItem>>(initialValue = emptyList()) {
        if (mediaRepository != null) {
            try {
                val folders = mediaRepository.getVirtualFolders()
                val allSeries = folders.flatMap { folder ->
                    runCatching { mediaRepository.getSeriesInFolder(folder.id) }.getOrDefault(emptyList())
                }
                if (allSeries.isNotEmpty()) {
                    val items = allSeries.take(8).map { series ->
                        val nextUp = mediaRepository.getNextUpForSeries(series.id)
                            ?: mediaRepository.getFirstEpisodeForSeries(series.id)
                        ContinueWatchingItem(
                            id = series.id,
                            seriesTitle = series.name,
                            episodeTitle = nextUp?.name ?: "Episode 1",
                            seasonNumber = nextUp?.seasonNumber ?: 1,
                            episodeNumber = nextUp?.indexNumber ?: 1,
                            resumePositionMs = 0L,
                            durationMs = if ((nextUp?.runTimeTicks ?: 0L) > 0L) nextUp!!.runTimeTicks / 10_000L else 2_700_000L,
                            backdropUrl = series.backdropUrl ?: mediaRepository.buildBackdropImageUrl(series.id),
                            posterUrl = series.posterUrl ?: mediaRepository.buildPrimaryImageUrl(series.id)
                        )
                    }
                    value = items
                }
            } catch (_: Exception) {
                // Ignore failure
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

                ActiveShuffleDeckItem(
                    deckId = deck.deckId,
                    title = deckTitle,
                    cycleNumber = deck.cycleNumber,
                    exhaustedCount = deck.cards.count { it.isExhausted },
                    totalCount = deck.cards.size,
                    lastResumeMs = deck.midwayResumeMs,
                    durationMs = 1_800_000L,
                    isChannelMix = isChannel,
                    backdropUrl = effectiveSeriesId?.let { mediaRepository?.buildBackdropImageUrl(it) },
                    posterUrl = effectiveSeriesId?.let { mediaRepository?.buildPrimaryImageUrl(it) }
                )
            }
        } else {
            emptyList()
        }
    }

    val heroItem = displayContinueWatching.firstOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Hero Spotlight Banner (Apple TV / JellyWatch Cinematic Stage)
        item {
            HeroBanner(
                title = heroItem?.seriesTitle ?: "Welcome to Jellycopter",
                subtitle = heroItem?.let { "S${it.seasonNumber}:E${it.episodeNumber} • ${it.episodeTitle}" } ?: "Smart Television for Jellyfin",
                description = if (heroItem != null) {
                    "Smart Shuffle continuously balances episode pacing, preventing character repetition and clustering while preserving canon arcs."
                } else {
                    "Experience intelligent smart shuffle playback and virtual broadcast channels configured directly from your media library."
                },
                backdropUrl = heroItem?.backdropUrl,
                showResume = heroItem != null,
                showShuffle = heroItem != null || displayDecks.isNotEmpty(),
                onResumeClick = {
                    if (displayContinueWatching.isNotEmpty()) {
                        onPlayContinueWatching(displayContinueWatching.first())
                    }
                },
                onShuffleClick = {
                    if (heroItem != null) {
                        onSmartShuffleHero(heroItem.id)
                    } else if (displayDecks.isNotEmpty()) {
                        onResumeDeck(displayDecks.first())
                    }
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

                Spacer(modifier = Modifier.height(14.dp))

                if (displayDecks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(JellycopterTheme.colors.surface)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                tint = JellycopterTheme.colors.primaryAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "No active shuffle decks",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Launch Smart Shuffle on any series or channel to generate an intelligent shuffle deck.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        items(displayDecks, key = { it.deckId }) { deck ->
                            ActiveShuffleDeckCard(
                                deck = deck,
                                onClick = { onResumeDeck(deck) }
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

                Spacer(modifier = Modifier.height(14.dp))

                if (displayContinueWatching.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(JellycopterTheme.colors.surface)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = JellycopterTheme.colors.primaryAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Nothing in progress yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Start watching any series from your library to track progress here.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        items(displayContinueWatching, key = { it.id }) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onPlayContinueWatching(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * JellyWatch-style section header with vertical colored accent pill bar.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(JellycopterTheme.colors.primaryAccent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = JellycopterTheme.colors.onBackground
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun HeroBanner(
    title: String,
    subtitle: String,
    description: String,
    backdropUrl: String? = null,
    showResume: Boolean = true,
    showShuffle: Boolean = true,
    onResumeClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        JellycopterTheme.colors.surfaceVariant,
                        JellycopterTheme.colors.surface,
                        JellycopterTheme.colors.background
                    )
                )
            )
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = backdropUrl,
                contentDescription = "$title Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(JellycopterTheme.colors.surfaceVariant)
                    )
                }
            )
        }

        val bg = JellycopterTheme.colors.background
        // Multi-stop scrims for cinematic Apple TV depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            bg.copy(alpha = 0.98f),
                            bg.copy(alpha = 0.88f),
                            bg.copy(alpha = 0.38f),
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
                            bg.copy(alpha = 0.50f),
                            bg.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .align(Alignment.CenterStart)
                .padding(30.dp)
        ) {
            // Badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetadataChip(
                    text = "FEATURED",
                    accentColor = Color(0xFFC084FC),
                    backgroundColor = Color(0x35A855F7)
                )
                MetadataChip(
                    text = "SMART SHUFFLE",
                    accentColor = Color(0xFF38BDF8),
                    backgroundColor = Color(0x2538BDF8)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Frosted tech metadata chips row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataChip(text = "4K")
                MetadataChip(text = "DOLBY VISION")
                MetadataChip(text = "5.1 AUDIO")
                MetadataChip(text = "TV-MA")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 2,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Apple TV-style CTA Buttons
            if (showResume || showShuffle) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (showResume) {
                        FocusCard(
                            onClick = onResumeClick,
                            shape = RoundedCornerShape(20.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isFocused) Color.White
                                        else JellycopterTheme.colors.primaryAccent
                                    )
                                    .padding(horizontal = 20.dp, vertical = 9.dp),
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
                                        tint = if (isFocused) Color.Black else Color.White
                                    )
                                    Text(
                                        text = "Resume Playback",
                                        color = if (isFocused) Color.Black else Color.White,
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
                            shape = RoundedCornerShape(20.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isFocused) JellycopterTheme.colors.surfaceVariant
                                        else Color(0x25FFFFFF)
                                    )
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
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
                                        tint = Color.White
                                    )
                                    Text(
                                        text = "Smart Shuffle",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
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
 * 16:9 Landscape Card (260dp x 146dp) for Active Shuffle Decks (tvOS / JellyWatch style)
 */
@Composable
private fun ActiveShuffleDeckCard(
    deck: ActiveShuffleDeckItem,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(260.dp)) {
        FocusCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp),
            shape = RoundedCornerShape(16.dp)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(JellycopterTheme.colors.surfaceVariant, JellycopterTheme.colors.surface)
                        )
                    )
            ) {
                val imageUrl = deck.backdropUrl ?: deck.posterUrl
                if (!imageUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = "${deck.title} Backdrop",
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
                                    .background(JellycopterTheme.colors.surfaceVariant)
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }

                // Top header inside card: badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                        backgroundColor = Color(0x60000000)
                    )
                }

                // Center visual indicator
                Icon(
                    imageVector = if (deck.isChannelMix) Icons.Rounded.Tv else Icons.Rounded.Shuffle,
                    contentDescription = if (deck.isChannelMix) "Mix Channel" else "Shuffle Deck",
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    tint = Color(0x99FFFFFF)
                )

                // Bottom flush progress bar
                MidwayResumeIndicator(
                    resumePositionMs = deck.lastResumeMs,
                    durationMs = deck.durationMs,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Typography underneath the card (tvOS standard)
        Column(
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = deck.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (deck.isChannelMix) "Virtual Broadcast Station" else "${deck.exhaustedCount}/${deck.totalCount} played • ${deck.cycleNumber}st cycle",
                fontSize = 13.sp,
                color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/**
 * 16:9 Landscape Card (260dp x 146dp) for Continue Watching items (tvOS / JellyWatch style)
 */
@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(260.dp)) {
        FocusCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp),
            shape = RoundedCornerShape(16.dp)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(JellycopterTheme.colors.surfaceVariant, JellycopterTheme.colors.surface)
                        )
                    )
            ) {
                val imageUrl = item.backdropUrl ?: item.posterUrl
                if (!imageUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = "${item.seriesTitle} Backdrop",
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
                                    .background(JellycopterTheme.colors.surfaceVariant)
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }

                // Top-right duration chip
                val minutesLeft = ((item.durationMs - item.resumePositionMs) / 60000).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    MetadataChip(
                        text = "$minutesLeft min left",
                        accentColor = Color(0xFFE2E8F0),
                        backgroundColor = Color(0x75000000)
                    )
                }

                // Center subtle play icon
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0x60FFFFFF),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )

                // Bottom flush progress bar
                MidwayResumeIndicator(
                    resumePositionMs = item.resumePositionMs,
                    durationMs = item.durationMs,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Typography underneath the card (tvOS standard)
        Column(
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = item.seriesTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "S${item.seasonNumber}:E${item.episodeNumber} • ${item.episodeTitle}",
                fontSize = 13.sp,
                color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

private fun defaultContinueWatching() = emptyList<ContinueWatchingItem>()
private fun defaultActiveDecks() = emptyList<ActiveShuffleDeckItem>()
