package org.jellycopter.discovery

import org.jellycopter.core.model.MediaRequestPayload
import org.jellycopter.core.model.SeasonStatus
import org.jellycopter.discovery.model.MediaRequestDto
import org.jellycopter.discovery.model.SeasonDto
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class MissingSeasonSummary(
    val totalAiredSeasons: Int,
    val localSeasonCount: Int,
    val missingSeasons: List<Int>,
    val unrequestedMissingSeasons: List<Int>,
    val badgeText: String?,
    val requestActionText: String?
) {
    val hasMissingSeasons: Boolean get() = missingSeasons.isNotEmpty()
    val canTriggerRequest: Boolean get() = unrequestedMissingSeasons.isNotEmpty()
}

class MissingSeasonDetector(
    private val dateProvider: () -> LocalDate = { LocalDate.now() }
) {

    /**
     * Diffs local library seasons against remote TMDB/Jellyseerr seasons.
     *
     * Rules:
     * 1. Specials (seasonNumber == 0) are strictly excluded.
     * 2. Unreleased seasons (episodeCount <= 0, empty airDate, or future airDate) are strictly excluded.
     * 3. Local presence is checked against localSeasonNumbers or episode counts.
     * 4. Request status is checked against active/pending requests in mediaInfo.
     */
    fun detectSeasonStatuses(
        localSeasonNumbers: Set<Int>,
        remoteSeasons: List<SeasonDto>,
        requests: List<MediaRequestDto> = emptyList(),
        localEpisodeCounts: Map<Int, Int> = emptyMap(),
        referenceDate: LocalDate = dateProvider()
    ): List<SeasonStatus> {
        val airedRegularSeasons = remoteSeasons.filter { season ->
            isReleasedRegularSeason(season, referenceDate)
        }

        return airedRegularSeasons.map { season ->
            val seasonNum = season.seasonNumber
            val isLocallyPresent = localSeasonNumbers.contains(seasonNum)
            val localEps = localEpisodeCounts[seasonNum] ?: if (isLocallyPresent) season.episodeCount else 0

            val matchingRequest = requests.firstOrNull { req ->
                // Status 1 = PENDING APPROVAL, 2 = APPROVED
                req.status in listOf(1, 2) && (req.seasons == null || req.seasons.contains(seasonNum))
            }

            SeasonStatus(
                seasonNumber = seasonNum,
                isAvailableLocally = isLocallyPresent,
                isRequested = matchingRequest != null,
                requestId = matchingRequest?.id,
                requestStatus = matchingRequest?.status,
                totalEpisodes = season.episodeCount,
                localEpisodes = localEps
            )
        }
    }

    fun summarize(statuses: List<SeasonStatus>): MissingSeasonSummary {
        val totalAired = statuses.size
        val locallyPresent = statuses.filter { it.isAvailableLocally }
        val missingStatuses = statuses.filter { !it.isAvailableLocally }
        val missingSeasonNumbers = missingStatuses.map { it.seasonNumber }.sorted()
        val unrequestedMissing = missingStatuses.filter { !it.isRequested }.map { it.seasonNumber }.sorted()

        val badgeText = when {
            missingSeasonNumbers.isEmpty() -> null
            missingSeasonNumbers.size == 1 -> "⚠️ 1 Missing Season"
            else -> "⚠️ ${missingSeasonNumbers.size} Missing Seasons"
        }

        val actionText = if (unrequestedMissing.isNotEmpty()) {
            "Request Missing Seasons (${unrequestedMissing.joinToString(", ") { "S$it" }})"
        } else {
            null
        }

        return MissingSeasonSummary(
            totalAiredSeasons = totalAired,
            localSeasonCount = locallyPresent.size,
            missingSeasons = missingSeasonNumbers,
            unrequestedMissingSeasons = unrequestedMissing,
            badgeText = badgeText,
            requestActionText = actionText
        )
    }

    fun createRequestPayloadForMissing(
        tmdbId: Int,
        statuses: List<SeasonStatus>,
        is4k: Boolean = false
    ): MediaRequestPayload? {
        val missingUnrequested = statuses
            .filter { !it.isAvailableLocally && !it.isRequested }
            .map { it.seasonNumber }
            .sorted()

        if (missingUnrequested.isEmpty()) return null

        return MediaRequestPayload(
            mediaType = "tv",
            mediaId = tmdbId,
            is4k = is4k,
            seasons = missingUnrequested
        )
    }

    private fun isReleasedRegularSeason(season: SeasonDto, referenceDate: LocalDate): Boolean {
        // Rule 1: S0 Specials ignored
        if (season.seasonNumber <= 0) return false

        // Rule 2: 0 episodes means unreleased / empty placeholder
        if (season.episodeCount <= 0) return false

        // Rule 3: Missing air date means unreleased
        val airDateStr = season.airDate?.trim() ?: return false
        if (airDateStr.isEmpty()) return false

        return try {
            val airDate = LocalDate.parse(airDateStr)
            // Released on or before reference date
            !airDate.isAfter(referenceDate)
        } catch (_: DateTimeParseException) {
            // If date unparseable but episode count > 0, assume released
            true
        }
    }
}
