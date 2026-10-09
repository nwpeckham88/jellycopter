package org.jellycopter.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellycopter.core.model.Achievement
import org.jellycopter.telemetry.PlaybackSessionStats
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme

@Composable
fun PostPlaybackScreen(
    session: PlaybackSessionStats,
    unlockedMilestones: List<Achievement> = emptyList(),
    nextEpisodeTitle: String = "S01E08 • Defiant Jazz",
    deckStandingText: String = "Deck Progress: 43 / 250 played (17% exhausted)",
    countdownSeconds: Int = 15,
    onPlayNext: () -> Unit = {},
    onReplay: () -> Unit = {},
    onBackToGuide: () -> Unit = {},
    onViewTrophies: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var timeLeft by remember { mutableIntStateOf(countdownSeconds) }
    val playNextFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            playNextFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
        onPlayNext()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
            .padding(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            // Milestone Celebration Banner (Sacred Playback Rule: displayed here post-playback)
            AnimatedVisibility(
                visible = unlockedMilestones.isNotEmpty(),
                enter = fadeIn() + slideInVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF231D12),
                                    Color(0xFF181611)
                                )
                            )
                        )
                        .border(
                            androidx.compose.foundation.BorderStroke(1.dp, Color(0x50F59E0B)),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = "Milestone Unlocked",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "MILESTONE UNLOCKED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    unlockedMilestones.forEach { ach ->
                        Text(
                            text = "${ach.title} • +${ach.points} PTS (${ach.tier.name})",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = ach.description,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Up Next Countdown Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JellycopterTheme.colors.surface)
                    .border(
                        androidx.compose.foundation.BorderStroke(1.dp, Color(0x18FFFFFF)),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(26.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "UP NEXT IN $timeLeft SECONDS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JellycopterTheme.colors.primaryAccent,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = nextEpisodeTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deckStandingText,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // D-Pad Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PostPlaybackActionButton(
                            text = "Play Next ($timeLeft)",
                            icon = Icons.Rounded.PlayArrow,
                            isPrimary = true,
                            modifier = Modifier.focusRequester(playNextFocusRequester),
                            onClick = onPlayNext
                        )

                        PostPlaybackActionButton(
                            text = "Replay",
                            icon = Icons.Rounded.Replay,
                            isPrimary = false,
                            onClick = onReplay
                        )

                        PostPlaybackActionButton(
                            text = "TV Guide",
                            icon = Icons.Rounded.Tv,
                            isPrimary = false,
                            onClick = onBackToGuide
                        )

                        if (unlockedMilestones.isNotEmpty()) {
                            PostPlaybackActionButton(
                                text = "Trophy Room",
                                icon = Icons.Rounded.EmojiEvents,
                                isPrimary = false,
                                onClick = onViewTrophies
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostPlaybackActionButton(
    text: String,
    icon: ImageVector? = null,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        val bg = when {
            isFocused -> Color.White
            isPrimary -> JellycopterTheme.colors.primaryAccent
            else -> Color.White.copy(alpha = 0.08f)
        }
        val textColor = if (isFocused) Color.Black else Color.White

        Box(
            modifier = Modifier
                .background(bg)
                .border(
                    width = 1.dp,
                    color = when {
                        isFocused -> Color.White
                        isPrimary -> JellycopterTheme.colors.primaryAccent
                        else -> Color(0x18FFFFFF)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = textColor
                    )
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}
