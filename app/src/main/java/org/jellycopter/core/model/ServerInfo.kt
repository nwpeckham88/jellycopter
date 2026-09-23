package org.jellycopter.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public system information returned by /System/Info/Public.
 */
@Serializable
data class ServerInfo(
    @SerialName("ServerName") val serverName: String = "",
    @SerialName("Version") val version: String = "",
    @SerialName("Id") val id: String = ""
)
