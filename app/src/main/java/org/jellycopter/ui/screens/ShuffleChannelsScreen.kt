package org.jellycopter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellycopter.core.database.ChannelEntity
import org.jellycopter.ui.components.ExhaustionBadge
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme

import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.StationPreviewArt
import org.jellycopter.ui.components.StationPreviewGenerator

// Legacy preview model kept for composable previews/tests
data class MixChannelItem(
    val channelId: String,
    val title: String,
    val description: String,
    val showsIncluded: List<String>,
    val cycleNumber: Int,
    val exhaustedCount: Int,
    val totalCount: Int
)

private val channelJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/**
 * Converts a live [ChannelEntity] to the UI model [MixChannelItem] for display.
 */
private fun ChannelEntity.toMixChannelItem(): MixChannelItem {
    val shows = runCatching {
        channelJson.decodeFromString<List<String>>(showsJson)
    }.getOrDefault(emptyList())
    return MixChannelItem(
        channelId = channelId,
        title = name,
        description = description ?: interleaveStrategy.toInterleaveLabel(),
        showsIncluded = shows,
        cycleNumber = 1,
        exhaustedCount = 0,
        totalCount = shows.size
    )
}

private fun String.toInterleaveLabel() = when (this) {
    "ROUND_ROBIN" -> "Round-Robin interleave"
    "RANDOM" -> "Random interleave"
    "WEIGHTED" -> "Weighted interleave"
    else -> this
}

@Composable
fun ShuffleChannelsScreen(
    /** Live channels from Room DB — when null, falls back to hardcoded preview data */
    liveChannels: List<ChannelEntity>? = null,
    mediaRepository: MediaRepository? = null,
    availableSeries: List<org.jellycopter.core.model.SeriesDetail> = emptyList(),
    onLaunchChannel: (ChannelEntity) -> Unit = {},
    onCreateChannel: () -> Unit = {},
    onEditChannel: (ChannelEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val channels = liveChannels ?: emptyList()
    val isEmpty = channels.isEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Header + "New Channel" button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VIRTUAL BROADCAST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = JellycopterTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Channels",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                // New Channel button
                FocusCard(
                    onClick = onCreateChannel,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(38.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .background(if (isFocused) Color.White else JellycopterTheme.colors.primaryAccent)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New Channel",
                                modifier = Modifier.size(16.dp),
                                tint = if (isFocused) Color.Black else Color.White
                            )
                            Text(
                                text = "New Channel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Empty state
        if (isEmpty) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF334155)
                        )
                        Text(
                            text = "No channels yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "Create a channel to mix episodes from multiple shows",
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }

        // Live channel rows
        items(channels, key = { it.channelId }) { entity ->
            ChannelRow(
                entity = entity,
                mediaRepository = mediaRepository,
                availableSeries = availableSeries,
                onLaunch = { onLaunchChannel(entity) },
                onEdit = { onEditChannel(entity) }
            )
        }
    }
}

@Composable
private fun ChannelRow(
    entity: ChannelEntity,
    mediaRepository: MediaRepository?,
    availableSeries: List<org.jellycopter.core.model.SeriesDetail> = emptyList(),
    onLaunch: () -> Unit,
    onEdit: () -> Unit
) {
    val item = entity.toMixChannelItem()
    val showNames = remember(entity.showsJson, mediaRepository, availableSeries) {
        StationPreviewGenerator.resolveShowTitles(entity.showsJson, mediaRepository, availableSeries)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusCard(
            onClick = onLaunch,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp)
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isFocused) JellycopterTheme.colors.surfaceVariant
                        else JellycopterTheme.colors.surface.copy(alpha = 0.85f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isFocused) Color(0x35FFFFFF) else Color(0x12FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Station Artwork Preview + Channel info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        StationPreviewArt(
                            showsJson = entity.showsJson,
                            mediaRepository = mediaRepository,
                            modifier = Modifier.size(width = 130.dp, height = 74.dp),
                            fallbackTitle = item.title,
                            isFocused = isFocused
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val chNum = entity.channelNumber
                                val chFormatted = if (chNum < 10) "CH 0$chNum" else "CH $chNum"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF03160D))
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFF34D399).copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = chFormatted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF34D399)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (showNames.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(JellycopterTheme.colors.primaryAccent.copy(alpha = 0.15f))
                                            .border(
                                                width = 1.dp,
                                                color = JellycopterTheme.colors.primaryAccent.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${showNames.size} SHOW${if (showNames.size == 1) "" else "S"}",
                                            fontSize = 10.sp,
                                            color = JellycopterTheme.colors.primaryAccent,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showNames.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = (showNames.take(4) as Iterable<String>).joinToString(separator = "  ·  "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Play / Tune In button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isFocused) Color.White
                                else JellycopterTheme.colors.primaryAccent
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isFocused) Icons.Rounded.PlayArrow else Icons.Rounded.Shuffle,
                                contentDescription = if (isFocused) "Tune In" else "Shuffle Play",
                                modifier = Modifier.size(15.dp),
                                tint = if (isFocused) Color.Black else Color.White
                            )
                            Text(
                                text = if (isFocused) "Tune In" else "Shuffle Play",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) Color.Black else Color.White,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }

        // Edit button (sibling FocusCard)
        FocusCard(
            onClick = onEdit,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
        ) { editFocused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (editFocused) JellycopterTheme.colors.primaryAccent
                        else JellycopterTheme.colors.surface.copy(alpha = 0.85f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (editFocused) Color(0x35FFFFFF) else Color(0x12FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit channel",
                    modifier = Modifier.size(20.dp),
                    tint = if (editFocused) Color.Black else Color(0xFF94A3B8)
                )
            }
        }
    }
}
