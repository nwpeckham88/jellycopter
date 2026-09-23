package org.jellycopter.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.model.VirtualFolder
import org.jellycopter.core.repository.DefaultMediaRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.components.MissingSeasonBadge
import org.jellycopter.ui.theme.JellycopterTheme
import java.util.Locale

data class LibrarySeriesItem(
    val seriesId: String,
    val title: String,
    val totalSeasons: Int,
    val localSeasonsCount: Int,
    val missingSeasonNumbers: List<Int> = emptyList(),
    val tmdbId: Int = 0
)

/**
 * Real media library browsing screen:
 * - Queries and displays Jellyfin virtual folders / library categories.
 * - Displays series cards with Coil AsyncImage posters.
 * - Clicking a series opens [SeriesDetailScreen].
 */
@Composable
fun LibraryScreen(
    mediaRepository: MediaRepository? = null,
    onOpenSeriesId: (String) -> Unit = {},
    seriesList: List<LibrarySeriesItem>? = null,
    onOpenSeries: (LibrarySeriesItem) -> Unit = {},
    onRequestMissingSeason: (LibrarySeriesItem, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val resolvedRepo = mediaRepository ?: remember {
        try {
            org.koin.java.KoinJavaComponent.get(MediaRepository::class.java)
        } catch (_: Exception) {
            DefaultMediaRepository()
        }
    }

    val foldersState by produceState<List<VirtualFolder>>(initialValue = emptyList()) {
        value = resolvedRepo.getVirtualFolders()
    }

    var selectedFolderId by remember(foldersState) {
        mutableStateOf(foldersState.firstOrNull()?.id ?: "folder_shows")
    }

    val seriesInFolderState by produceState<List<SeriesDetail>>(initialValue = emptyList(), key1 = selectedFolderId) {
        value = resolvedRepo.getSeriesInFolder(selectedFolderId)
    }

    val requestedMap = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSeries = remember(seriesInFolderState, searchQuery) {
        if (searchQuery.isBlank()) {
            seriesInFolderState
        } else {
            seriesInFolderState.filter { series ->
                series.name.contains(searchQuery, ignoreCase = true) ||
                    (!series.overview.isNullOrBlank() && series.overview.contains(searchQuery, ignoreCase = true))
            }
        }
    }

    val effectiveLegacySeries = remember(seriesList, seriesInFolderState) {
        if (seriesList != null) {
            seriesList
        } else {
            seriesInFolderState.mapNotNull { series ->
                val localNums = series.seasons.map { it.seasonNumber }.filter { it > 0 }.toSet()
                if (localNums.isEmpty()) return@mapNotNull null
                val maxSeason = localNums.maxOrNull() ?: 1
                val missing = (1..maxSeason).filter { it !in localNums }
                LibrarySeriesItem(
                    seriesId = series.id,
                    title = series.name,
                    totalSeasons = maxSeason,
                    localSeasonsCount = localNums.size,
                    missingSeasonNumbers = missing,
                    tmdbId = 0
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Media Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Explore series across all categories • Track missing seasons",
                    fontSize = 14.sp,
                    color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Search Bar with real-time text filtering
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search titles or overviews...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotBlank()) JellycopterTheme.colors.primaryAccent else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = JellycopterTheme.colors.surface,
                        unfocusedContainerColor = JellycopterTheme.colors.surface,
                        focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = JellycopterTheme.colors.primaryAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Virtual Folders Row
        if (foldersState.isNotEmpty()) {
            item {
                Text(
                    text = "Categories",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(foldersState, key = { it.id }) { folder ->
                        val isSelected = folder.id == selectedFolderId
                        FolderTabPill(
                            folder = folder,
                            isSelected = isSelected,
                            onClick = { selectedFolderId = folder.id }
                        )
                    }
                }
            }
        }

        // Series Cards Section
        item {
            val folderName = foldersState.firstOrNull { it.id == selectedFolderId }?.name ?: "Series"
            Text(
                text = if (searchQuery.isNotBlank()) "$folderName (Filtered)" else folderName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (filteredSeries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (foldersState.isEmpty()) {
                        CircularProgressIndicator(
                            color = JellycopterTheme.colors.primaryAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    } else if (searchQuery.isNotBlank()) {
                        Text(
                            text = "No series matching \"$searchQuery\"",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "No series found in this library folder.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            // Multi-row / grid layout for TV viewing (5 columns)
            val chunked = filteredSeries.chunked(5)
            chunked.forEach { rowItems ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowItems.forEach { series ->
                            Box(modifier = Modifier.weight(1f)) {
                                SeriesPosterCard(
                                    series = series,
                                    onClick = { onOpenSeriesId(series.id) }
                                )
                            }
                        }
                        if (rowItems.size < 5) {
                            repeat(5 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Missing Season Diff & Requests Section
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Missing Season Diff & 1-Click Requests",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Direct 1-click Jellyseerr request triggers on unacquired seasons",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        if (effectiveLegacySeries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All seasons in your library are complete. No missing seasons detected.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(effectiveLegacySeries, key = { it.seriesId }) { series ->
                LibrarySeriesRow(
                    series = series,
                    requestedMap = requestedMap,
                    onOpen = {
                        onOpenSeriesId(series.seriesId)
                        onOpenSeries(series)
                    },
                    onRequestMissing = { seasonNum ->
                        requestedMap["${series.seriesId}_$seasonNum"] = true
                        onRequestMissingSeason(series, seasonNum)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderTabPill(
    folder: VirtualFolder,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(38.dp)
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
        val iconTint = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color(0xFF94A3B8)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = folder.name,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
                Text(
                    text = folder.name,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun SeriesPosterCard(
    series: SeriesDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(265.dp),
        shape = RoundedCornerShape(12.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else JellycopterTheme.colors.surface)
        ) {
            // Poster Image with Coil AsyncImage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .border(
                        BorderStroke(
                            width = if (isFocused) 3.dp else 0.dp,
                            color = if (isFocused) Color.White else Color.Transparent
                        ),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
            ) {
                if (!series.posterUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = series.posterUrl,
                        contentDescription = "${series.name} Poster",
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
                                    modifier = Modifier.size(24.dp),
                                    color = JellycopterTheme.colors.primaryAccent,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        error = {
                            SeriesPosterFallback(seriesName = series.name)
                        }
                    )
                } else {
                    SeriesPosterFallback(seriesName = series.name)
                }
            }

            // Series Name & Year
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = series.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (series.premiereYear != null) {
                        Text(
                            text = "${series.premiereYear}",
                            fontSize = 12.sp,
                            color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    if (series.communityRating != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Rating",
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFFFBBF24)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", series.communityRating),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySeriesRow(
    series: LibrarySeriesItem,
    requestedMap: Map<String, Boolean>,
    onOpen: () -> Unit,
    onRequestMissing: (Int) -> Unit
) {
    FocusCard(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .height(95.dp),
        shape = RoundedCornerShape(12.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else JellycopterTheme.colors.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = series.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${series.localSeasonsCount} of ${series.totalSeasons} Seasons in Library",
                        fontSize = 13.sp,
                        color = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Missing Season Badges with direct request triggers
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    series.missingSeasonNumbers.forEach { seasonNum ->
                        val isRequested = requestedMap["${series.seriesId}_$seasonNum"] == true
                        if (isRequested) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "S$seasonNum Requested",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Requested",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        } else {
                            MissingSeasonBadge(
                                seasonNumber = seasonNum,
                                onRequestClicked = { onRequestMissing(seasonNum) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun defaultLibrarySeries() = emptyList<LibrarySeriesItem>()

@Composable
fun SeriesPosterFallback(
    seriesName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(JellycopterTheme.colors.surfaceVariant, JellycopterTheme.colors.surface)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = JellycopterTheme.colors.onSurface.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = seriesName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = JellycopterTheme.colors.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

