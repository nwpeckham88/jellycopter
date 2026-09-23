package org.jellycopter.discovery

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.jellycopter.core.model.MediaDiscoverItem
import org.jellycopter.core.model.MediaRequestPayload
import org.jellycopter.core.model.SeasonStatus
import org.jellycopter.discovery.model.MovieDetailsDto
import org.jellycopter.discovery.model.RequestResult
import org.jellycopter.discovery.model.RequestUiState
import org.jellycopter.discovery.model.TvDetailsDto
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class JellyseerrClient(
    val service: JellyseerrService,
    val requestHandler: RequestHandler = RequestHandler(service),
    val missingSeasonDetector: MissingSeasonDetector = MissingSeasonDetector(),
    var localSeasonProvider: (suspend (seriesId: String) -> Set<Int>)? = null,
    var localEpisodeProvider: (suspend (seriesId: String) -> Map<Int, Int>)? = null
) {

    val uiState: StateFlow<RequestUiState> get() = requestHandler.uiState

    /**
     * Trending movies and TV shows (/discover/trending).
     */
    suspend fun getTrending(page: Int = 1): List<MediaDiscoverItem> {
        val response = service.getTrending(page = page)
        return response.results.map { it.toMediaDiscoverItem(defaultMediaType = "movie") }
    }

    /**
     * Popular media (/discover/movies).
     */
    suspend fun getPopular(page: Int = 1): List<MediaDiscoverItem> {
        return getPopularMovies(page)
    }

    /**
     * Popular movies (/discover/movies?sortBy=popularity.desc).
     */
    suspend fun getPopularMovies(page: Int = 1): List<MediaDiscoverItem> {
        val response = service.getDiscoverMovies(page = page, sortBy = "popularity.desc")
        return response.results.map { it.toMediaDiscoverItem(defaultMediaType = "movie") }
    }

    /**
     * Popular TV shows (/discover/tv?sortBy=popularity.desc).
     */
    suspend fun getPopularTv(page: Int = 1): List<MediaDiscoverItem> {
        val response = service.getDiscoverTv(page = page, sortBy = "popularity.desc")
        return response.results.map { it.toMediaDiscoverItem(defaultMediaType = "tv") }
    }

    /**
     * Upcoming movie or TV releases (/discover/movies/upcoming or /discover/tv/upcoming).
     */
    suspend fun getUpcoming(mediaType: String, page: Int = 1): List<MediaDiscoverItem> {
        return if (mediaType.equals("tv", ignoreCase = true)) {
            val response = service.getUpcomingTv(page = page)
            response.results.map { it.toMediaDiscoverItem(defaultMediaType = "tv") }
        } else {
            val response = service.getUpcomingMovies(page = page)
            response.results.map { it.toMediaDiscoverItem(defaultMediaType = "movie") }
        }
    }

    /**
     * Studio movie collection carousel (/discover/movies/studio/{studioId}).
     */
    suspend fun getStudioMovies(studioId: Int, page: Int = 1): List<MediaDiscoverItem> {
        val response = service.getStudioMovies(studioId = studioId, page = page)
        return response.results.map { it.toMediaDiscoverItem(defaultMediaType = "movie") }
    }

    /**
     * Network TV show collection carousel (/discover/tv/network/{networkId}).
     */
    suspend fun getNetworkTv(networkId: Int, page: Int = 1): List<MediaDiscoverItem> {
        val response = service.getNetworkTv(networkId = networkId, page = page)
        return response.results.map { it.toMediaDiscoverItem(defaultMediaType = "tv") }
    }

    /**
     * Full TV show details (/tv/{tvId}).
     */
    suspend fun getTvDetails(tvId: Int): TvDetailsDto {
        return service.getTvDetails(tvId = tvId)
    }

    /**
     * Full Movie details (/movie/{movieId}).
     */
    suspend fun getMovieDetails(movieId: Int): MovieDetailsDto {
        return service.getMovieDetails(movieId = movieId)
    }

    /**
     * Submits a media request using the core MediaRequestPayload.
     */
    suspend fun requestMedia(request: MediaRequestPayload): RequestResult {
        return requestHandler.submitCoreRequest(request)
    }

    /**
     * Submits an instant 1-Click request with smart defaults.
     */
    suspend fun requestOneClick(
        mediaId: Int,
        mediaType: String,
        title: String = "",
        isAnime: Boolean = false
    ): RequestResult {
        return requestHandler.submitOneClickRequest(
            mediaId = mediaId,
            mediaType = mediaType,
            title = title,
            isAnime = isAnime
        )
    }

    /**
     * Checks missing seasons for a given local series ID and TMDB ID.
     */
    suspend fun checkMissingSeasons(
        seriesId: String,
        tmdbId: Int,
        referenceDate: LocalDate = LocalDate.now()
    ): List<SeasonStatus> {
        val localSeasons = localSeasonProvider?.invoke(seriesId) ?: emptySet()
        val localEpisodes = localEpisodeProvider?.invoke(seriesId) ?: emptyMap()
        val tvDetails = service.getTvDetails(tmdbId)

        return missingSeasonDetector.detectSeasonStatuses(
            localSeasonNumbers = localSeasons,
            remoteSeasons = tvDetails.seasons,
            requests = tvDetails.mediaInfo?.requests ?: emptyList(),
            localEpisodeCounts = localEpisodes,
            referenceDate = referenceDate
        )
    }

    companion object {
        fun create(
            baseUrl: String,
            apiKey: String? = null,
            connectTimeoutSeconds: Long = 10,
            readTimeoutSeconds: Long = 10,
            json: Json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                coerceInputValues = true
                explicitNulls = false
            },
            localSeasonProvider: (suspend (String) -> Set<Int>)? = null,
            localEpisodeProvider: (suspend (String) -> Map<Int, Int>)? = null
        ): JellyseerrClient {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val okHttpClientBuilder = OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)

            if (!apiKey.isNullOrBlank()) {
                okHttpClientBuilder.addInterceptor(Interceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("X-Api-Key", apiKey)
                        .build()
                    chain.proceed(request)
                })
            }

            val okHttpClient = okHttpClientBuilder.build()
            val contentType = "application/json".toMediaType()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()

            val service = retrofit.create(JellyseerrService::class.java)
            val handler = RequestHandler(service, apiKey, json)
            val detector = MissingSeasonDetector()

            return JellyseerrClient(
                service = service,
                requestHandler = handler,
                missingSeasonDetector = detector,
                localSeasonProvider = localSeasonProvider,
                localEpisodeProvider = localEpisodeProvider
            )
        }
    }
}
