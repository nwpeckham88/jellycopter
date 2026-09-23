package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDetail(
    val id: String,
    val name: String,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val communityRating: Float? = null,
    val premiereYear: Int? = null,
    val seasons: List<SeasonItem> = emptyList(),
    val nextUpEpisode: EpisodeItem? = null
)
