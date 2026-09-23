package org.jellycopter.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val channelId: String,
    val name: String,
    val description: String? = null,
    val showsJson: String = "[]",
    /** Interleave strategy: ROUND_ROBIN | RANDOM | WEIGHTED */
    val interleaveStrategy: String = "ROUND_ROBIN",
    /** Audio night mode: ON | OFF | USE_GLOBAL */
    val audioNightMode: String = "USE_GLOBAL",
    /** Skip behavior: RECYCLED | COUNTED */
    val skipBehavior: String = "RECYCLED",
    /** Resume on return: NEXT_EPISODE | BEGINNING | EXACT_POSITION */
    val resumeBehavior: String = "NEXT_EPISODE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

