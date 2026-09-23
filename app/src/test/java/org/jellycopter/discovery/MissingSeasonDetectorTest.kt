package org.jellycopter.discovery

import kotlinx.coroutines.test.runTest
import org.jellycopter.discovery.model.MediaInfoDto
import org.jellycopter.discovery.model.MediaRequestDto
import org.jellycopter.discovery.model.SeasonDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class MissingSeasonDetectorTest {

    private lateinit var detector: MissingSeasonDetector
    private val fixedToday = LocalDate.of(2026, 9, 16)

    @Before
    fun setUp() {
        detector = MissingSeasonDetector(dateProvider = { fixedToday })
    }

    @Test
    fun testDiffDetectsMissingSeasonsCorrectly() {
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 0, name = "Specials", episodeCount = 5, airDate = "2015-01-01"),
            SeasonDto(id = 2, seasonNumber = 1, name = "Season 1", episodeCount = 10, airDate = "2015-02-08"),
            SeasonDto(id = 3, seasonNumber = 2, name = "Season 2", episodeCount = 10, airDate = "2016-02-15"),
            SeasonDto(id = 4, seasonNumber = 3, name = "Season 3", episodeCount = 10, airDate = "2017-04-10"),
            SeasonDto(id = 5, seasonNumber = 4, name = "Season 4", episodeCount = 10, airDate = "2018-08-06"),
            SeasonDto(id = 6, seasonNumber = 5, name = "Season 5", episodeCount = 10, airDate = "2020-02-23"),
            SeasonDto(id = 7, seasonNumber = 6, name = "Season 6 (Unaired)", episodeCount = 0, airDate = null)
        )

        val localSeasons = setOf(1, 2)
        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = localSeasons,
            remoteSeasons = remoteSeasons,
            referenceDate = fixedToday
        )

        // Seasons 0 (specials) and 6 (unaired) should be excluded: exactly 5 seasons returned
        assertEquals(5, statuses.size)
        assertEquals(listOf(1, 2, 3, 4, 5), statuses.map { it.seasonNumber })

        // Check local availability
        val s1 = statuses.first { it.seasonNumber == 1 }
        assertTrue(s1.isAvailableLocally)
        val s2 = statuses.first { it.seasonNumber == 2 }
        assertTrue(s2.isAvailableLocally)

        val s3 = statuses.first { it.seasonNumber == 3 }
        assertFalse(s3.isAvailableLocally)
        val s4 = statuses.first { it.seasonNumber == 4 }
        assertFalse(s4.isAvailableLocally)
        val s5 = statuses.first { it.seasonNumber == 5 }
        assertFalse(s5.isAvailableLocally)

        // Summary test
        val summary = detector.summarize(statuses)
        assertEquals(5, summary.totalAiredSeasons)
        assertEquals(2, summary.localSeasonCount)
        assertEquals(listOf(3, 4, 5), summary.missingSeasons)
        assertEquals("⚠️ 3 Missing Seasons", summary.badgeText)
        assertEquals("Request Missing Seasons (S3, S4, S5)", summary.requestActionText)

        // Direct request payload test
        val payload = detector.createRequestPayloadForMissing(
            tmdbId = 60059,
            statuses = statuses,
            is4k = false
        )
        assertNotNull(payload)
        assertEquals("tv", payload?.mediaType)
        assertEquals(60059, payload?.mediaId)
        assertEquals(listOf(3, 4, 5), payload?.seasons)
        assertFalse(payload?.is4k ?: true)
    }

    @Test
    fun testSpecialsSeasonZeroStrictlyIgnored() {
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 0, name = "Specials", episodeCount = 12, airDate = "2020-01-01"),
            SeasonDto(id = 2, seasonNumber = 1, name = "Season 1", episodeCount = 8, airDate = "2021-01-01")
        )

        val localSeasons = setOf(1) // Only season 1 locally, season 0 missing
        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = localSeasons,
            remoteSeasons = remoteSeasons,
            referenceDate = fixedToday
        )

        assertEquals(1, statuses.size)
        assertEquals(1, statuses[0].seasonNumber)
        assertTrue(statuses[0].isAvailableLocally)

        val summary = detector.summarize(statuses)
        assertFalse(summary.hasMissingSeasons)
        assertNull(summary.badgeText)
        assertNull(summary.requestActionText)

        val payload = detector.createRequestPayloadForMissing(100, statuses)
        assertNull(payload)
    }

    @Test
    fun testFutureUnreleasedSeasonsStrictlyIgnored() {
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 1, name = "Season 1", episodeCount = 10, airDate = "2024-01-01"),
            SeasonDto(id = 2, seasonNumber = 2, name = "Season 2", episodeCount = 10, airDate = "2025-01-01"),
            SeasonDto(id = 3, seasonNumber = 3, name = "Season 3 (Future)", episodeCount = 10, airDate = "2027-05-20")
        )

        val localSeasons = setOf(1, 2)
        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = localSeasons,
            remoteSeasons = remoteSeasons,
            referenceDate = fixedToday // 2026-09-16
        )

        // Season 3 airs in 2027, so it must be excluded
        assertEquals(2, statuses.size)
        assertEquals(listOf(1, 2), statuses.map { it.seasonNumber })
        assertTrue(statuses.all { it.isAvailableLocally })

        val summary = detector.summarize(statuses)
        assertFalse(summary.hasMissingSeasons)
    }

    @Test
    fun testExistingPendingRequestsMarkedOnBadges() {
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 1, name = "Season 1", episodeCount = 10, airDate = "2020-01-01"),
            SeasonDto(id = 2, seasonNumber = 2, name = "Season 2", episodeCount = 10, airDate = "2021-01-01"),
            SeasonDto(id = 3, seasonNumber = 3, name = "Season 3", episodeCount = 10, airDate = "2022-01-01")
        )

        val localSeasons = setOf(1)
        val requests = listOf(
            MediaRequestDto(
                id = 42,
                status = 2, // Approved / Pending download
                seasons = listOf(2) // Season 2 is requested
            )
        )

        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = localSeasons,
            remoteSeasons = remoteSeasons,
            requests = requests,
            referenceDate = fixedToday
        )

        val s2 = statuses.first { it.seasonNumber == 2 }
        assertFalse(s2.isAvailableLocally)
        assertTrue(s2.isRequested)
        assertEquals(42, s2.requestId)
        assertEquals(2, s2.requestStatus)

        val s3 = statuses.first { it.seasonNumber == 3 }
        assertFalse(s3.isAvailableLocally)
        assertFalse(s3.isRequested)

        val summary = detector.summarize(statuses)
        assertEquals(listOf(2, 3), summary.missingSeasons)
        assertEquals(listOf(3), summary.unrequestedMissingSeasons)
        assertEquals("Request Missing Seasons (S3)", summary.requestActionText)

        val payload = detector.createRequestPayloadForMissing(500, statuses)
        assertEquals(listOf(3), payload?.seasons)
    }

    @Test
    fun testJellyseerrClientCheckMissingSeasonsIntegration() = runTest {
        val server = MockWebServer()
        try {
            val tvDetailsJson = """
            {
              "id": 1399,
              "name": "Game of Thrones",
              "seasons": [
                {
                  "seasonNumber": 0,
                  "name": "Specials",
                  "episodeCount": 10,
                  "airDate": "2011-01-01"
                },
                {
                  "seasonNumber": 1,
                  "name": "Season 1",
                  "episodeCount": 10,
                  "airDate": "2011-04-17"
                },
                {
                  "seasonNumber": 2,
                  "name": "Season 2",
                  "episodeCount": 10,
                  "airDate": "2012-04-01"
                }
              ],
              "mediaInfo": {
                "id": 100,
                "status": 4,
                "requests": []
              }
            }
            """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(tvDetailsJson))

            val client = JellyseerrClient.create(
                baseUrl = server.url("/api/v1/"),
                localSeasonProvider = { seriesId ->
                    if (seriesId == "jellyfin_got_series") setOf(1) else emptySet()
                }
            )

            val statuses = client.checkMissingSeasons(
                seriesId = "jellyfin_got_series",
                tmdbId = 1399,
                referenceDate = fixedToday
            )

            assertEquals(2, statuses.size)
            val s1 = statuses.first { it.seasonNumber == 1 }
            assertTrue(s1.isAvailableLocally)
            val s2 = statuses.first { it.seasonNumber == 2 }
            assertFalse(s2.isAvailableLocally)

            val req = server.takeRequest()
            assertEquals("/api/v1/tv/1399?language=en", req?.path)
        } finally {
            server.close()
        }
    }
}
