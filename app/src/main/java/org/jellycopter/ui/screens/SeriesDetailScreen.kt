package org.jellycopter.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import org.jellycopter.core.database.ChannelEntity
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.SeasonItem
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.repository.DefaultMediaRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme
import java.util.Locale

/**
 * Fullscreen rich Series Detail view featuring:
 * - Backdrop header via Coil AsyncImage with vertical gradient scrim overlay.
 * - Poster card via Coil AsyncImage with focus border.
 * - Metadata block: title, year, rating, overview.
 * - 4-Button Action Bar with 24dp Material Icons:
 *     1. "Resume / Next Up" (Icons.Rounded.PlayArrow): plays nextUpEpisode from resume point.
 *     2. "Restart Show" (Icons.Rounded.Replay): plays S1E1 from 00:00.
 *     3. "Smart Shuffle" (Icons.Rounded.Shuffle): launches standalone smart shuffle for the show.
 *     4. "Add to Channel" (Icons.Rounded.PlaylistAdd): action to add show to channel.
 * - Season pills row and episode cards displaying thumbnail (AsyncImage), episode index, title, and play button.
 */
@Composable
fun SeriesDetailScreen(
    seriesId: String? = null,
    initialSeriesDetail: SeriesDetail? = null,
    mediaRepository: MediaRepository? = null,
    channels: List<ChannelEntity> = emptyList(),
    onResumeNextUp: (SeriesDetail, EpisodeItem?) -> Unit = { _, _ -> },
    onRestartShow: (SeriesDetail, EpisodeItem?) -> Unit = { _, _ -> },
    onSmartShuffle: (SeriesDetail) -> Unit = {},
    onAddToChannel: (SeriesDetail) -> Unit = {},
    onAddToExistingChannel: (SeriesDetail, ChannelEntity) -> Unit = { _, _ -> },
    onPlayEpisode: (SeriesDetail, EpisodeItem) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val resolvedRepo = mediaRepository ?: remember {
        try {
            org.koin.java.KoinJavaComponent.get(MediaRepository::class.java)
        } catch (_: Exception) {
            DefaultMediaRepository()
        }
    }

    val seriesDetailState by produceState(initialValue = initialSeriesDetail, key1 = seriesId) {
        if (initialSeriesDetail != null) {
            value = initialSeriesDetail
        } else if (!seriesId.isNullOrBlank()) {
            value = resolvedRepo.getSeriesDetail(seriesId)
        }
    }

    val seriesDetail = seriesDetailState

    if (seriesDetail == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(JellycopterTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = JellycopterTheme.colors.primaryAccent,
                modifier = Modifier.size(48.dp)
            )
        }
        return
    }

    var selectedSeasonId by remember(seriesDetail.id) {
        val nextUpSeason = seriesDetail.seasons.firstOrNull { season ->
            season.seasonNumber == seriesDetail.nextUpEpisode?.seasonNumber
        }
        mutableStateOf(nextUpSeason?.id ?: seriesDetail.seasons.firstOrNull()?.id ?: "")
    }

    androidx.compose.runtime.LaunchedEffect(seriesDetail.seasons) {
        if (selectedSeasonId.isBlank() && seriesDetail.seasons.isNotEmpty()) {
            val nextUpSeason = seriesDetail.seasons.firstOrNull { season ->
                season.seasonNumber == seriesDetail.nextUpEpisode?.seasonNumber
            }
            selectedSeasonId = nextUpSeason?.id ?: seriesDetail.seasons.first().id
        }
    }

    var showAddToChannelDialog by remember { mutableStateOf(false) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }

    val activeSeason = seriesDetail.seasons.firstOrNull { it.id == selectedSeasonId }
        ?: seriesDetail.seasons.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // 1. Backdrop Header with vertical gradient scrim overlay
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    if (!seriesDetail.backdropUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = seriesDetail.backdropUrl,
                            contentDescription = "${seriesDetail.name} Backdrop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0F1322))
                                )
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    JellycopterTheme.colors.surfaceVariant,
                                                    JellycopterTheme.colors.background
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
                                        listOf(
                                            JellycopterTheme.colors.surfaceVariant,
                                            JellycopterTheme.colors.background
                                        )
                                    )
                                )
                        )
                    }

                    // Vertical gradient scrim overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Transparent,
                                        JellycopterTheme.colors.background.copy(alpha = 0.85f),
                                        JellycopterTheme.colors.background
                                    )
                                )
                            )
                    )

                    // Back button overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp)
                    ) {
                        FocusCard(
                            onClick = onBack,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isFocused) Color.White.copy(alpha = 0.35f)
                                        else Color.Black.copy(alpha = 0.55f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 2. Poster card and Metadata block
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster card via Coil AsyncImage with focus border
                    FocusCard(
                        onClick = { onResumeNextUp(seriesDetail, seriesDetail.nextUpEpisode) },
                        modifier = Modifier
                            .width(145.dp)
                            .height(218.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    BorderStroke(
                                        width = 1.dp,
                                        color = if (isFocused) Color(0x35FFFFFF) else Color(0x18FFFFFF)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            if (!seriesDetail.posterUrl.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = seriesDetail.posterUrl,
                                    contentDescription = "${seriesDetail.name} Poster",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF161B30)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp),
                                                color = JellycopterTheme.colors.primaryAccent,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    },
                                    error = {
                                        SeriesPosterFallback(seriesName = seriesDetail.name)
                                    }
                                )
                            } else {
                                SeriesPosterFallback(seriesName = seriesDetail.name)
                            }
                        }
                    }

                    // Metadata Block
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "SERIES DETAIL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = JellycopterTheme.colors.primaryAccent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = seriesDetail.name,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (seriesDetail.premiereYear != null) {
                                Text(
                                    text = "${seriesDetail.premiereYear}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (seriesDetail.communityRating != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = "Rating",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFFFBBF24)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f", seriesDetail.communityRating),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            val seasonCount = seriesDetail.seasons.size
                            if (seasonCount > 0) {
                                Text(
                                    text = "$seasonCount ${if (seasonCount == 1) "Season" else "Seasons"}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        if (!seriesDetail.overview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = seriesDetail.overview,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // 3. 4-Button Action Bar with 24dp Material Icons
            item {
                SeriesDetailActionBar(
                    seriesDetail = seriesDetail,
                    onResumeNextUp = {
                        onResumeNextUp(seriesDetail, seriesDetail.nextUpEpisode)
                    },
                    onRestartShow = {
                        val s1e1 = seriesDetail.seasons.firstOrNull { it.seasonNumber == 1 }
                            ?.episodes?.firstOrNull { it.indexNumber == 1 }
                            ?: seriesDetail.seasons.firstOrNull()?.episodes?.firstOrNull()
                        onRestartShow(seriesDetail, s1e1)
                    },
                    onSmartShuffle = {
                        onSmartShuffle(seriesDetail)
                    },
                    onAddToChannel = {
                        showAddToChannelDialog = true
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            // 4. Season Pills Row
            if (seriesDetail.seasons.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Seasons",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(seriesDetail.seasons, key = { it.id }) { season ->
                            val isSelected = season.id == selectedSeasonId
                            SeasonPill(
                                season = season,
                                isSelected = isSelected,
                                onClick = { selectedSeasonId = season.id }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // 5. Episode Cards List
            if (activeSeason != null && activeSeason.episodes.isNotEmpty()) {
                items(activeSeason.episodes, key = { it.id }) { episode ->
                    EpisodeRowCard(
                        episode = episode,
                        fallbackThumbnailUrl = resolvedRepo.buildPrimaryImageUrl(episode.id),
                        onPlay = { onPlayEpisode(seriesDetail, episode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 5.dp)
                    )
                }
            } else if (activeSeason != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No episodes available in this season.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        if (showAddToChannelDialog) {
            AddToChannelDialog(
                seriesName = seriesDetail.name,
                channels = channels,
                onCreateNewChannel = {
                    showAddToChannelDialog = false
                    onAddToChannel(seriesDetail)
                },
                onSelectChannel = { channel ->
                    showAddToChannelDialog = false
                    onAddToExistingChannel(seriesDetail, channel)
                    confirmationMessage = "Added to ${channel.name}"
                },
                onDismiss = { showAddToChannelDialog = false }
            )
        }

        // Channel Added Toast/Banner overlay
        val confirmation = confirmationMessage
        if (confirmation != null) {
            androidx.compose.runtime.LaunchedEffect(confirmation) {
                delay(2500)
                confirmationMessage = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Added",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = confirmation,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 4-Button Action Bar featuring 24dp Material Icons.
 */
@Composable
fun SeriesDetailActionBar(
    seriesDetail: SeriesDetail,
    onResumeNextUp: () -> Unit,
    onRestartShow: () -> Unit,
    onSmartShuffle: () -> Unit,
    onAddToChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val nextUp = seriesDetail.nextUpEpisode
        val resumeText = if (nextUp != null) {
            "Resume S${nextUp.seasonNumber}E${nextUp.indexNumber}"
        } else {
            "Resume / Next Up"
        }

        ActionButton(
            label = resumeText,
            icon = Icons.Rounded.PlayArrow,
            isPrimary = true,
            onClick = onResumeNextUp,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            label = "Restart Show",
            icon = Icons.Rounded.Replay,
            isPrimary = false,
            onClick = onRestartShow,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            label = "Smart Shuffle",
            icon = Icons.Rounded.Shuffle,
            isPrimary = false,
            onClick = onSmartShuffle,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            label = "Add to Channel",
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            isPrimary = false,
            onClick = onAddToChannel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(6.dp)
    ) { isFocused ->
        val bgColor = when {
            isFocused -> Color.White
            isPrimary -> JellycopterTheme.colors.primaryAccent
            else -> JellycopterTheme.colors.surface
        }
        val contentColor = when {
            isFocused -> Color.Black
            else -> Color.White
        }
        val iconTint = when {
            isFocused -> Color.Black
            isPrimary -> Color.White
            else -> JellycopterTheme.colors.primaryAccent
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = when {
                        isFocused -> Color.White
                        isPrimary -> JellycopterTheme.colors.primaryAccent
                        else -> Color(0x18FFFFFF)
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SeasonPill(
    season: SeasonItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(36.dp)
    ) { isFocused ->
        val bgColor = when {
            isFocused -> Color.White
            isSelected -> JellycopterTheme.colors.primaryAccent
            else -> JellycopterTheme.colors.surface
        }
        val textColor = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color(0xFF94A3B8)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = when {
                        isFocused -> Color.White
                        isSelected -> JellycopterTheme.colors.primaryAccent
                        else -> Color(0x18FFFFFF)
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = season.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
private fun EpisodeRowCard(
    episode: EpisodeItem,
    fallbackThumbnailUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onPlay,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else JellycopterTheme.colors.surface)
                .border(
                    width = 1.dp,
                    color = if (isFocused) Color(0x35FFFFFF) else Color(0x12FFFFFF),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Episode thumbnail via Coil AsyncImage
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!fallbackThumbnailUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = fallbackThumbnailUrl,
                            contentDescription = episode.name,
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
                                            Brush.verticalGradient(
                                                listOf(JellycopterTheme.colors.surfaceVariant, JellycopterTheme.colors.surface)
                                            )
                                        )
                                )
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }

                // Episode Index, Name, Overview, Runtime
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(JellycopterTheme.colors.primaryAccent.copy(alpha = 0.15f))
                                .border(1.dp, JellycopterTheme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "E${episode.indexNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JellycopterTheme.colors.primaryAccent
                            )
                        }
                        Text(
                            text = episode.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!episode.overview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = episode.overview,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (episode.runTimeTicks > 0L) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val minutes = (episode.runTimeTicks / 10_000_000L) / 60
                        Text(
                            text = "$minutes min",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Play Button visual indicator (card click triggers onPlay)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, if (isFocused) Color.White else Color(0x18FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(20.dp),
                        tint = if (isFocused) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToChannelDialog(
    seriesName: String,
    channels: List<ChannelEntity>,
    onCreateNewChannel: () -> Unit,
    onSelectChannel: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellycopterTheme.colors.surface,
        title = {
            Text(
                text = "Add to Channel",
                color = JellycopterTheme.colors.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Choose a channel for \"$seriesName\":",
                        color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Option: Create New Channel
                item {
                    FocusCard(
                        onClick = onCreateNewChannel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isFocused) JellycopterTheme.colors.primaryAccent.copy(alpha = 0.25f)
                                    else JellycopterTheme.colors.surfaceVariant
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Create Channel",
                                tint = JellycopterTheme.colors.primaryAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Create New Channel",
                                    color = JellycopterTheme.colors.primaryAccent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pre-fill new channel with $seriesName",
                                    color = JellycopterTheme.colors.onSurface.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (channels.isNotEmpty()) {
                    item {
                        Text(
                            text = "Existing Channels",
                            color = JellycopterTheme.colors.onSurface.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }

                    items(channels, key = { it.channelId }) { channel ->
                        FocusCard(
                            onClick = { onSelectChannel(channel) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isFocused) JellycopterTheme.colors.primaryAccent.copy(alpha = 0.25f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tv,
                                    contentDescription = channel.name,
                                    tint = if (isFocused) JellycopterTheme.colors.primaryAccent else JellycopterTheme.colors.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        color = JellycopterTheme.colors.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!channel.description.isNullOrBlank()) {
                                        Text(
                                            text = channel.description,
                                            color = JellycopterTheme.colors.onSurface.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                Text("Cancel", color = JellycopterTheme.colors.primaryAccent)
            }
        }
    )
}
