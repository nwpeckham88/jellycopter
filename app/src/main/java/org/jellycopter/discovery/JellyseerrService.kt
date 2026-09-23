package org.jellycopter.discovery

import okhttp3.ResponseBody
import org.jellycopter.discovery.model.DiscoverPageResponse
import org.jellycopter.discovery.model.JellyseerrRequestPayload
import org.jellycopter.discovery.model.MediaRequestDto
import org.jellycopter.discovery.model.MovieDetailsDto
import org.jellycopter.discovery.model.QualityProfileDto
import org.jellycopter.discovery.model.RadarrServerDto
import org.jellycopter.discovery.model.SonarrServerDto
import org.jellycopter.discovery.model.TvDetailsDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyseerrService {

    @GET("discover/trending")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("discover/movies")
    suspend fun getDiscoverMovies(
        @Query("page") page: Int = 1,
        @Query("sortBy") sortBy: String = "popularity.desc",
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("discover/tv")
    suspend fun getDiscoverTv(
        @Query("page") page: Int = 1,
        @Query("sortBy") sortBy: String = "popularity.desc",
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("discover/movies/upcoming")
    suspend fun getUpcomingMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("discover/tv/upcoming")
    suspend fun getUpcomingTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("discover/movies/studio/{studioId}")
    suspend fun getStudioMovies(
        @Path("studioId") studioId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("discover/tv/network/{networkId}")
    suspend fun getNetworkTv(
        @Path("networkId") networkId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): DiscoverPageResponse

    @GET("tv/{tvId}")
    suspend fun getTvDetails(
        @Path("tvId") tvId: Int,
        @Query("language") language: String = "en"
    ): TvDetailsDto

    @GET("movie/{movieId}")
    suspend fun getMovieDetails(
        @Path("movieId") movieId: Int,
        @Query("language") language: String = "en"
    ): MovieDetailsDto

    @POST("request")
    suspend fun postRequest(
        @Body payload: JellyseerrRequestPayload,
        @Header("X-Api-Key") apiKey: String? = null
    ): Response<MediaRequestDto>

    @GET("service/radarr")
    suspend fun getRadarrServers(): List<RadarrServerDto>

    @GET("service/sonarr")
    suspend fun getSonarrServers(): List<SonarrServerDto>

    @GET("service/radarr/{id}/profiles")
    suspend fun getRadarrProfiles(@Path("id") serverId: Int): List<QualityProfileDto>

    @GET("service/sonarr/{id}/profiles")
    suspend fun getSonarrProfiles(@Path("id") serverId: Int): List<QualityProfileDto>
}
