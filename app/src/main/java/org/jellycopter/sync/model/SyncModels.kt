package org.jellycopter.sync.model

import kotlinx.serialization.Serializable

@Serializable
data class PingResponseDto(
    val version: String = "1.0.0",
    val supportedSyncProtocols: List<Int> = listOf(1),
    val serverTimeUtc: String? = null
)

@Serializable
data class DeckSyncDto(
    val deckId: String,
    val seriesId: String? = null,
    val channelId: String? = null,
    val cycleNumber: Int = 1,
    val exhaustedItemIds: List<String> = emptyList(),
    val unexhaustedPoolIds: List<String> = emptyList(),
    val lastPlayedItemId: String? = null,
    val lastResumePositionTicks: Long = 0L,
    val updatedAtUtc: String? = null
)

@Serializable
data class AchievementSyncDto(
    val id: String,
    val title: String,
    val unlockedAtUtc: String? = null,
    val progress: Float = 0.0f,
    val maxProgress: Float = 1.0f,
    val isUnlocked: Boolean = false
)

sealed interface SyncResult {
    data class Success(val message: String, val syncedCount: Int) : SyncResult
    data class FallbackLocal(val reason: String) : SyncResult
    data class Error(val error: Throwable) : SyncResult
}
