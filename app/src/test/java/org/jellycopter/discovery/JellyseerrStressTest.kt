package org.jellycopter.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jellycopter.discovery.model.RequestResult
import org.jellycopter.discovery.model.RequestUiState
import org.jellycopter.discovery.model.SeasonDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class JellyseerrStressTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyseerrClient
    private lateinit var requestHandler: RequestHandler
    private val refDate = LocalDate.of(2026, 9, 16)

    @Before
    fun setUp() {
        server = MockWebServer()
        client = JellyseerrClient.create(
            baseUrl = server.url("/api/v1/"),
            connectTimeoutSeconds = 2,
            readTimeoutSeconds = 2
        )
        requestHandler = client.requestHandler
    }

    @After
    fun tearDown() {
        server.close()
    }

    // -------------------------------------------------------------
    // 1. Network & HTTP Error Stress Tests
    // -------------------------------------------------------------

    @Test
    fun testHttp409WithHtmlBodyDoesNotCrashJsonParser() = runTest {
        // Enqueue empty servers list
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        // Enqueue 409 Conflict with HTML Cloudflare error body instead of JSON
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .addHeader("Content-Type", "text/html")
                .setBody("<html><body>409 Conflict: Already Exists</body></html>")
        )

        val result = client.requestOneClick(
            mediaId = 12345,
            mediaType = "movie",
            title = "Dune"
        )

        assertTrue(result is RequestResult.AlreadyRequested)
        val conflict = result as RequestResult.AlreadyRequested
        assertEquals(12345, conflict.mediaId)
        // Defaults safely to fallback string
        assertEquals("Request already exists", conflict.message)

        val uiState = requestHandler.uiState.value
        assertTrue(uiState is RequestUiState.AlreadyRequested)
    }

    @Test
    fun testHttp502BadGatewayHandledGracefully() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("<html>502 Bad Gateway</html>")
        )

        val result = client.requestOneClick(
            mediaId = 50201,
            mediaType = "tv",
            title = "Gateway Show"
        )

        assertTrue(result is RequestResult.Error)
        val err = result as RequestResult.Error
        assertEquals(502, err.code)
        assertTrue(err.message.contains("502"))

        val uiState = requestHandler.uiState.value
        assertTrue(uiState is RequestUiState.Error)
    }

    @Test
    fun testHttp500EmptyBodyHandledGracefully() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("")
        )

        val result = client.requestOneClick(
            mediaId = 50001,
            mediaType = "movie",
            title = "Empty Error Movie"
        )

        assertTrue(result is RequestResult.Error)
        val err = result as RequestResult.Error
        assertEquals(500, err.code)
    }

    @Test
    fun testConcurrentOneClickRequests() = runTest {
        // Enqueue servers once (cached)
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        // First request succeeds
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"id": 1, "status": 2, "is4k": false}""")
        )
        // 4 concurrent requests hit 409
        for (i in 0 until 4) {
            server.enqueue(
                MockResponse().setResponseCode(409).setBody("""{"message": "Request already exists"}""")
            )
        }

        val deferreds = (1..5).map { idx ->
            async {
                client.requestOneClick(
                    mediaId = 77700,
                    mediaType = "movie",
                    title = "Concurrent Movie"
                )
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(5, results.size)
        val successes = results.filterIsInstance<RequestResult.Success>()
        val conflicts = results.filterIsInstance<RequestResult.AlreadyRequested>()

        assertEquals(1, successes.size)
        assertEquals(4, conflicts.size)
    }

    // -------------------------------------------------------------
    // 2. Adversarial Season Diffing Stress Tests
    // -------------------------------------------------------------

    @Test
    fun testAdversarialDiffingSpecialsSeasonZeroStrictlyExcluded() {
        val detector = MissingSeasonDetector(dateProvider = { refDate })
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 0, name = "Specials", episodeCount = 20, airDate = "2010-01-01"),
            SeasonDto(id = 2, seasonNumber = 1, name = "Season 1", episodeCount = 10, airDate = "2011-01-01"),
            SeasonDto(id = 3, seasonNumber = 2, name = "Season 2", episodeCount = 10, airDate = "2012-01-01")
        )

        // Local library only has Season 1 (missing S0 and S2)
        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = setOf(1),
            remoteSeasons = remoteSeasons,
            referenceDate = refDate
        )

        val numbers = statuses.map { it.seasonNumber }
        assertFalse("Season 0 specials must NEVER appear in detected season statuses", numbers.contains(0))
        assertEquals(listOf(1, 2), numbers)

        val summary = detector.summarize(statuses)
        assertEquals(listOf(2), summary.missingSeasons)
        assertEquals("⚠️ 1 Missing Season", summary.badgeText)
    }

    @Test
    fun testAdversarialDiffingUnairedAndFutureDates() {
        val detector = MissingSeasonDetector(dateProvider = { refDate })
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 1, name = "Season 1", episodeCount = 10, airDate = "2024-01-01"),
            SeasonDto(id = 2, seasonNumber = 2, name = "Season 2", episodeCount = 10, airDate = "2026-09-15"), // Past
            SeasonDto(id = 3, seasonNumber = 3, name = "Season 3", episodeCount = 10, airDate = "2026-09-16"), // Today
            SeasonDto(id = 4, seasonNumber = 4, name = "Season 4", episodeCount = 10, airDate = "2026-09-17"), // Future
            SeasonDto(id = 5, seasonNumber = 5, name = "Season 5", episodeCount = 0, airDate = "2026-01-01"),  // 0 eps
            SeasonDto(id = 6, seasonNumber = 6, name = "Season 6", episodeCount = 10, airDate = null),          // Null date
            SeasonDto(id = 7, seasonNumber = 7, name = "Season 7", episodeCount = 10, airDate = "   ")         // Whitespace date
        )

        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = setOf(1),
            remoteSeasons = remoteSeasons,
            referenceDate = refDate
        )

        val numbers = statuses.map { it.seasonNumber }
        assertEquals(listOf(1, 2, 3), numbers)

        val summary = detector.summarize(statuses)
        assertEquals(listOf(2, 3), summary.missingSeasons)
        assertEquals("⚠️ 2 Missing Seasons", summary.badgeText)
    }

    @Test
    fun testAdversarialDiffingMismatchedNonContiguousNumbering() {
        val detector = MissingSeasonDetector(dateProvider = { refDate })
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 1, name = "Part 1", episodeCount = 12, airDate = "2020-01-01"),
            SeasonDto(id = 2, seasonNumber = 5, name = "Part 5", episodeCount = 12, airDate = "2022-01-01"),
            SeasonDto(id = 3, seasonNumber = 9, name = "Part 9", episodeCount = 12, airDate = "2024-01-01"),
            SeasonDto(id = 4, seasonNumber = 100, name = "Centurion", episodeCount = 24, airDate = "2025-01-01")
        )

        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = setOf(1, 9),
            remoteSeasons = remoteSeasons,
            referenceDate = refDate
        )

        val summary = detector.summarize(statuses)
        assertEquals(listOf(5, 100), summary.missingSeasons)
        assertEquals("⚠️ 2 Missing Seasons", summary.badgeText)
        assertEquals("Request Missing Seasons (S5, S100)", summary.requestActionText)

        val payload = detector.createRequestPayloadForMissing(
            tmdbId = 8888,
            statuses = statuses,
            is4k = true
        )
        assertEquals(listOf(5, 100), payload?.seasons)
        assertTrue(payload?.is4k == true)
    }

    @Test
    fun testUnparseableAirDateAssumedReleasedIfEpisodeCountPositive() {
        val detector = MissingSeasonDetector(dateProvider = { refDate })
        val remoteSeasons = listOf(
            SeasonDto(id = 1, seasonNumber = 1, name = "Season 1", episodeCount = 8, airDate = "TBA 2026")
        )

        val statuses = detector.detectSeasonStatuses(
            localSeasonNumbers = emptySet(),
            remoteSeasons = remoteSeasons,
            referenceDate = refDate
        )

        assertEquals(1, statuses.size)
        assertEquals(1, statuses[0].seasonNumber)
        assertFalse(statuses[0].isAvailableLocally)
    }
}
