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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.model.AchievementTier
import org.jellycopter.telemetry.MilestoneDefinitions
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import org.jellycopter.ui.components.MetadataChip

@Composable
fun TrophyRoomScreen(
    achievements: List<Achievement> = defaultAchievements(),
    onAchievementClicked: (Achievement) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val unlockedList = achievements.filter { it.isUnlocked }
    val lockedList = achievements.filter { !it.isUnlocked }

    val totalPoints = achievements.sumOf { it.points }
    val earnedPoints = unlockedList.sumOf { it.points }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Gamerscore / Trophy Header (Frosted Studio Glass Banner)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                JellycopterTheme.colors.surfaceVariant,
                                JellycopterTheme.colors.surface,
                                Color(0xFF0E0F14)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Color(0x1EFFFFFF)),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FBBF24))
                                .border(1.dp, Color(0x40FBBF24), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HALL OF FAME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = "Points",
                                modifier = Modifier.size(26.dp),
                                tint = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "$earnedPoints / $totalPoints PTS",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${unlockedList.size} of ${achievements.size} Milestones Completed",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Trophy Icon Plaque
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x20FBBF24))
                            .border(
                                BorderStroke(1.dp, Color(0x35FBBF24)),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = "Trophy Plaque",
                            modifier = Modifier.size(30.dp),
                            tint = Color(0xFFFBBF24)
                        )
                    }
                }
            }
        }

        // Unlocked Milestones
        if (unlockedList.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                    Text(
                        text = "COMPLETED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Unlocked Milestones",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            items(unlockedList, key = { it.id }) { ach ->
                AchievementCard(
                    achievement = ach,
                    isUnlocked = true,
                    onClick = { onAchievementClicked(ach) }
                )
            }
        }

        // Locked Silhouettes with Live Progress
        if (lockedList.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                    Text(
                        text = "IN PROGRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = JellycopterTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Locked Milestones",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            items(lockedList, key = { it.id }) { ach ->
                AchievementCard(
                    achievement = ach,
                    isUnlocked = false,
                    onClick = { onAchievementClicked(ach) }
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    val tierColor = when (achievement.tier) {
        AchievementTier.PLATINUM -> Color(0xFF38BDF8) // Platinum Cyan
        AchievementTier.GOLD -> Color(0xFFFBBF24) // Gold Amber
        AchievementTier.SILVER -> Color(0xFFCBD5E1) // Silver Slate
        AchievementTier.BRONZE -> Color(0xFFF97316) // Bronze Orange
    }

    FocusCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp),
        shape = RoundedCornerShape(12.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isUnlocked) {
                        if (isFocused) JellycopterTheme.colors.surfaceVariant
                        else JellycopterTheme.colors.surface.copy(alpha = 0.85f)
                    } else {
                        if (isFocused) JellycopterTheme.colors.surface.copy(alpha = 0.65f)
                        else JellycopterTheme.colors.surface.copy(alpha = 0.35f)
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Trophy Art or Silhouette
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isUnlocked) tierColor.copy(alpha = 0.15f)
                                else Color(0x22000000)
                            )
                            .border(
                                BorderStroke(1.dp, if (isUnlocked) tierColor.copy(alpha = 0.35f) else Color(0x14FFFFFF)),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                            contentDescription = if (isUnlocked) "Unlocked" else "Locked",
                            modifier = Modifier.size(22.dp),
                            tint = if (isUnlocked) tierColor else Color(0x55FFFFFF)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = achievement.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnlocked) Color.White else Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tierColor.copy(alpha = 0.12f))
                                    .border(
                                        width = 1.dp,
                                        color = tierColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${achievement.tier.name} · ${achievement.points} PTS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp,
                                    color = tierColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = achievement.description,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )

                        if (!isUnlocked && achievement.maxProgress > 1.0f) {
                            Spacer(modifier = Modifier.height(5.dp))
                            val pct = (achievement.progress / achievement.maxProgress).coerceIn(0f, 1f)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            ) {
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = JellycopterTheme.colors.primaryAccent,
                                    trackColor = Color(0x22FFFFFF)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.0f / %.0f", achievement.progress, achievement.maxProgress),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                if (isUnlocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x1810B981))
                            .border(
                                BorderStroke(1.dp, Color(0x3510B981)),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "COMPLETED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF10B981)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun defaultAchievements(): List<Achievement> {
    return MilestoneDefinitions.ALL_MILESTONES.mapIndexed { index, ach ->
        if (index < 2) {
            ach.copy(isUnlocked = true, unlockedAtUtc = "2026-09-16T04:00:00Z", progress = ach.maxProgress)
        } else if (index == 2) {
            ach.copy(progress = 48.0f)
        } else {
            ach
        }
    }
}
