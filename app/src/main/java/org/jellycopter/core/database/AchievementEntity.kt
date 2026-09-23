package org.jellycopter.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val tier: String,
    val points: Int,
    val isUnlocked: Boolean = false,
    val unlockedAtUtc: String? = null,
    val progress: Float = 0.0f,
    val maxProgress: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)
