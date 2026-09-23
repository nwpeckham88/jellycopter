package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaRequestPayload(
    val mediaType: String,
    val mediaId: Int,
    val is4k: Boolean = false,
    val seasons: List<Int>? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null
)
