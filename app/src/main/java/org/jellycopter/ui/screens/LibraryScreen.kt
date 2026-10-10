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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.model.VirtualFolder
import org.jellycopter.core.repository.DefaultMediaRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.FocusCard
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

enum class CatalogFilter(val label: String) {
    ALL("All Titles"),
    MISSING_SEASONS("Missing Seasons"),
    TOP_RATED("Top Rated (8.5+)")
}

enum class CatalogSort(val label: String) {
    NAME_ASC("Name (A-Z)"),
    RATING_DESC("Rating (High-Low)"),
    YEAR_DESC("Year (Newest)")
}

/**
 * High-performance, 10-foot TV-optimized media library browsing screen:
 * - Responsive LazyVerticalGrid with GridCells.Adaptive (175dp minSize).
 * - Full 2:3 vertical portrait poster cards with SubcomposeAsyncImage shimmer and error fallback.
 * - Sibling non-nested remote D-pad focus for missing season acquisition (no FocusCard-in-FocusCard traps).
 * - Multi-criteria real-time filtering (All, Missing Seasons, Top Rated) and sorting (Name, Rating, Year).
 * - Dynamic category tabs with high-contrast active-focus preservation.
 * - Remote-friendly search bar handling ImeAction.Search and TV focus glow.
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
    val focusManager = LocalFocusManager.current
    val resolvedRepo = mediaRepository ?: remember {
        try {
            org.koin.java.KoinJavaComponent.get(MediaRepository::class.java)
        } catch (_: Exception) {
            DefaultMediaRepository()
        }
    }

    var foldersLoading by remember { mutableStateOf(true) }
    var foldersError by remember { mutableStateOf(false) }

    val foldersState by produceState<List<VirtualFolder>>(initialValue = emptyList()) {
        foldersLoading = true
        foldersError = false
        value = try {
            val list = resolvedRepo.getVirtualFolders()
            foldersLoading = false
            list
        } catch (_: Exception) {
            foldersLoading = false
            foldersError = true
            emptyList()
        }
    }

    var selectedFolderId by remember { mutableStateOf("") }
    LaunchedEffect(foldersState) {
        if (foldersState.isNotEmpty() && (selectedFolderId.isEmpty() || foldersState.none { it.id == selectedFolderId })) {
            selectedFolderId = foldersState.first().id
        }
    }

    var seriesLoading by remember { mutableStateOf(false) }
    var seriesError by remember { mutableStateOf(false) }

    val seriesInFolderState by produceState<List<SeriesDetail>>(
        initialValue = emptyList(),
        key1 = selectedFolderId
    ) {
        if (selectedFolderId.isNotBlank()) {
            seriesLoading = true
            seriesError = false
            value = try {
                val list = resolvedRepo.getSeriesInFolder(selectedFolderId)
                seriesLoading = false
                list
            } catch (_: Exception) {
                seriesLoading = false
                seriesError = true
                emptyList()
            }
        } else {
            value = emptyList()
        }
    }

    val requestedMap = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(CatalogFilter.ALL) }
    var selectedSort by remember { mutableStateOf(CatalogSort.NAME_ASC) }

    // Accurate season diff calculation
    val effectiveLegacySeries = remember(seriesList, seriesInFolderState) {
        if (seriesList != null) {
            seriesList
        } else {
            seriesInFolderState.mapNotNull { series ->
                val acquiredSeasons = series.seasons.filter { it.episodes.isNotEmpty() }
                    .map { it.seasonNumber }.filter { it > 0 }.toSet()
                val allSeasonNumbers = series.seasons.map { it.seasonNumber }.filter { it > 0 }.toSet()

                val maxSeason = allSeasonNumbers.maxOrNull() ?: acquiredSeasons.maxOrNull() ?: 1
                val missing = if (allSeasonNumbers.isNotEmpty()) {
                    (1..maxSeason).filter { it !in acquiredSeasons }
                } else {
                    emptyList()
                }

                LibrarySeriesItem(
                    seriesId = series.id,
                    title = series.name,
                    totalSeasons = maxSeason,
                    localSeasonsCount = acquiredSeasons.size,
                    missingSeasonNumbers = missing,
                    tmdbId = series.tmdbId ?: 0
                )
            }
        }
    }

    val missingSeasonsBySeriesId = remember(effectiveLegacySeries) {
        effectiveLegacySeries.associate { it.seriesId to it.missingSeasonNumbers }
    }

    val seriesItemBySeriesId = remember(effectiveLegacySeries) {
        effectiveLegacySeries.associateBy { it.seriesId }
    }

    // Filtered & Sorted Series
    val processedSeries = remember(
        seriesInFolderState,
        searchQuery,
        selectedFilter,
        selectedSort,
        missingSeasonsBySeriesId
    ) {
        var list = if (searchQuery.isBlank()) {
            seriesInFolderState
        } else {
            seriesInFolderState.filter { series ->
                series.name.contains(searchQuery, ignoreCase = true) ||
                    (!series.overview.isNullOrBlank() && series.overview.contains(searchQuery, ignoreCase = true))
            }
        }

        // Apply filter
        list = when (selectedFilter) {
            CatalogFilter.ALL -> list
            CatalogFilter.MISSING_SEASONS -> list.filter {
                (missingSeasonsBySeriesId[it.id]?.isNotEmpty() == true)
            }
            CatalogFilter.TOP_RATED -> list.filter {
                (it.communityRating ?: 0f) >= 8.5f
            }
        }

        // Apply sort
        when (selectedSort) {
            CatalogSort.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            CatalogSort.RATING_DESC -> list.sortedByDescending { it.communityRating ?: 0f }
            CatalogSort.YEAR_DESC -> list.sortedByDescending { it.premiereYear ?: 0 }
        }
    }

    val missingShowsInFolder = remember(effectiveLegacySeries) {
        effectiveLegacySeries.filter { it.missingSeasonNumbers.isNotEmpty() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 175.dp),
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Block (Full Width)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "CATALOG",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = JellycopterTheme.colors.primaryAccent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Media Library",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${processedSeries.size} Titles • ${foldersState.size} Categories",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Explore titles, browse virtual folders, and track unacquired seasons",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // 2. Category Tabs & Filter Chips (Full Width)
        if (foldersState.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Category Folders Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
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

                    // Filter & Sort Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CatalogFilter.entries.forEach { filter ->
                                val isFilterActive = selectedFilter == filter
                                val countSuffix = when (filter) {
                                    CatalogFilter.MISSING_SEASONS -> " (${missingShowsInFolder.size})"
                                    else -> ""
                                }
                                FocusCard(
                                    onClick = { selectedFilter = filter },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) { isFocused ->
                                    val bg = when {
                                        isFocused -> Color.White
                                        isFilterActive -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.25f)
                                        else -> JellycopterTheme.colors.surface
                                    }
                                    val textCol = when {
                                        isFocused -> Color.Black
                                        isFilterActive -> JellycopterTheme.colors.primaryAccent
                                        else -> Color(0xFF94A3B8)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .background(bg)
                                            .border(
                                                width = 1.dp,
                                                color = if (isFilterActive) JellycopterTheme.colors.primaryAccent else Color(0x18FFFFFF),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                             .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${filter.label}$countSuffix",
                                            fontSize = 11.sp,
                                            fontWeight = if (isFilterActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                                            color = textCol
                                        )
                                    }
                                }
                            }
                        }

                        // Cycle Sort Button
                        FocusCard(
                            onClick = {
                                selectedSort = when (selectedSort) {
                                    CatalogSort.NAME_ASC -> CatalogSort.RATING_DESC
                                    CatalogSort.RATING_DESC -> CatalogSort.YEAR_DESC
                                    CatalogSort.YEAR_DESC -> CatalogSort.NAME_ASC
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(if (isFocused) Color.White else JellycopterTheme.colors.surface)
                                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "Sort",
                                        tint = if (isFocused) Color.Black else Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = selectedSort.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isFocused) Color.Black else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. TV Search Bar (Full Width)
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search titles or descriptions across catalog...",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotBlank() || isSearchFocused) JellycopterTheme.colors.primaryAccent else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = JellycopterTheme.colors.surfaceVariant,
                    unfocusedContainerColor = JellycopterTheme.colors.surface,
                    focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = JellycopterTheme.colors.primaryAccent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .onFocusChanged { isSearchFocused = it.isFocused }
            )
        }

        // 4. Section Subheader (Full Width)
        item(span = { GridItemSpan(maxLineSpan) }) {
            val folderName = foldersState.firstOrNull { it.id == selectedFolderId }?.name ?: "All Titles"
            val filterNote = if (selectedFilter != CatalogFilter.ALL) " • ${selectedFilter.label}" else ""
            val searchNote = if (searchQuery.isNotBlank()) " matching \"$searchQuery\"" else ""
            Text(
                text = "$folderName$filterNote$searchNote (${processedSeries.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        // 5. Loading / Empty States (Full Width)
        if (processedSeries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        foldersLoading || seriesLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = JellycopterTheme.colors.primaryAccent,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Loading catalog items...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        foldersError || seriesError -> {
                            Text(
                                text = "Unable to connect to media server. Check connection in Settings.",
                                color = Color(0xFFF43F5E),
                                fontSize = 14.sp
                            )
                        }
                        searchQuery.isNotBlank() -> {
                            Text(
                                text = "No series matching \"$searchQuery\"",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                        selectedFilter == CatalogFilter.MISSING_SEASONS -> {
                            Text(
                                text = "All titles in this category have complete seasons. No missing seasons found.",
                                color = Color(0xFF10B981),
                                fontSize = 14.sp
                            )
                        }
                        else -> {
                            Text(
                                text = "No titles found in this library folder.",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            // 6. Individual Series Poster Cards in Grid (2:3 Vertical Aspect Ratio)
            items(processedSeries, key = { it.id }) { series ->
                val missingNums = missingSeasonsBySeriesId[series.id] ?: emptyList()
                SeriesPosterCard(
                    series = series,
                    missingSeasonNumbers = missingNums,
                    onClick = {
                        onOpenSeriesId(series.id)
                        seriesItemBySeriesId[series.id]?.let { onOpenSeries(it) }
                    }
                )
            }
        }

        // 7. Missing Season Acquisition Section (Full Width, Sibling Focus)
        if (missingShowsInFolder.isNotEmpty() && selectedFilter != CatalogFilter.MISSING_SEASONS) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = "CATALOG STATUS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = JellycopterTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Missing Season Diff (${missingShowsInFolder.size} Series Incomplete)",
                        fontSize = 20.sp,
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

            items(missingShowsInFolder, key = { "missing_${it.seriesId}" }, span = { GridItemSpan(maxLineSpan) }) { seriesItem ->
                MissingSeasonCatalogRow(
                    series = seriesItem,
                    requestedMap = requestedMap,
                    onOpen = {
                        onOpenSeriesId(seriesItem.seriesId)
                        onOpenSeries(seriesItem)
                    },
                    onRequestMissing = { seasonNum ->
                        requestedMap["${seriesItem.seriesId}_$seasonNum"] = true
                        onRequestMissingSeason(seriesItem, seasonNum)
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
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(40.dp)
    ) { isFocused ->
        val bgColor = when {
            isFocused && isSelected -> JellycopterTheme.colors.primaryAccent
            isFocused -> Color.White
            isSelected -> JellycopterTheme.colors.primaryAccent
            else -> JellycopterTheme.colors.surface
        }
        val textColor = when {
            isFocused && isSelected -> Color.White
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color(0xFF94A3B8)
        }
        val iconTint = when {
            isFocused && isSelected -> Color.White
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color(0xFF94A3B8)
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = folder.name,
                    modifier = Modifier.size(16.dp),
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
    missingSeasonNumbers: List<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else JellycopterTheme.colors.surface)
        ) {
            // 2:3 Vertical Portrait Poster Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
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

                // Top-right Missing Season Warning Chip
                if (missingSeasonNumbers.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xE6E11D48))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (missingSeasonNumbers.size == 1) "S${missingSeasonNumbers.first()} Missing" else "${missingSeasonNumbers.size} Sns Missing",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                // Top-left Rating Chip
                if (series.communityRating != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 2.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Rating",
                                modifier = Modifier.size(10.dp),
                                tint = Color(0xFFFBBF24)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", series.communityRating),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Series Name & Year
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = series.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = series.premiereYear?.toString() ?: "TV Series",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    val seasonCount = series.seasons.size
                    if (seasonCount > 0) {
                        Text(
                            text = "$seasonCount ${if (seasonCount == 1) "Season" else "Seasons"}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * TV-Friendly Sibling Focus Row for Catalog Status & Missing Seasons.
 * Eliminates nested FocusCard traps: the show detail card and request buttons are separate siblings.
 */
@Composable
private fun MissingSeasonCatalogRow(
    series: LibrarySeriesItem,
    requestedMap: Map<String, Boolean>,
    onOpen: () -> Unit,
    onRequestMissing: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Card: Opens series details (Independent FocusCard)
        FocusCard(
            onClick = onOpen,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(10.dp)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else JellycopterTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = series.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${series.localSeasonsCount} of ${series.totalSeasons} Seasons in Library",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Text(
                        text = "Open Details",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = JellycopterTheme.colors.primaryAccent
                    )
                }
            }
        }

        // Right Sibling Actions: Independent FocusCards per missing season (NOT nested!)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            series.missingSeasonNumbers.forEach { seasonNum ->
                val isRequested = requestedMap["${series.seriesId}_$seasonNum"] == true
                if (isRequested) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x2010B981))
                            .border(1.dp, Color(0x6010B981), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Requested",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF10B981)
                            )
                            Text(
                                text = "S$seasonNum REQUESTED",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else {
                    FocusCard(
                        onClick = { onRequestMissing(seasonNum) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) Color.White else Color(0x28F43F5E))
                                .border(
                                    BorderStroke(1.dp, if (isFocused) Color.White else Color(0x60F43F5E)),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Request S$seasonNum",
                                color = if (isFocused) Color.Black else Color(0xFFFDA4AF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JellycopterTheme.colors.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
