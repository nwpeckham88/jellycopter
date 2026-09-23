package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonStatus(
    val seasonNumber: Int,
    val isAvailableLocally: Boolean = false,
    val isRequested: Boolean = false,
    val requestId: Int? = null,
    val requestStatus: Int? = null,
    val totalEpisodes: Int = 0,
    val localEpisodes: Int = 0
)
