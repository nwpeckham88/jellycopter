package org.jellycopter.discovery

import kotlinx.coroutines.test.runTest
import org.jellycopter.discovery.model.QualityProfileDto
import org.jellycopter.discovery.model.RequestResult
import org.jellycopter.discovery.model.SeasonDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LongPressRequestConfigTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyseerrClient
    private lateinit var requestHandler: RequestHandler

    @Before
    fun setUp() {
        server = MockWebServer()
        client = JellyseerrClient.create(baseUrl = server.url("/api/v1/"))
        requestHandler = client.requestHandler
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun testCustomSeasonSelectionAnd4kOverridePayload() = runTest {
        val seasons = listOf(
            SeasonDto(id = 1, seasonNumber = 0, name = "Specials", episodeCount = 3),
            SeasonDto(id = 2, seasonNumber = 1, name = "Season 1", episodeCount = 10),
            SeasonDto(id = 3, seasonNumber = 2, name = "Season 2", episodeCount = 10),
            SeasonDto(id = 4, seasonNumber = 3, name = "Season 3", episodeCount = 10),
            SeasonDto(id = 5, seasonNumber = 4, name = "Season 4", episodeCount = 10)
        )

        var config = RequestConfig.createForTv(
            tvId = 98765,
            title = "Westworld",
            availableSeasons = seasons
        )

        // Select specific seasons: 2 and 4
        config = config.toggleSeason(2)
        config = config.toggleSeason(4)
        config = config.withQualityProfile(profileId = 15, is4kOverride = true)
        config = config.withServer(serverId = 10, rootFolderOverride = "/data/tv/4k")

        assertEquals(setOf(2, 4), config.selectedSeasons)
        assertTrue(config.is4k)
        assertEquals(15, config.profileId)
        assertEquals(10, config.serverId)
        assertEquals("/data/tv/4k", config.rootFolder)

        val successJson = """
        {
          "id": 777,
          "status": 1,
          "is4k": true,
          "serverId": 10,
          "profileId": 15,
          "rootFolder": "/data/tv/4k",
          "seasons": [2, 4]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(201).setBody(successJson))

        val result = requestHandler.submitConfiguredRequest(config)
        assertTrue(result is RequestResult.Success)
        assertEquals(777, (result as RequestResult.Success).requestId)
        assertTrue(result.is4k)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded?.method)
        assertEquals("/api/v1/request", recorded?.path)
        val body = recorded?.body ?: ""

        assertTrue(body.contains("\"mediaType\":\"tv\""))
        assertTrue(body.contains("\"mediaId\":98765"))
        assertTrue(body.contains("\"is4k\":true"))
        assertTrue(body.contains("\"seasons\":[2,4]") || body.contains("\"seasons\":[2, 4]"))
        assertTrue(body.contains("\"serverId\":10"))
        assertTrue(body.contains("\"profileId\":15"))
        assertTrue(body.contains("\"rootFolder\":\"/data/tv/4k\""))
    }

    @Test
    fun testSelectAllAndClearAvailableSeasons() {
        val seasons = listOf(
            SeasonDto(id = 1, seasonNumber = 0, name = "Specials", episodeCount = 5),
            SeasonDto(id = 2, seasonNumber = 1, name = "Season 1", episodeCount = 8),
            SeasonDto(id = 3, seasonNumber = 2, name = "Season 2", episodeCount = 8),
            SeasonDto(id = 4, seasonNumber = 3, name = "Season 3 (Unaired)", episodeCount = 0)
        )

        var config = RequestConfig.createForTv(
            tvId = 1234,
            title = "Test Show",
            availableSeasons = seasons
        )

        // Select all valid (ignores Season 0 and unaired Season 3)
        config = config.selectAllAvailableSeasons()
        assertEquals(setOf(1, 2), config.selectedSeasons)

        // Clear
        config = config.clearSeasonSelection()
        assertTrue(config.selectedSeasons.isEmpty())

        // Toggle season 1 back on
        config = config.toggleSeason(1)
        assertEquals(setOf(1), config.selectedSeasons)

        // Toggle season 1 off
        config = config.toggleSeason(1)
        assertTrue(config.selectedSeasons.isEmpty())
    }

    @Test
    fun testCorePayloadMapping() {
        val config = RequestConfig(
            mediaId = 555,
            mediaType = "tv",
            title = "Test",
            is4k = true,
            selectedSeasons = setOf(1, 3),
            serverId = 2,
            profileId = 5,
            rootFolder = "/tv"
        )

        val corePayload = config.toCorePayload()
        assertEquals("tv", corePayload.mediaType)
        assertEquals(555, corePayload.mediaId)
        assertTrue(corePayload.is4k)
        assertEquals(listOf(1, 3), corePayload.seasons)
        assertEquals(2, corePayload.serverId)
        assertEquals(5, corePayload.profileId)
        assertEquals("/tv", corePayload.rootFolder)
    }
}
