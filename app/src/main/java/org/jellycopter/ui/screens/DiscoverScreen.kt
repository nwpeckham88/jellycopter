package org.jellycopter.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import org.jellycopter.core.model.MediaDiscoverItem
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme

@Composable
fun DiscoverScreen(
    trending: List<MediaDiscoverItem> = defaultTrendingItems(),
    popular: List<MediaDiscoverItem> = defaultPopularItems(),
    upcoming: List<MediaDiscoverItem> = defaultUpcomingItems(),
    onRequestMedia: (MediaDiscoverItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var longPressItem by remember { mutableStateOf<MediaDiscoverItem?>(null) }
    val requestedItemIds = remember { mutableStateMapOf<Int, String>() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "Jellyseerr Discovery",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Click to 1-Click Request • Long-press remote for Season & Quality options",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        item {
            DiscoveryCarousel(
                title = "Trending This Week",
                icon = Icons.Rounded.Whatshot,
                items = trending,
                requestedMap = requestedItemIds,
                onRequest = { item ->
                    requestedItemIds[item.id] = "Requested!"
                    onRequestMedia(item)
                },
                onLongPress = { item -> longPressItem = item }
            )
        }

        item {
            DiscoveryCarousel(
                title = "Popular TV & Movies",
                icon = Icons.Rounded.Star,
                items = popular,
                requestedMap = requestedItemIds,
                onRequest = { item ->
                    requestedItemIds[item.id] = "Requested!"
                    onRequestMedia(item)
                },
                onLongPress = { item -> longPressItem = item }
            )
        }

        item {
            DiscoveryCarousel(
                title = "Upcoming Releases",
                icon = Icons.Rounded.CalendarToday,
                items = upcoming,
                requestedMap = requestedItemIds,
                onRequest = { item ->
                    requestedItemIds[item.id] = "Requested!"
                    onRequestMedia(item)
                },
                onLongPress = { item -> longPressItem = item }
            )
        }
    }

    // Long-press season & quality options dialog
    longPressItem?.let { item ->
        SeasonQualityDialog(
            item = item,
            onDismiss = { longPressItem = null },
            onConfirmRequest = {
                requestedItemIds[item.id] = "Custom Requested!"
                onRequestMedia(item)
                longPressItem = null
            }
        )
    }
}

@Composable
private fun DiscoveryCarousel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    items: List<MediaDiscoverItem>,
    requestedMap: Map<Int, String>,
    onRequest: (MediaDiscoverItem) -> Unit,
    onLongPress: (MediaDiscoverItem) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = JellycopterTheme.colors.primaryAccent
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                DiscoveryCard(
                    item = item,
                    feedbackText = requestedMap[item.id],
                    onClick = { onRequest(item) },
                    onLongClick = { onLongPress(item) }
                )
            }
        }
    }
}

@Composable
private fun DiscoveryCard(
    item: MediaDiscoverItem,
    feedbackText: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .width(150.dp)
            .height(230.dp),
        shape = RoundedCornerShape(12.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JellycopterTheme.colors.surface)
        ) {
            // Poster Image
            if (!item.posterPath.isNullOrBlank()) {
                val fullPosterUrl = if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w500${item.posterPath}"
                SubcomposeAsyncImage(
                    model = fullPosterUrl,
                    contentDescription = item.title,
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
                                .background(JellycopterTheme.colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.mediaType == "tv") Icons.Rounded.Tv else Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = JellycopterTheme.colors.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(JellycopterTheme.colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaType == "tv") Icons.Rounded.Tv else Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = JellycopterTheme.colors.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Bottom Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 100f
                        )
                    )
            )

            // Content Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Tag: Media type + Star
                Column {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${item.mediaType.uppercase()} •",
                            fontSize = 12.sp,
                            color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Rating",
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "${item.voteAverage}",
                            fontSize = 12.sp,
                            color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // 1-Click request or animated feedback status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                feedbackText != null -> Color(0xFF10B981) // Green success
                                isFocused -> JellycopterTheme.colors.primaryAccent
                                else -> JellycopterTheme.colors.surfaceVariant
                            }
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = feedbackText ?: (if (isFocused) "1-Click Request" else "Request"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused && feedbackText == null) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonQualityDialog(
    item: MediaDiscoverItem,
    onDismiss: () -> Unit,
    onConfirmRequest: () -> Unit
) {
    var selectAllSeasons by remember { mutableStateOf(true) }
    var qualityProfile by remember { mutableStateOf("1080p / 4K UHD") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellycopterTheme.colors.surface,
        title = {
            Text(
                text = "Request Options: ${item.title}",
                color = JellycopterTheme.colors.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Configure season options and quality profile overrides for Radarr/Sonarr.",
                    fontSize = 13.sp,
                    color = JellycopterTheme.colors.onSurface.copy(alpha = 0.8f)
                )

                FocusCard(
                    onClick = { selectAllSeasons = !selectAllSeasons },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Checkbox(
                            checked = selectAllSeasons,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = JellycopterTheme.colors.primaryAccent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Request All Future & Current Seasons",
                            color = JellycopterTheme.colors.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }

                Text(
                    text = "Quality Profile: $qualityProfile",
                    color = JellycopterTheme.colors.secondaryAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            FocusCard(
                onClick = onConfirmRequest,
                shape = RoundedCornerShape(8.dp)
            ) { isFocused ->
                Box(
                    modifier = Modifier
                        .background(if (isFocused) JellycopterTheme.colors.borderGlow else JellycopterTheme.colors.primaryAccent)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Confirm Request", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            FocusCard(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) { isFocused ->
                Box(
                    modifier = Modifier
                        .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Cancel", color = JellycopterTheme.colors.onSurface, fontSize = 13.sp)
                }
            }
        }
    )
}

private fun defaultTrendingItems() = listOf(
    MediaDiscoverItem(id = 101, title = "The Penguin", mediaType = "tv", overview = "overview", releaseDate = "2024-09-19", voteAverage = 8.5),
    MediaDiscoverItem(id = 102, title = "Dune: Part Two", mediaType = "movie", overview = "overview", releaseDate = "2024-03-01", voteAverage = 8.8),
    MediaDiscoverItem(id = 103, title = "Shogun", mediaType = "tv", overview = "overview", releaseDate = "2024-02-27", voteAverage = 8.7),
    MediaDiscoverItem(id = 104, title = "Alien: Romulus", mediaType = "movie", overview = "overview", releaseDate = "2024-08-16", voteAverage = 7.6)
)

private fun defaultPopularItems() = listOf(
    MediaDiscoverItem(id = 201, title = "Arcane", mediaType = "tv", overview = "overview", releaseDate = "2024-11-09", voteAverage = 9.0),
    MediaDiscoverItem(id = 202, title = "Gladiator II", mediaType = "movie", overview = "overview", releaseDate = "2024-11-22", voteAverage = 7.4),
    MediaDiscoverItem(id = 203, title = "The Bear", mediaType = "tv", overview = "overview", releaseDate = "2024-06-27", voteAverage = 8.6)
)

private fun defaultUpcomingItems() = listOf(
    MediaDiscoverItem(id = 301, title = "Severance S2", mediaType = "tv", overview = "overview", releaseDate = "2025-01-17", voteAverage = 8.9),
    MediaDiscoverItem(id = 302, title = "Squid Game S2", mediaType = "tv", overview = "overview", releaseDate = "2024-12-26", voteAverage = 8.1)
)
