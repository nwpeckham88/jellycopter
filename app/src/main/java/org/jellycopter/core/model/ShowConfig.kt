package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ShowConfig(
    val seriesId: String,
    val seriesName: String,
    val weight: Int = 1,
    val seasons: List<Int>? = null
)
