package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaDiscoverItem(
    val id: Int,
    val tmdbId: Int = id,
    val mediaType: String,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double = 0.0,
    val status: Int = 1,
    val isRequested: Boolean = false
)
