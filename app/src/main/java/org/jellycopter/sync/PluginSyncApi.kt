package org.jellycopter.sync

import okhttp3.ResponseBody
import org.jellycopter.sync.model.AchievementSyncDto
import org.jellycopter.sync.model.DeckSyncDto
import org.jellycopter.sync.model.PingResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PluginSyncApi {

    @GET("Jellycopter/Ping")
    suspend fun ping(): Response<PingResponseDto>

    @GET("Jellycopter/Decks")
    suspend fun getDecks(@Query("userId") userId: String? = null): Response<List<DeckSyncDto>>

    @POST("Jellycopter/Decks")
    suspend fun upsertDeck(
        @Body deck: DeckSyncDto,
        @Query("userId") userId: String? = null
    ): Response<DeckSyncDto>

    @GET("Jellycopter/Achievements")
    suspend fun getAchievements(@Query("userId") userId: String? = null): Response<List<AchievementSyncDto>>

    @POST("Jellycopter/Achievements")
    suspend fun upsertAchievements(
        @Body achievements: List<AchievementSyncDto>,
        @Query("userId") userId: String? = null
    ): Response<ResponseBody>
}
