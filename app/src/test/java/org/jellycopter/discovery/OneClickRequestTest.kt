package org.jellycopter.discovery

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.jellycopter.discovery.model.RequestResult
import org.jellycopter.discovery.model.RequestUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OneClickRequestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyseerrClient
    private lateinit var requestHandler: RequestHandler

    @Before
    fun setUp() {
        server = MockWebServer()
        client = JellyseerrClient.create(baseUrl = server.url("/api/v1/"), apiKey = "apikey123")
        requestHandler = client.requestHandler
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun testOneClickMovieRequestPayloadConstructionAndSuccess() = runTest {
        // Mock Radarr server list
        val radarrServersJson = """
        [
          {
            "id": 1,
            "name": "Radarr 1080p",
            "isDefault": true,
            "is4k": false,
            "activeProfileId": 4,
            "activeDirectory": "/data/movies"
          }
        ]
        """.trimIndent()

        val requestSuccessJson = """
        {
          "id": 901,
          "status": 2,
          "is4k": false,
          "serverId": 1,
          "profileId": 4
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(radarrServersJson))
        server.enqueue(MockResponse().setResponseCode(201).setBody(requestSuccessJson))

        requestHandler.uiState.test {
            assertEquals(RequestUiState.Idle, awaitItem())

            val result = client.requestOneClick(
                mediaId = 12345,
                mediaType = "movie",
                title = "Oppenheimer"
            )

            // Submitting state emitted
            val submittingState = awaitItem()
            assertTrue(submittingState is RequestUiState.Submitting)
            assertEquals(12345, (submittingState as RequestUiState.Submitting).mediaId)

            // Success state emitted (triggers animated confirmation)
            val successState = awaitItem()
            assertTrue(successState is RequestUiState.Success)
            assertEquals(901, (successState as RequestUiState.Success).requestId)
            assertEquals("Requested successfully", successState.message)

            assertTrue(result is RequestResult.Success)
            assertEquals(901, (result as RequestResult.Success).requestId)
            assertEquals(2, result.status)
            assertFalse(result.is4k)
        }

        // Verify request payload sent to Jellyseerr
        val req1 = server.takeRequest() // Radarr servers call
        assertEquals("GET", req1?.method)
        assertEquals("/api/v1/service/radarr", req1?.path)

        val req2 = server.takeRequest() // POST request call
        assertEquals("POST", req2?.method)
        assertEquals("/api/v1/request", req2?.path)
        val body = req2?.body ?: ""
        assertTrue(body.contains("\"mediaType\":\"movie\""))
        assertTrue(body.contains("\"mediaId\":12345"))
        assertTrue(body.contains("\"is4k\":false"))
        assertTrue(body.contains("\"serverId\":1"))
        assertTrue(body.contains("\"profileId\":4"))
        assertFalse(body.contains("\"seasons\"")) // seasons omitted for movies
    }

    @Test
    fun testOneClickTvRequestDefaultsToAllSeasons() = runTest {
        // Mock Sonarr server list
        val sonarrServersJson = """
        [
          {
            "id": 2,
            "name": "Sonarr TV",
            "isDefault": true,
            "is4k": false,
            "activeProfileId": 1,
            "activeProfileName": "HD-1080p",
            "activeDirectory": "/data/tv",
            "activeAnimeProfileId": 5,
            "activeAnimeDirectory": "/data/anime"
          }
        ]
        """.trimIndent()

        val requestSuccessJson = """
        {
          "id": 902,
          "status": 2,
          "is4k": false,
          "serverId": 2,
          "profileId": 1
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(sonarrServersJson))
        server.enqueue(MockResponse().setResponseCode(201).setBody(requestSuccessJson))

        val result = client.requestOneClick(
            mediaId = 60059,
            mediaType = "tv",
            title = "Better Call Saul",
            isAnime = false
        )

        assertTrue(result is RequestResult.Success)
        val postReq = server.takeRequest() // Sonarr servers call
        assertEquals("/api/v1/service/sonarr", postReq?.path)

        val req2 = server.takeRequest() // POST request call
        val body = req2?.body ?: ""
        assertTrue(body.contains("\"mediaType\":\"tv\""))
        assertTrue(body.contains("\"mediaId\":60059"))
        assertTrue(body.contains("\"seasons\":\"all\""))
        assertTrue(body.contains("\"serverId\":2"))
        assertTrue(body.contains("\"profileId\":1"))
    }

    @Test
    fun testOneClickAnimeRequestSelectsAnimeProfile() = runTest {
        val sonarrServersJson = """
        [
          {
            "id": 3,
            "name": "Sonarr Mixed",
            "isDefault": true,
            "activeProfileId": 1,
            "activeAnimeProfileId": 9,
            "activeAnimeDirectory": "/data/anime"
          }
        ]
        """.trimIndent()

        val requestSuccessJson = """
        {
          "id": 903,
          "status": 1,
          "is4k": false,
          "serverId": 3,
          "profileId": 9
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(sonarrServersJson))
        server.enqueue(MockResponse().setResponseCode(201).setBody(requestSuccessJson))

        val result = client.requestOneClick(
            mediaId = 1429,
            mediaType = "tv",
            title = "Attack on Titan",
            isAnime = true
        )

        assertTrue(result is RequestResult.Success)
        server.takeRequest() // Sonarr call
        val req2 = server.takeRequest()
        val body = req2?.body ?: ""
        assertTrue(body.contains("\"profileId\":9"))
        assertTrue(body.contains("\"rootFolder\":\"/data/anime\""))
    }
}
