package org.jellycopter.media

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellycopter.core.repository.DefaultMediaRepository
import org.jellycopter.core.repository.JellyfinImageUrlBuilder
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.session.UserSession
import org.jellycopter.di.appModules
import org.jellycopter.discovery.MockResponse
import org.jellycopter.discovery.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

class MediaRepositoryTest : KoinTest {

    private lateinit var mockStorage: EncryptedSessionStorage
    private lateinit var mediaRepository: DefaultMediaRepository
    private var mockWebServer: MockWebServer? = null

    @Before
    fun setUp() {
        stopKoin()
        mockStorage = mockk(relaxed = true)
        every { mockStorage.getSession() } returns null
        mediaRepository = DefaultMediaRepository(sessionStorage = mockStorage)
    }

    @After
    fun tearDown() {
        stopKoin()
        mockWebServer?.close()
        mockWebServer = null
    }

    @Test
    fun testOfflineVirtualFoldersReturnsDefaultLibraries() = runTest {
        val folders = mediaRepository.getVirtualFolders()
        assertNotNull(folders)
        assertTrue(folders.size >= 3)

        val folderShows = folders.firstOrNull { it.id == "folder_shows" }
        assertNotNull(folderShows)
        assertEquals("TV Shows", folderShows?.name)
        assertEquals("tvshows", folderShows?.collectionType)

        val folderAnim = folders.firstOrNull { it.id == "folder_animation" }
        assertNotNull(folderAnim)
        assertEquals("Animation & Cartoons", folderAnim?.name)

        val folderMovies = folders.firstOrNull { it.id == "folder_movies" }
        assertNotNull(folderMovies)
        assertEquals("Movies", folderMovies?.name)
        assertEquals("movies", folderMovies?.collectionType)
    }

    @Test
    fun testOfflineSeriesInFolderReturnsMatchingShows() = runTest {
        val tvShows = mediaRepository.getSeriesInFolder("folder_shows")
        assertFalse(tvShows.isEmpty())

        val severance = tvShows.firstOrNull { it.id == "series_1" }
        assertNotNull(severance)
        assertEquals("Severance", severance?.name)
        assertEquals(2022, severance?.premiereYear)
        assertEquals(8.7f, severance?.communityRating ?: 0f, 0.05f)

        val animationShows = mediaRepository.getSeriesInFolder("folder_animation")
        assertTrue(animationShows.any { it.name == "Futurama" })
        assertTrue(animationShows.any { it.name == "Cowboy Bebop" })
    }

    @Test
    fun testOfflineSeriesDetailContainsSeasonsAndNextUp() = runTest {
        val detail = mediaRepository.getSeriesDetail("series_1")
        assertNotNull(detail)
        assertEquals("Severance", detail?.name)
        assertFalse(detail?.overview.isNullOrBlank())
        assertFalse(detail?.posterUrl.isNullOrBlank())
        assertFalse(detail?.backdropUrl.isNullOrBlank())

        // Seasons
        val seasons = detail?.seasons ?: emptyList()
        assertTrue(seasons.size >= 2)
        assertEquals("Season 1", seasons[0].name)
        assertEquals(1, seasons[0].seasonNumber)
        assertEquals(9, seasons[0].episodes.size)

        // Next Up Episode (S1E2 "Half Loop")
        val nextUp = detail?.nextUpEpisode
        assertNotNull(nextUp)
        assertEquals("Half Loop", nextUp?.name)
        assertEquals(1, nextUp?.seasonNumber)
        assertEquals(2, nextUp?.indexNumber)
    }

    @Test
    fun testOfflineNextUpForSeries() = runTest {
        val nextUp = mediaRepository.getNextUpForSeries("series_1")
        assertNotNull(nextUp)
        assertEquals("ep_sev_102", nextUp?.id)
        assertEquals("Half Loop", nextUp?.name)
        assertEquals(1, nextUp?.seasonNumber)
        assertEquals(2, nextUp?.indexNumber)
    }

    @Test
    fun testOfflineFirstEpisodeForSeries() = runTest {
        val firstEp = mediaRepository.getFirstEpisodeForSeries("series_1")
        assertNotNull(firstEp)
        assertEquals("ep_sev_101", firstEp?.id)
        assertEquals("Good News About Hell", firstEp?.name)
        assertEquals(1, firstEp?.seasonNumber)
        assertEquals(1, firstEp?.indexNumber)
        assertEquals(34200000000L, firstEp?.runTimeTicks)
    }

    @Test
    fun testOfflineAllEpisodesAndEpisodesForSeason() = runTest {
        val allEpisodes = mediaRepository.getAllEpisodesForSeries("series_1")
        assertEquals(11, allEpisodes.size)

        val s1Episodes = mediaRepository.getEpisodesForSeason("series_1", "season_sev_1")
        assertEquals(9, s1Episodes.size)
        assertEquals(1, s1Episodes.first().indexNumber)
        assertEquals(9, s1Episodes.last().indexNumber)
    }

    @Test
    fun testJellyfinImageUrlBuilderEndpoints() {
        val serverUrl = "http://192.168.1.50:8096"
        val itemId = "item_sev_101"

        // Primary Image URL
        val primaryUrl = JellyfinImageUrlBuilder.buildPrimaryImageUrl(serverUrl, itemId)
        assertEquals("http://192.168.1.50:8096/Items/item_sev_101/Images/Primary", primaryUrl)

        // Backdrop Image URL
        val backdropUrl = JellyfinImageUrlBuilder.buildBackdropImageUrl(serverUrl, itemId)
        assertEquals("http://192.168.1.50:8096/Items/item_sev_101/Images/Backdrop", backdropUrl)

        // Image URL with parameters
        val parametrizedUrl = JellyfinImageUrlBuilder.buildPrimaryImageUrl(
            serverUrl = "http://192.168.1.50:8096/",
            itemId = itemId,
            fillWidth = 400,
            quality = 90
        )
        assertEquals("http://192.168.1.50:8096/Items/item_sev_101/Images/Primary?fillWidth=400&quality=90", parametrizedUrl)
    }

    @Test
    fun testLiveServerIntegrationWithMockServer() = runTest {
        val server = MockWebServer()
        mockWebServer = server

        val session = UserSession(
            serverUrl = server.url(),
            accessToken = "token_live_123",
            userId = "usr_42",
            username = "Nathan",
            serverName = "MockServer"
        )
        every { mockStorage.getSession() } returns session

        // 1. Enqueue Views response
        val viewsJson = """
            {
              "Items": [
                { "Id": "lib_tv", "Name": "Television", "CollectionType": "tvshows" },
                { "Id": "lib_movies", "Name": "Cinema", "CollectionType": "movies" }
              ],
              "TotalRecordCount": 2
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(viewsJson))

        val liveFolders = mediaRepository.getVirtualFolders()
        assertEquals(2, liveFolders.size)
        assertEquals("lib_tv", liveFolders[0].id)
        assertEquals("Television", liveFolders[0].name)
        assertEquals("tvshows", liveFolders[0].collectionType)

        val viewsRequest = server.takeRequest()
        assertNotNull(viewsRequest)
        assertEquals("token_live_123", viewsRequest?.getHeader("X-Emby-Token"))
        assertTrue(viewsRequest?.path?.contains("/Users/usr_42/Views") == true)

        // 2. Enqueue Series in folder response
        val seriesJson = """
            {
              "Items": [
                {
                  "Id": "series_wire",
                  "Name": "The Wire",
                  "Overview": "Baltimore drug scene through the eyes of law enforcement.",
                  "CommunityRating": 9.3,
                  "ProductionYear": 2002
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(seriesJson))

        val seriesList = mediaRepository.getSeriesInFolder("lib_tv")
        assertEquals(1, seriesList.size)
        val theWire = seriesList[0]
        assertEquals("series_wire", theWire.id)
        assertEquals("The Wire", theWire.name)
        assertEquals(2002, theWire.premiereYear)
        assertEquals(9.3f, theWire.communityRating ?: 0f, 0.05f)
        assertTrue(theWire.posterUrl?.contains("/Items/series_wire/Images/Primary") == true)
        assertTrue(theWire.backdropUrl?.contains("/Items/series_wire/Images/Backdrop") == true)

        // 3. Enqueue Next Up response
        val nextUpJson = """
            {
              "Items": [
                {
                  "Id": "ep_wire_203",
                  "Name": "Hot Shots",
                  "SeriesId": "series_wire",
                  "SeriesName": "The Wire",
                  "SeasonId": "season_wire_2",
                  "ParentIndexNumber": 2,
                  "IndexNumber": 3,
                  "RunTimeTicks": 33000000000,
                  "Overview": "The port investigation accelerates."
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(nextUpJson))

        val nextUp = mediaRepository.getNextUpForSeries("series_wire")
        assertNotNull(nextUp)
        assertEquals("ep_wire_203", nextUp?.id)
        assertEquals("Hot Shots", nextUp?.name)
        assertEquals(2, nextUp?.seasonNumber)
        assertEquals(3, nextUp?.indexNumber)
        assertEquals(33000000000L, nextUp?.runTimeTicks)
    }

    @Test
    fun testKoinMediaRepositoryResolution() {
        val mockContext = mockk<Context>(relaxed = true)
        startKoin {
            androidContext(mockContext)
            modules(appModules)
        }

        val resolved = get<MediaRepository>()
        assertNotNull(resolved)
        assertTrue(resolved is DefaultMediaRepository)
    }
}
