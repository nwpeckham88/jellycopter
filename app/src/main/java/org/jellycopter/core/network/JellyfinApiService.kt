package org.jellycopter.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jellycopter.core.model.ServerInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Request payload for authenticating by username and password.
 */
@Serializable
data class AuthenticateByNameRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String
)

/**
 * Basic user information returned inside [AuthenticateUserResult].
 */
@Serializable
data class UserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("ServerId") val serverId: String? = null
)

/**
 * Authentication result returned by /Users/AuthenticateByName.
 */
@Serializable
data class AuthenticateUserResult(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null
)

/**
 * Retrofit interface for Jellyfin server probing and authentication.
 */
interface JellyfinApiService {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<ServerInfo>

    @GET
    suspend fun getPublicSystemInfoByUrl(@Url url: String): Response<ServerInfo>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Header("X-Emby-Authorization") authHeader: String,
        @Body request: AuthenticateByNameRequest
    ): Response<AuthenticateUserResult>

    @POST
    suspend fun authenticateByNameByUrl(
        @Url url: String,
        @Header("X-Emby-Authorization") authHeader: String,
        @Body request: AuthenticateByNameRequest
    ): Response<AuthenticateUserResult>

    companion object {
        fun buildEmbyAuthHeader(
            clientName: String = "Jellycopter",
            deviceName: String = "AndroidTV",
            deviceId: String = "jellycopter-android",
            version: String = "1.0.0",
            token: String? = null
        ): String {
            val base = """MediaBrowser Client="$clientName", Device="$deviceName", DeviceId="$deviceId", Version="$version""""
            return if (token.isNullOrBlank()) base else """$base, Token="$token""""
        }
    }
}
