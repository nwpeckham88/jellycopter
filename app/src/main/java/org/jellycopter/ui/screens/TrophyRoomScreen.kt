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
        // Gamerscore / Trophy Header (Frosted Apple TV Glass Banner)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                JellycopterTheme.colors.surfaceVariant,
                                JellycopterTheme.colors.surface,
                                JellycopterTheme.colors.background
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Color(0x28FFFFFF)),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(26.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        MetadataChip(
                            text = "TROPHY ROOM",
                            accentColor = Color(0xFFFBBF24),
                            backgroundColor = Color(0x30FBBF24)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = "Points",
                                modifier = Modifier.size(28.dp),
                                tint = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "$earnedPoints / $totalPoints Points",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${unlockedList.size} of ${achievements.size} Quirky Milestones Unlocked",
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    // Large Trophy Glowing Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x35FBBF24))
                            .border(
                                BorderStroke(1.dp, Color(0x50FBBF24)),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = "Trophy Glow",
                            modifier = Modifier.size(34.dp),
                            tint = Color(0xFFFBBF24)
                        )
                    }
                }
            }
        }

        // Unlocked Milestones
        if (unlockedList.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
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
                        text = "In Progress & Locked Milestones",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
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
            .height(104.dp),
        shape = RoundedCornerShape(16.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isUnlocked) {
                        if (isFocused) JellycopterTheme.colors.surfaceVariant
                        else JellycopterTheme.colors.surface.copy(alpha = 0.85f)
                    } else {
                        if (isFocused) JellycopterTheme.colors.surface.copy(alpha = 0.7f)
                        else JellycopterTheme.colors.surface.copy(alpha = 0.45f)
                    }
                )
                .padding(horizontal = 22.dp, vertical = 16.dp)
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
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isUnlocked) tierColor.copy(alpha = 0.2f)
                                else Color(0x33000000)
                            )
                            .border(
                                BorderStroke(1.dp, if (isUnlocked) tierColor.copy(alpha = 0.4f) else Color(0x18FFFFFF)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                            contentDescription = if (isUnlocked) "Unlocked" else "Locked",
                            modifier = Modifier.size(24.dp),
                            tint = if (isUnlocked) tierColor else Color(0x66FFFFFF)
                        )
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = achievement.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnlocked) Color.White else Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            MetadataChip(
                                text = "${achievement.tier.name} • ${achievement.points} pts",
                                accentColor = tierColor,
                                backgroundColor = tierColor.copy(alpha = 0.16f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = achievement.description,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )

                        if (!isUnlocked && achievement.maxProgress > 1.0f) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val pct = (achievement.progress / achievement.maxProgress).coerceIn(0f, 1f)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = JellycopterTheme.colors.primaryAccent,
                                    trackColor = Color(0x44000000)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.1f / %.1f", achievement.progress, achievement.maxProgress),
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                if (isUnlocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2810B981))
                            .border(
                                BorderStroke(1.dp, Color(0x5010B981)),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "UNLOCKED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Unlocked",
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
