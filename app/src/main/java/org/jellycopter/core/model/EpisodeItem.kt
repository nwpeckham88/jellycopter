package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeItem(
    val id: String,
    val name: String,
    val seriesId: String,
    val seriesName: String = "",
    val seasonId: String,
    val seasonNumber: Int,
    val indexNumber: Int,
    val runTimeTicks: Long = 0L,
    val overview: String? = null,
    val playCount: Double = 0.0,
    val isMissing: Boolean = false,
    val locationType: String = "FileSystem",
    val mediaType: String = "Video"
)
