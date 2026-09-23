package org.jellycopter.discovery

import kotlinx.coroutines.test.runTest
import org.jellycopter.discovery.model.JellyseerrRequestPayload
import org.jellycopter.discovery.model.RequestResult
import org.jellycopter.discovery.model.RequestUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConflictAndErrorHandlingTest {

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
    fun testConflict409HandledGracefullyWithoutFatalException() = runTest {
        // Enqueue empty Radarr servers list (forces direct payload)
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        // Enqueue 409 Conflict error
        val conflictJson = """
        {
          "message": "Request already exists"
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(409).setBody(conflictJson))

        val result = client.requestOneClick(
            mediaId = 55555,
            mediaType = "movie",
            title = "Inception"
        )

        assertTrue("Expected RequestResult.AlreadyRequested", result is RequestResult.AlreadyRequested)
        val alreadyRequested = result as RequestResult.AlreadyRequested
        assertEquals(55555, alreadyRequested.mediaId)
        assertEquals("Request already exists", alreadyRequested.message)

        // Verify animated UI state
        val uiState = requestHandler.uiState.value
        assertTrue("Expected RequestUiState.AlreadyRequested", uiState is RequestUiState.AlreadyRequested)
        val uiAlreadyRequested = uiState as RequestUiState.AlreadyRequested
        assertEquals(55555, uiAlreadyRequested.mediaId)
        assertEquals("Request already exists", uiAlreadyRequested.message)
    }

    @Test
    fun testPermissionDenied403HandledGracefully() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val forbiddenJson = """
        {
          "message": "User does not have permission to request media"
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(403).setBody(forbiddenJson))

        val result = client.requestOneClick(
            mediaId = 77777,
            mediaType = "movie",
            title = "Restricted Movie"
        )

        assertTrue(result is RequestResult.PermissionDenied)
        val denied = result as RequestResult.PermissionDenied
        assertEquals(77777, denied.mediaId)
        assertEquals("User does not have permission to request media", denied.message)

        val uiState = requestHandler.uiState.value
        assertTrue(uiState is RequestUiState.PermissionDenied)
        val uiDenied = uiState as RequestUiState.PermissionDenied
        assertEquals(77777, uiDenied.mediaId)
    }

    @Test
    fun testServerError500HandledGracefully() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val errorJson = """
        {
          "message": "Internal Radarr connection failure"
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorJson))

        val result = client.requestOneClick(
            mediaId = 88888,
            mediaType = "movie",
            title = "Broken Server Title"
        )

        assertTrue(result is RequestResult.Error)
        val errorResult = result as RequestResult.Error
        assertEquals(500, errorResult.code)
        assertEquals("Internal Radarr connection failure", errorResult.message)

        val uiState = requestHandler.uiState.value
        assertTrue(uiState is RequestUiState.Error)
        val uiError = uiState as RequestUiState.Error
        assertEquals(88888, uiError.mediaId)
    }

    @Test
    fun testNetworkTimeoutGracefulHandling() = runTest {
        // Create client pointing to closed server
        val brokenClient = JellyseerrClient.create(
            baseUrl = "http://127.0.0.1:59999/api/v1/",
            connectTimeoutSeconds = 1,
            readTimeoutSeconds = 1
        )

        val result = brokenClient.requestOneClick(
            mediaId = 99999,
            mediaType = "movie",
            title = "Timeout Title"
        )

        assertTrue(result is RequestResult.Error)
        val error = result as RequestResult.Error
        assertEquals(-1, error.code)
        assertTrue(error.message.isNotEmpty())

        val uiState = brokenClient.uiState.value
        assertTrue(uiState is RequestUiState.Error)
    }
}
