package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaCard(
    val id: String,
    val seriesId: String,
    val seasonId: String,
    val categoryKey: String,
    val items: List<EpisodeItem>,
    val isMultiPart: Boolean = items.size > 1,
    val seasonNumber: Int = items.firstOrNull()?.seasonNumber ?: 0,
    val playCount: Double = items.firstOrNull()?.playCount ?: 0.0,
    val isExhausted: Boolean = false
) {
    val itemIds: List<String> get() = items.map { it.id }
}
