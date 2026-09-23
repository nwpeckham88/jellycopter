package org.jellycopter.core.model

import kotlinx.serialization.Serializable

@Serializable
data class VirtualFolder(
    val id: String,
    val name: String,
    val collectionType: String? = null
)
