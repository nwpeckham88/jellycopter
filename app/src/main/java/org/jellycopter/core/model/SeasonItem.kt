package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonItem(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val name: String,
    val episodes: List<EpisodeItem> = emptyList()
)
