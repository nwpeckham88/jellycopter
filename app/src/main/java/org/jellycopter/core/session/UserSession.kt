package org.jellycopter.core.session

import kotlinx.serialization.Serializable

/**
 * Represents an active authenticated user session with a Jellyfin server.
 */
@Serializable
data class UserSession(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val username: String,
    val serverName: String = "",
    val serverId: String = ""
)
