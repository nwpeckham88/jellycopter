package org.jellycopter.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jellycopter.core.database.AchievementDao
import org.jellycopter.core.database.AchievementEntity
import org.jellycopter.core.model.Achievement
import org.jellycopter.core.model.AchievementTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface AchievementRepository {
    fun getAllAchievements(): Flow<List<Achievement>>
    fun getUnlockedAchievements(): Flow<List<Achievement>>
    suspend fun getAchievement(id: String): Achievement?
    suspend fun unlockAchievement(id: String, timestampUtc: String? = null): Boolean
    suspend fun updateProgress(id: String, progress: Float)
    suspend fun syncAchievements(incoming: List<Achievement>)
    suspend fun seedDefaults(achievements: List<Achievement>)
}

class RoomAchievementRepository(
    private val achievementDao: AchievementDao
) : AchievementRepository {

    override fun getAllAchievements(): Flow<List<Achievement>> {
        return achievementDao.getAllAchievementsFlow().map { list ->
            list.map { toAchievement(it) }
        }
    }

    override fun getUnlockedAchievements(): Flow<List<Achievement>> {
        return achievementDao.getUnlockedAchievementsFlow().map { list ->
            list.map { toAchievement(it) }
        }
    }

    override suspend fun getAchievement(id: String): Achievement? {
        val entity = achievementDao.getAchievementById(id) ?: return null
        return toAchievement(entity)
    }

    override suspend fun unlockAchievement(id: String, timestampUtc: String?): Boolean {
        val existing = achievementDao.getAchievementById(id) ?: return false
        if (existing.isUnlocked) return false

        val ts = timestampUtc ?: currentIsoTimestamp()
        achievementDao.markUnlocked(id, ts)
        return true
    }

    override suspend fun updateProgress(id: String, progress: Float) {
        val existing = achievementDao.getAchievementById(id) ?: return
        if (existing.isUnlocked) return

        val clamped = progress.coerceIn(0.0f, existing.maxProgress)
        achievementDao.updateProgress(id, clamped)

        if (clamped >= existing.maxProgress) {
            achievementDao.markUnlocked(id, currentIsoTimestamp())
        }
    }

    override suspend fun syncAchievements(incoming: List<Achievement>) {
        for (item in incoming) {
            val existing = achievementDao.getAchievementById(item.id)
            if (existing == null) {
                achievementDao.upsertAchievement(toEntity(item))
            } else {
                // Conflict resolution: earliest unlock timestamp wins; max progress wins
                val isUnlocked = existing.isUnlocked || item.isUnlocked
                val unlockTime = when {
                    existing.unlockedAtUtc != null && item.unlockedAtUtc != null -> {
                        if (existing.unlockedAtUtc <= item.unlockedAtUtc) existing.unlockedAtUtc else item.unlockedAtUtc
                    }
                    existing.unlockedAtUtc != null -> existing.unlockedAtUtc
                    item.unlockedAtUtc != null -> item.unlockedAtUtc
                    else -> null
                }
                val maxProg = maxOf(existing.progress, item.progress)

                val merged = existing.copy(
                    isUnlocked = isUnlocked,
                    unlockedAtUtc = unlockTime,
                    progress = maxProg,
                    updatedAt = System.currentTimeMillis()
                )
                achievementDao.upsertAchievement(merged)
            }
        }
    }

    override suspend fun seedDefaults(achievements: List<Achievement>) {
        for (item in achievements) {
            val existing = achievementDao.getAchievementById(item.id)
            if (existing == null) {
                achievementDao.upsertAchievement(toEntity(item))
            }
        }
    }

    private fun toAchievement(entity: AchievementEntity): Achievement {
        val tier = runCatching {
            AchievementTier.valueOf(entity.tier.uppercase())
        }.getOrDefault(AchievementTier.BRONZE)

        return Achievement(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            tier = tier,
            points = entity.points,
            isUnlocked = entity.isUnlocked,
            unlockedAtUtc = entity.unlockedAtUtc,
            progress = entity.progress,
            maxProgress = entity.maxProgress
        )
    }

    private fun toEntity(model: Achievement): AchievementEntity {
        return AchievementEntity(
            id = model.id,
            title = model.title,
            description = model.description,
            tier = model.tier.name,
            points = model.points,
            isUnlocked = model.isUnlocked,
            unlockedAtUtc = model.unlockedAtUtc,
            progress = model.progress,
            maxProgress = model.maxProgress,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun currentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
