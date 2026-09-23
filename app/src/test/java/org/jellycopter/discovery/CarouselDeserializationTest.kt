package org.jellycopter.discovery

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CarouselDeserializationTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyseerrClient

    @Before
    fun setUp() {
        server = MockWebServer()
        client = JellyseerrClient.create(baseUrl = server.url("/api/v1/"), apiKey = "test_api_key")
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun testTrendingCarouselDeserializesMixedMediaTypes() = runTest {
        val jsonBody = """
        {
          "page": 1,
          "totalPages": 5,
          "totalResults": 2,
          "results": [
            {
              "id": 101,
              "mediaType": "movie",
              "title": "Dune: Part Two",
              "overview": "Paul Atreides unites with Chani and the Fremen.",
              "posterPath": "/dune2.jpg",
              "backdropPath": "/dune2_bg.jpg",
              "releaseDate": "2024-03-01",
              "voteAverage": 8.4,
              "mediaInfo": {
                "id": 1,
                "tmdbId": 101,
                "status": 5,
                "requests": []
              }
            },
            {
              "id": 202,
              "mediaType": "tv",
              "name": "Severance",
              "overview": "Mark leads a team of office workers whose memories have been surgically divided.",
              "posterPath": "/severance.jpg",
              "backdropPath": "/severance_bg.jpg",
              "firstAirDate": "2022-02-18",
              "voteAverage": 8.7,
              "mediaInfo": {
                "id": 2,
                "tmdbId": 202,
                "status": 2,
                "requests": [
                  {
                    "id": 55,
                    "status": 2,
                    "is4k": false
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonBody))

        val items = client.getTrending(page = 1)
        assertEquals(2, items.size)

        // Movie assertion
        val movie = items[0]
        assertEquals(101, movie.id)
        assertEquals("movie", movie.mediaType)
        assertEquals("Dune: Part Two", movie.title)
        assertEquals("/dune2.jpg", movie.posterPath)
        assertEquals(5, movie.status) // Available
        assertFalse(movie.isRequested)

        // TV assertion
        val tv = items[1]
        assertEquals(202, tv.id)
        assertEquals("tv", tv.mediaType)
        assertEquals("Severance", tv.title)
        assertEquals("/severance.jpg", tv.posterPath)
        assertEquals(2, tv.status) // Pending
        assertTrue(tv.isRequested)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded?.method)
        assertEquals("/api/v1/discover/trending?page=1&language=en", recorded?.path)
        assertEquals("test_api_key", recorded?.getHeader("X-Api-Key"))
    }

    @Test
    fun testPopularMoviesAndTvCarousels() = runTest {
        val moviesJson = """
        {
          "page": 1,
          "totalPages": 10,
          "totalResults": 1,
          "results": [
            {
              "id": 550,
              "title": "Fight Club",
              "releaseDate": "1999-10-15",
              "voteAverage": 8.4,
              "mediaInfo": {
                "status": 1
              }
            }
          ]
        }
        """.trimIndent()

        val tvJson = """
        {
          "page": 1,
          "totalPages": 10,
          "totalResults": 1,
          "results": [
            {
              "id": 1399,
              "name": "Game of Thrones",
              "firstAirDate": "2011-04-17",
              "voteAverage": 8.4,
              "mediaInfo": null
            }
          ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(moviesJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(tvJson))

        val movies = client.getPopularMovies(page = 1)
        val tvShows = client.getPopularTv(page = 1)

        assertEquals(1, movies.size)
        assertEquals("Fight Club", movies[0].title)
        assertEquals("movie", movies[0].mediaType)
        assertEquals(1, movies[0].status) // UNKNOWN

        assertEquals(1, tvShows.size)
        assertEquals("Game of Thrones", tvShows[0].title)
        assertEquals("tv", tvShows[0].mediaType)
        assertEquals(1, tvShows[0].status) // Default UNKNOWN when mediaInfo is null

        val req1 = server.takeRequest()
        assertEquals("/api/v1/discover/movies?page=1&sortBy=popularity.desc&language=en", req1?.path)

        val req2 = server.takeRequest()
        assertEquals("/api/v1/discover/tv?page=1&sortBy=popularity.desc&language=en", req2?.path)
    }

    @Test
    fun testUpcomingMoviesAndTvCarousels() = runTest {
        val upcomingMoviesJson = """
        {
          "page": 1,
          "totalPages": 1,
          "totalResults": 1,
          "results": [
            {
              "id": 888,
              "title": "Upcoming Blockbuster",
              "releaseDate": "2027-01-01"
            }
          ]
        }
        """.trimIndent()

        val upcomingTvJson = """
        {
          "page": 1,
          "totalPages": 1,
          "totalResults": 1,
          "results": [
            {
              "id": 999,
              "name": "Upcoming Sci-Fi Series",
              "firstAirDate": "2027-03-15"
            }
          ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(upcomingMoviesJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(upcomingTvJson))

        val movies = client.getUpcoming("movie", page = 1)
        val tvSeries = client.getUpcoming("tv", page = 1)

        assertEquals("Upcoming Blockbuster", movies[0].title)
        assertEquals("Upcoming Sci-Fi Series", tvSeries[0].title)

        val req1 = server.takeRequest()
        assertEquals("/api/v1/discover/movies/upcoming?page=1&language=en", req1?.path)

        val req2 = server.takeRequest()
        assertEquals("/api/v1/discover/tv/upcoming?page=1&language=en", req2?.path)
    }

    @Test
    fun testStudioAndNetworkCarousels() = runTest {
        val studioJson = """
        {
          "page": 1,
          "totalPages": 1,
          "totalResults": 1,
          "results": [
            {
              "id": 12,
              "title": "Finding Nemo",
              "releaseDate": "2003-05-30"
            }
          ]
        }
        """.trimIndent()

        val networkJson = """
        {
          "page": 1,
          "totalPages": 1,
          "totalResults": 1,
          "results": [
            {
              "id": 66732,
              "name": "Stranger Things",
              "firstAirDate": "2016-07-15"
            }
          ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(studioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(networkJson))

        val studioMovies = client.getStudioMovies(studioId = 3, page = 1) // Pixar
        val networkShows = client.getNetworkTv(networkId = 213, page = 1) // Netflix

        assertEquals(1, studioMovies.size)
        assertEquals("Finding Nemo", studioMovies[0].title)
        assertEquals("movie", studioMovies[0].mediaType)

        assertEquals(1, networkShows.size)
        assertEquals("Stranger Things", networkShows[0].title)
        assertEquals("tv", networkShows[0].mediaType)

        val req1 = server.takeRequest()
        assertEquals("/api/v1/discover/movies/studio/3?page=1&language=en", req1?.path)

        val req2 = server.takeRequest()
        assertEquals("/api/v1/discover/tv/network/213?page=1&language=en", req2?.path)
    }
}
