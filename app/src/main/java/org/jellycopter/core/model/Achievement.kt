package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AchievementTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM
}

@Serializable
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val tier: AchievementTier = AchievementTier.BRONZE,
    val points: Int = 10,
    val isUnlocked: Boolean = false,
    val unlockedAtUtc: String? = null,
    val progress: Float = 0.0f,
    val maxProgress: Float = 1.0f
)
