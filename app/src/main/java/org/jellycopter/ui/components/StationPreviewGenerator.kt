package org.jellycopter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import kotlinx.serialization.json.Json
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.theme.JellycopterTheme

data class StationArtwork(
    val primaryBackdropUrl: String?,
    val primaryPosterUrl: String?,
    val showPosterUrls: List<String>,
    val showBackdropUrls: List<String>
)

object StationPreviewGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    fun extractShowIds(showsJson: String): List<String> {
        val trimmed = showsJson.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()

        // 1. Try decoding standard JSON array of strings: ["id1", "id2"]
        val listResult = runCatching {
            json.decodeFromString<List<String>>(trimmed)
        }.getOrNull()
        if (!listResult.isNullOrEmpty()) {
            return listResult.map { it.removePrefix("series:").removePrefix("channel:").trim() }.filter { it.isNotEmpty() }
        }

        // 2. Try decoding JSON single string: "id1"
        val singleResult = runCatching {
            json.decodeFromString<String>(trimmed)
        }.getOrNull()
        if (!singleResult.isNullOrBlank()) {
            return listOf(singleResult.removePrefix("series:").removePrefix("channel:").trim()).filter { it.isNotEmpty() }
        }

        // 3. Fallback: Parse bracketed strings like "[id1, id2]"
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val unbracketed = trimmed.removePrefix("[").removeSuffix("]").trim()
            if (unbracketed.isEmpty()) return emptyList()
            return unbracketed.split(",")
                .map { it.trim().trim('"', '\'').removePrefix("series:").removePrefix("channel:").trim() }
                .filter { it.isNotEmpty() }
        }

        // 4. Handle prefixed show ID e.g. "series:series_1"
        if (trimmed.startsWith("series:") || trimmed.startsWith("channel:")) {
            val clean = trimmed.removePrefix("series:").removePrefix("channel:").trim()
            if (clean.isNotEmpty()) return listOf(clean)
        }

        return emptyList()
    }

    fun resolveArtwork(showsJson: String, mediaRepository: MediaRepository?): StationArtwork {
        val showIds = extractShowIds(showsJson)
        if (showIds.isEmpty() || mediaRepository == null) {
            return StationArtwork(null, null, emptyList(), emptyList())
        }

        val backdrops = showIds.mapNotNull { mediaRepository.buildBackdropImageUrl(it) }
        val posters = showIds.mapNotNull { mediaRepository.buildPrimaryImageUrl(it) }

        return StationArtwork(
            primaryBackdropUrl = backdrops.firstOrNull(),
            primaryPosterUrl = posters.firstOrNull(),
            showPosterUrls = posters.take(4),
            showBackdropUrls = backdrops.take(4)
        )
    }

    fun resolveShowTitles(
        showsJson: String,
        mediaRepository: MediaRepository?,
        availableSeries: List<SeriesDetail> = emptyList()
    ): List<String> {
        val showIds = extractShowIds(showsJson)
        if (showIds.isEmpty()) return emptyList()

        val seriesMap = availableSeries.associateBy { it.id }

        return showIds.map { id ->
            seriesMap[id]?.name ?: id
        }
    }
}

/**
 * Renders a 16:9 preview artwork card for a shuffle station / mix channel.
 * Uses show backdrops and posters with graceful placeholder fallbacks.
 */
@Composable
fun StationPreviewArt(
    showsJson: String,
    mediaRepository: MediaRepository?,
    modifier: Modifier = Modifier,
    fallbackTitle: String = "Channel",
    isFocused: Boolean = false
) {
    val artwork = remember(showsJson, mediaRepository) {
        StationPreviewGenerator.resolveArtwork(showsJson, mediaRepository)
    }

    val posters = artwork.showPosterUrls
    val primaryBackdrop = artwork.primaryBackdropUrl

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        JellycopterTheme.colors.surfaceVariant,
                        JellycopterTheme.colors.surface
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) JellycopterTheme.colors.primaryAccent else Color(0x25FFFFFF),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Case 1: Multiple show posters (2-3) available -> display a multi-poster split collage
            posters.size >= 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val count = posters.take(3).size
                    posters.take(3).forEach { posterUrl ->
                        SubcomposeAsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(JellycopterTheme.colors.surfaceVariant)
                                )
                            },
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(JellycopterTheme.colors.surfaceVariant)
                                )
                            }
                        )
                    }
                }
                // Gradient overlay to maintain contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            )
                        )
                )
            }

            // Case 2: Single show artwork -> use backdrop or single poster
            !primaryBackdrop.isNullOrBlank() || posters.isNotEmpty() -> {
                val imageUrl = primaryBackdrop ?: posters.first()
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        StationFallbackGraphic(fallbackTitle = fallbackTitle)
                    },
                    loading = {
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
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            )
                        )
                )
            }

            // Case 3: No artwork available -> stylish fallback
            else -> {
                StationFallbackGraphic(fallbackTitle = fallbackTitle)
            }
        }

        // Station watermark badge in top-end corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = JellycopterTheme.colors.primaryAccent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "STATION",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StationFallbackGraphic(fallbackTitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        JellycopterTheme.colors.surfaceVariant,
                        JellycopterTheme.colors.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Tv,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = JellycopterTheme.colors.primaryAccent.copy(alpha = 0.6f)
        )
    }
}
