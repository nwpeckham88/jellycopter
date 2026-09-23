package org.jellycopter.discovery

import org.jellycopter.core.model.MediaRequestPayload
import org.jellycopter.discovery.model.JellyseerrRequestPayload
import org.jellycopter.discovery.model.QualityProfileDto
import org.jellycopter.discovery.model.SeasonDto
import org.jellycopter.discovery.model.SeasonsField

data class ServerOption(
    val id: Int,
    val name: String,
    val isDefault: Boolean = false,
    val is4k: Boolean = false,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null
)

data class RequestConfig(
    val mediaId: Int,
    val mediaType: String,
    val title: String = "",
    val is4k: Boolean = false,
    val selectedSeasons: Set<Int> = emptySet(),
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val availableSeasons: List<SeasonDto> = emptyList(),
    val availableProfiles: List<QualityProfileDto> = emptyList(),
    val availableServers: List<ServerOption> = emptyList()
) {

    fun toggleSeason(seasonNumber: Int): RequestConfig {
        val updated = if (selectedSeasons.contains(seasonNumber)) {
            selectedSeasons - seasonNumber
        } else {
            selectedSeasons + seasonNumber
        }
        return copy(selectedSeasons = updated)
    }

    fun selectSeasons(seasons: Collection<Int>): RequestConfig {
        return copy(selectedSeasons = seasons.toSet())
    }

    fun selectAllAvailableSeasons(): RequestConfig {
        val allValid = availableSeasons
            .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            .map { it.seasonNumber }
            .toSet()
        return copy(selectedSeasons = allValid)
    }

    fun clearSeasonSelection(): RequestConfig {
        return copy(selectedSeasons = emptySet())
    }

    fun withQualityProfile(profileId: Int?, is4kOverride: Boolean? = null): RequestConfig {
        return copy(
            profileId = profileId,
            is4k = is4kOverride ?: is4k
        )
    }

    fun withServer(serverId: Int?, rootFolderOverride: String? = null): RequestConfig {
        return copy(
            serverId = serverId,
            rootFolder = rootFolderOverride ?: rootFolder
        )
    }

    fun with4k(enabled: Boolean): RequestConfig {
        return copy(is4k = enabled)
    }

    fun toCorePayload(): MediaRequestPayload {
        val seasonsList = if (mediaType == "tv" && selectedSeasons.isNotEmpty()) {
            selectedSeasons.sorted()
        } else {
            null
        }
        return MediaRequestPayload(
            mediaType = mediaType,
            mediaId = mediaId,
            is4k = is4k,
            seasons = seasonsList,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder
        )
    }

    fun toJellyseerrPayload(): JellyseerrRequestPayload {
        val seasonsField = if (mediaType == "tv") {
            if (selectedSeasons.isEmpty()) {
                SeasonsField.All
            } else {
                SeasonsField.Custom(selectedSeasons.sorted())
            }
        } else {
            null
        }
        return JellyseerrRequestPayload(
            mediaType = mediaType,
            mediaId = mediaId,
            is4k = is4k,
            seasons = seasonsField,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder
        )
    }

    companion object {
        fun createForMovie(
            movieId: Int,
            title: String = "",
            defaultServerId: Int? = null,
            defaultProfileId: Int? = null
        ): RequestConfig {
            return RequestConfig(
                mediaId = movieId,
                mediaType = "movie",
                title = title,
                serverId = defaultServerId,
                profileId = defaultProfileId
            )
        }

        fun createForTv(
            tvId: Int,
            title: String = "",
            availableSeasons: List<SeasonDto> = emptyList(),
            initialSelectedSeasons: Set<Int> = emptySet(),
            defaultServerId: Int? = null,
            defaultProfileId: Int? = null
        ): RequestConfig {
            return RequestConfig(
                mediaId = tvId,
                mediaType = "tv",
                title = title,
                availableSeasons = availableSeasons,
                selectedSeasons = initialSelectedSeasons,
                serverId = defaultServerId,
                profileId = defaultProfileId
            )
        }
    }
}
