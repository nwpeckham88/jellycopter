package org.jellycopter.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.jellycopter.core.model.MediaRequestPayload
import org.jellycopter.discovery.model.JellyseerrErrorResponse
import org.jellycopter.discovery.model.JellyseerrRequestPayload
import org.jellycopter.discovery.model.RadarrServerDto
import org.jellycopter.discovery.model.RequestResult
import org.jellycopter.discovery.model.RequestUiState
import org.jellycopter.discovery.model.SeasonsField
import org.jellycopter.discovery.model.SonarrServerDto
import retrofit2.Response

class RequestHandler(
    private val service: JellyseerrService,
    private val apiKey: String? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }
) {

    private val _uiState = MutableStateFlow<RequestUiState>(RequestUiState.Idle)
    val uiState: StateFlow<RequestUiState> = _uiState.asStateFlow()

    private var cachedRadarrServers: List<RadarrServerDto>? = null
    private var cachedSonarrServers: List<SonarrServerDto>? = null
    private val radarrMutex = Mutex()
    private val sonarrMutex = Mutex()

    fun resetState() {
        _uiState.value = RequestUiState.Idle
    }

    /**
     * Submits a 1-Click request with smart defaults:
     * - Automatic server resolution (isDefault: true or first available)
     * - Automatic quality profile resolution (activeProfileId, or activeAnimeProfileId if anime)
     * - Default to 1080p SDR (is4k: false)
     * - Default to all seasons ("all") for TV series
     */
    suspend fun submitOneClickRequest(
        mediaId: Int,
        mediaType: String,
        title: String = "",
        isAnime: Boolean = false
    ): RequestResult {
        _uiState.value = RequestUiState.Submitting(mediaId, title)

        val resolvedPayload = resolveSmartDefaults(
            mediaId = mediaId,
            mediaType = mediaType,
            isAnime = isAnime
        )

        return executeRequest(resolvedPayload, title)
    }

    /**
     * Submits a long-press customized configuration request.
     */
    suspend fun submitConfiguredRequest(config: RequestConfig): RequestResult {
        _uiState.value = RequestUiState.Submitting(config.mediaId, config.title)
        val payload = config.toJellyseerrPayload()
        return executeRequest(payload, config.title)
    }

    /**
     * Submits a request using the core MediaRequestPayload domain model.
     */
    suspend fun submitCoreRequest(payload: MediaRequestPayload, title: String = ""): RequestResult {
        _uiState.value = RequestUiState.Submitting(payload.mediaId, title)
        val seasonsField = if (payload.mediaType == "tv") {
            if (payload.seasons.isNullOrEmpty()) SeasonsField.All else SeasonsField.Custom(payload.seasons!!)
        } else {
            null
        }
        val requestPayload = JellyseerrRequestPayload(
            mediaType = payload.mediaType,
            mediaId = payload.mediaId,
            is4k = payload.is4k,
            seasons = seasonsField,
            serverId = payload.serverId,
            profileId = payload.profileId,
            rootFolder = payload.rootFolder
        )
        return executeRequest(requestPayload, title)
    }

    private suspend fun resolveSmartDefaults(
        mediaId: Int,
        mediaType: String,
        isAnime: Boolean
    ): JellyseerrRequestPayload {
        var serverId: Int? = null
        var profileId: Int? = null
        var rootFolder: String? = null

        try {
            if (mediaType.equals("tv", ignoreCase = true)) {
                val sonarrList = getSonarrServers()
                val server = sonarrList.firstOrNull { it.isDefault } ?: sonarrList.firstOrNull()
                if (server != null) {
                    serverId = server.id
                    if (isAnime && server.activeAnimeProfileId != null) {
                        profileId = server.activeAnimeProfileId
                        rootFolder = server.activeAnimeDirectory ?: server.activeDirectory
                    } else {
                        profileId = server.activeProfileId
                        rootFolder = server.activeDirectory
                    }
                }
            } else {
                val radarrList = getRadarrServers()
                val server = radarrList.firstOrNull { it.isDefault } ?: radarrList.firstOrNull()
                if (server != null) {
                    serverId = server.id
                    profileId = server.activeProfileId
                    rootFolder = server.activeDirectory
                }
            }
        } catch (_: Exception) {
            // If fetching server list fails (e.g. non-admin user), Jellyseerr server-side defaults apply
        }

        val seasons = if (mediaType.equals("tv", ignoreCase = true)) {
            SeasonsField.All
        } else {
            null
        }

        return JellyseerrRequestPayload(
            mediaType = mediaType.lowercase(),
            mediaId = mediaId,
            is4k = false,
            seasons = seasons,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder
        )
    }

    private suspend fun executeRequest(
        payload: JellyseerrRequestPayload,
        title: String
    ): RequestResult {
        return try {
            val response = service.postRequest(payload, apiKey)
            handleHttpResponse(response, payload.mediaId)
        } catch (e: Exception) {
            val errResult = RequestResult.Error(
                code = -1,
                message = e.localizedMessage ?: "Network communication failure",
                cause = e
            )
            _uiState.value = RequestUiState.Error(
                mediaId = payload.mediaId,
                message = errResult.message
            )
            errResult
        }
    }

    private fun handleHttpResponse(
        response: Response<org.jellycopter.discovery.model.MediaRequestDto>,
        mediaId: Int
    ): RequestResult {
        return when (response.code()) {
            200, 201 -> {
                val body = response.body()
                val requestId = body?.id ?: 0
                val status = body?.status ?: 2
                val is4k = body?.is4k ?: false
                val successResult = RequestResult.Success(
                    requestId = requestId,
                    status = status,
                    is4k = is4k,
                    message = "Requested successfully"
                )
                _uiState.value = RequestUiState.Success(
                    mediaId = mediaId,
                    requestId = requestId,
                    message = successResult.message
                )
                successResult
            }
            409 -> {
                val errorMessage = extractErrorMessage(response) ?: "Request already exists"
                val conflictResult = RequestResult.AlreadyRequested(
                    mediaId = mediaId,
                    message = errorMessage
                )
                _uiState.value = RequestUiState.AlreadyRequested(
                    mediaId = mediaId,
                    message = errorMessage
                )
                conflictResult
            }
            403 -> {
                val errorMessage = extractErrorMessage(response) ?: "User does not have permission to request media"
                val forbiddenResult = RequestResult.PermissionDenied(
                    mediaId = mediaId,
                    message = errorMessage
                )
                _uiState.value = RequestUiState.PermissionDenied(
                    mediaId = mediaId,
                    message = errorMessage
                )
                forbiddenResult
            }
            else -> {
                val errorMessage = extractErrorMessage(response) ?: "Request failed with HTTP ${response.code()}"
                val errorResult = RequestResult.Error(
                    code = response.code(),
                    message = errorMessage
                )
                _uiState.value = RequestUiState.Error(
                    mediaId = mediaId,
                    message = errorMessage
                )
                errorResult
            }
        }
    }

    private fun extractErrorMessage(response: Response<*>): String? {
        return try {
            val errorBody = response.errorBody()?.string() ?: return null
            val parsed = json.decodeFromString<JellyseerrErrorResponse>(errorBody)
            parsed.message ?: parsed.error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getRadarrServers(): List<RadarrServerDto> {
        cachedRadarrServers?.let { return it }
        return radarrMutex.withLock {
            cachedRadarrServers?.let { return it }
            try {
                val servers = service.getRadarrServers()
                cachedRadarrServers = servers
                servers
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun getSonarrServers(): List<SonarrServerDto> {
        cachedSonarrServers?.let { return it }
        return sonarrMutex.withLock {
            cachedSonarrServers?.let { return it }
            try {
                val servers = service.getSonarrServers()
                cachedSonarrServers = servers
                servers
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
