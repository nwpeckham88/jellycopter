package org.jellycopter.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAchievements(achievements: List<AchievementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAchievement(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getAchievementById(id: String): AchievementEntity?

    @Query("SELECT * FROM achievements ORDER BY id ASC")
    fun getAllAchievementsFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements ORDER BY id ASC")
    suspend fun getAllAchievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE isUnlocked = 1 ORDER BY unlockedAtUtc DESC")
    fun getUnlockedAchievementsFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE isUnlocked = 1 ORDER BY unlockedAtUtc DESC")
    suspend fun getUnlockedAchievements(): List<AchievementEntity>

    @Query("UPDATE achievements SET progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE achievements SET isUnlocked = 1, unlockedAtUtc = :unlockedAtUtc, progress = maxProgress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markUnlocked(id: String, unlockedAtUtc: String, updatedAt: Long = System.currentTimeMillis())
}
