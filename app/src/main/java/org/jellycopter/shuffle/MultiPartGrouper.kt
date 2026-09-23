package org.jellycopter.shuffle

import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.MediaCard

object MultiPartGrouper {

    private val GENERIC_BLACKLIST = setOf(
        "episode", "ep", "chapter", "part", "pt", "vol", "volume", "special", ""
    )

    private val ROMAN_REGEX = Regex("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")

    private val ROMAN_VALUES = mapOf(
        'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50,
        'C' to 100, 'D' to 500, 'M' to 1000
    )

    private val PART_REGEX = Regex(
        """^(.*?)(?:,\s*|:\s*|\s+-\s*|\s*-\s*|\s*\(\s*|\s*\[\s*|\s+)(?:(part|pt\.?|vol\.?|volume|chapter)\s*)?([0-9]+|[ivxlcdm]+)(?:\s*\)|\s*\])?$""",
        RegexOption.IGNORE_CASE
    )

    data class ParsedPartInfo(
        val originalTitle: String,
        val normalizedBase: String,
        val keyword: String?,
        val partNumber: Int
    )

    /**
     * Filters out non-playable episodes (virtual items, missing files, zero runtime, non-video).
     */
    fun filterPlayableEpisodes(episodes: List<EpisodeItem>): List<EpisodeItem> {
        return episodes.filter { ep ->
            !ep.locationType.equals("Virtual", ignoreCase = true) &&
                    !ep.isMissing &&
                    ep.runTimeTicks > 0L &&
                    (ep.mediaType.isEmpty() || ep.mediaType.equals("Video", ignoreCase = true))
        }
    }

    /**
     * Parse part information from an episode title.
     * Returns null if not a recognized multi-part format or if base title is generic.
     */
    fun parsePartInfo(title: String): ParsedPartInfo? {
        val match = PART_REGEX.matchEntire(title.trim()) ?: return null
        val rawBase = match.groupValues[1]
        val keyword = match.groupValues[2].takeIf { it.isNotEmpty() }
        val numStr = match.groupValues[3]

        val partNum = parseNumber(numStr) ?: return null
        val normalizedBase = normalizeBaseTitle(rawBase)

        if (isGenericTitle(normalizedBase)) {
            return null
        }

        return ParsedPartInfo(
            originalTitle = title,
            normalizedBase = normalizedBase,
            keyword = keyword,
            partNumber = partNum
        )
    }

    fun parseNumber(str: String): Int? {
        val trimmed = str.trim()
        val asInt = trimmed.toIntOrNull()
        if (asInt != null) return asInt

        val upper = trimmed.uppercase()
        if (upper.isEmpty() || !ROMAN_REGEX.matches(upper)) return null

        var total = 0
        var prev = 0
        for (i in upper.indices.reversed()) {
            val curr = ROMAN_VALUES[upper[i]] ?: return null
            if (curr < prev) {
                total -= curr
            } else {
                total += curr
                prev = curr
            }
        }
        return if (total > 0) total else null
    }

    fun normalizeBaseTitle(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("""^[\s\-_,:;()\[\]]+|[\s\-_,:;()\[\]]+$"""), "")
            .trim()
    }

    fun isGenericTitle(normalizedBase: String): Boolean {
        return normalizedBase.isEmpty() || normalizedBase in GENERIC_BLACKLIST
    }

    /**
     * Checks the 4-point invariant between two sequential candidate episodes:
     * 1. Same SeasonId
     * 2. Sequential Episode Index (indexB == indexA + 1)
     * 3. Matching Normalized Base Title
     * 4. Consecutive Part Number (partB == partA + 1)
     */
    fun canBond(
        epA: EpisodeItem,
        parsedA: ParsedPartInfo?,
        epB: EpisodeItem,
        parsedB: ParsedPartInfo?
    ): Boolean {
        // Invariant 1: Same Season
        if (epA.seasonId != epB.seasonId) return false

        // Invariant 2: Sequential Index Number
        if (epB.indexNumber != epA.indexNumber + 1) return false

        // Invariant 3: Matching Base Title (and not null / not generic)
        if (parsedA == null || parsedB == null) return false
        if (parsedA.normalizedBase != parsedB.normalizedBase) return false

        // Invariant 4: Consecutive Part Number
        if (parsedB.partNumber != parsedA.partNumber + 1) return false

        return true
    }

    /**
     * Groups a collection of episodes into atomic MediaCards according to 4-point invariant checks.
     * Episodes are first filtered for hygiene, grouped by season, sorted by indexNumber, and then bonded.
     */
    fun groupEpisodes(rawEpisodes: List<EpisodeItem>): List<MediaCard> {
        val playable = filterPlayableEpisodes(rawEpisodes)
        if (playable.isEmpty()) return emptyList()

        // Group by season to process each season chronologically
        val seasonGroups = playable.groupBy { it.seasonId }
        val allCards = mutableListOf<MediaCard>()

        for ((_, seasonEpisodes) in seasonGroups) {
            val sortedEpisodes = seasonEpisodes.sortedBy { it.indexNumber }
            if (sortedEpisodes.isEmpty()) continue

            var currentGroup = mutableListOf<EpisodeItem>()
            var lastParsed: ParsedPartInfo? = null

            for (ep in sortedEpisodes) {
                val parsed = parsePartInfo(ep.name)

                if (currentGroup.isEmpty()) {
                    currentGroup.add(ep)
                    lastParsed = parsed
                } else {
                    val lastEp = currentGroup.last()
                    if (canBond(lastEp, lastParsed, ep, parsed)) {
                        // All 4 invariants satisfied: bond into current group
                        currentGroup.add(ep)
                        lastParsed = parsed
                    } else {
                        // Close current group and start new one
                        allCards.add(createMediaCard(currentGroup))
                        currentGroup = mutableListOf(ep)
                        lastParsed = parsed
                    }
                }
            }

            if (currentGroup.isNotEmpty()) {
                allCards.add(createMediaCard(currentGroup))
            }
        }

        return allCards
    }

    private fun createMediaCard(items: List<EpisodeItem>): MediaCard {
        require(items.isNotEmpty()) { "Cannot create MediaCard with empty items" }
        val first = items.first()
        val playCount = items.map { it.playCount }.average()

        return MediaCard(
            id = first.id,
            seriesId = first.seriesId,
            seasonId = first.seasonId,
            categoryKey = first.seasonId,
            items = items,
            isMultiPart = items.size > 1,
            seasonNumber = first.seasonNumber,
            playCount = playCount,
            isExhausted = false
        )
    }
}
