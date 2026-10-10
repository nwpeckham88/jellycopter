package org.jellycopter.core.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellycopter.core.model.EpisodeItem
import org.jellycopter.core.model.SeasonItem
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.core.model.VirtualFolder
import org.jellycopter.core.network.JellyfinApiService
import org.jellycopter.core.session.EncryptedSessionStorage
import java.util.concurrent.TimeUnit

/**
 * Image URL builder for Jellyfin server media items.
 *
 * Conforms to Jellyfin REST endpoints:
 * - /Items/{id}/Images/Primary
 * - /Items/{id}/Images/Backdrop
 */
object JellyfinImageUrlBuilder {
    fun buildPrimaryImageUrl(
        serverUrl: String,
        itemId: String,
        fillWidth: Int? = null,
        quality: Int? = null
    ): String {
        val cleanId = itemId.removePrefix("series:").removePrefix("channel:").trim()
        val base = "${serverUrl.trim().removeSuffix("/")}/Items/$cleanId/Images/Primary"
        val params = mutableListOf<String>()
        if (fillWidth != null) params.add("fillWidth=$fillWidth")
        if (quality != null) params.add("quality=$quality")
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    fun buildBackdropImageUrl(
        serverUrl: String,
        itemId: String,
        fillWidth: Int? = null,
        quality: Int? = null
    ): String {
        val cleanId = itemId.removePrefix("series:").removePrefix("channel:").trim()
        val base = "${serverUrl.trim().removeSuffix("/")}/Items/$cleanId/Images/Backdrop"
        val params = mutableListOf<String>()
        if (fillWidth != null) params.add("fillWidth=$fillWidth")
        if (quality != null) params.add("quality=$quality")
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }
}

/**
 * Contract for querying Jellyfin media libraries, virtual folders, series, seasons, and episodes.
 */
interface MediaRepository {
    suspend fun getVirtualFolders(): List<VirtualFolder>
    suspend fun getSeriesInFolder(folderId: String): List<SeriesDetail>
    suspend fun getAllSeries(): List<SeriesDetail> = emptyList()
    suspend fun getSeriesDetail(seriesId: String): SeriesDetail?
    suspend fun getNextUpForSeries(seriesId: String): EpisodeItem?
    suspend fun getFirstEpisodeForSeries(seriesId: String): EpisodeItem?
    suspend fun getAllEpisodesForSeries(seriesId: String): List<EpisodeItem>
    suspend fun getSeasonsForSeries(seriesId: String): List<SeasonItem>
    suspend fun getEpisodesForSeason(seriesId: String, seasonId: String): List<EpisodeItem>
    fun buildPrimaryImageUrl(itemId: String): String?
    fun buildBackdropImageUrl(itemId: String): String?
}

/**
 * Default implementation of [MediaRepository] integrating with live Jellyfin server
 * via OkHttp REST endpoints, with robust fallback to a rich offline mock dataset.
 */
class DefaultMediaRepository(
    private val sessionStorage: EncryptedSessionStorage? = null,
    private val apiService: JellyfinApiService? = null,
    private val okHttpClient: OkHttpClient? = null,
    private val json: Json? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaRepository {

    private val client: OkHttpClient by lazy {
        okHttpClient ?: OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val jsonParser: Json by lazy {
        json ?: Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    override fun buildPrimaryImageUrl(itemId: String): String? {
        val cleanId = itemId.removePrefix("series:").removePrefix("channel:").trim()
        val session = sessionStorage?.getSession() ?: return MockMediaDataset.getSeriesPoster(cleanId)
        return JellyfinImageUrlBuilder.buildPrimaryImageUrl(session.serverUrl, cleanId)
    }

    override fun buildBackdropImageUrl(itemId: String): String? {
        val cleanId = itemId.removePrefix("series:").removePrefix("channel:").trim()
        val session = sessionStorage?.getSession() ?: return MockMediaDataset.getSeriesBackdrop(cleanId)
        return JellyfinImageUrlBuilder.buildBackdropImageUrl(session.serverUrl, cleanId)
    }

    override suspend fun getVirtualFolders(): List<VirtualFolder> = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Users/${session.userId}/Views"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    if (items != null && items.isNotEmpty()) {
                        val folders = items.map { element ->
                            val obj = element.jsonObject
                            VirtualFolder(
                                id = obj["Id"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                                name = obj["Name"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: "Library",
                                collectionType = obj["CollectionType"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["collectionType"]?.jsonPrimitive?.contentOrNull
                            )
                        }
                        android.util.Log.d("JELLY_DEBUG", "getVirtualFolders: ${folders.map { "${it.name} (id=${it.id}, type=${it.collectionType})" }}")
                        return@withContext folders
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JELLY_DEBUG", "getVirtualFolders parse error", e)
                    // Fall back to mock dataset on parse error
                }
            } else {
                android.util.Log.w("JELLY_DEBUG", "getVirtualFolders executeGet returned null from $url (token len=${session.accessToken.length})")
            }
        }
        MockMediaDataset.virtualFolders
    }

    override suspend fun getSeriesInFolder(folderId: String): List<SeriesDetail> = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Users/${session.userId}/Items" +
                    "?parentId=$folderId&includeItemTypes=Series,Movie&recursive=true" +
                    "&fields=Overview,PrimaryImageAspectRatio,CommunityRating,PremiereDate,ProductionYear,ProviderIds,ChildCount"
            val responseBody = executeGet(url, session.accessToken)
            android.util.Log.d("JELLY_DEBUG", "getSeriesInFolder folderId=$folderId url=$url responseLen=${responseBody?.length ?: 0}")
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    android.util.Log.d("JELLY_DEBUG", "getSeriesInFolder folderId=$folderId itemsCount=${items?.size ?: 0} sampleTypes=${items?.take(5)?.map { it.jsonObject["Type"]?.jsonPrimitive?.contentOrNull }}")
                    if (items != null && items.isNotEmpty()) {
                        return@withContext items.map { element ->
                            val obj = element.jsonObject
                            val seriesId = obj["Id"]?.jsonPrimitive?.contentOrNull
                                ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            val seriesName = obj["Name"]?.jsonPrimitive?.contentOrNull
                                ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            val overview = obj["Overview"]?.jsonPrimitive?.contentOrNull
                            val rating = obj["CommunityRating"]?.jsonPrimitive?.floatOrNull
                            val year = obj["ProductionYear"]?.jsonPrimitive?.intOrNull
                                ?: parseYearFromDate(obj["PremiereDate"]?.jsonPrimitive?.contentOrNull)
                            val providerIds = obj["ProviderIds"]?.jsonObject ?: obj["providerIds"]?.jsonObject
                            val tmdbId = providerIds?.get("Tmdb")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: providerIds?.get("tmdb")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                            SeriesDetail(
                                id = seriesId,
                                name = seriesName,
                                overview = overview,
                                posterUrl = JellyfinImageUrlBuilder.buildPrimaryImageUrl(session.serverUrl, seriesId),
                                backdropUrl = JellyfinImageUrlBuilder.buildBackdropImageUrl(session.serverUrl, seriesId),
                                communityRating = rating,
                                premiereYear = year,
                                seasons = emptyList(),
                                nextUpEpisode = null,
                                tmdbId = tmdbId
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Fall through to mock dataset
                }
            }
        }
        MockMediaDataset.getSeriesForFolder(folderId)
    }

    override suspend fun getAllSeries(): List<SeriesDetail> = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Users/${session.userId}/Items" +
                    "?includeItemTypes=Series&recursive=true&sortBy=SortName" +
                    "&fields=Overview,PrimaryImageAspectRatio,CommunityRating,PremiereDate,ProductionYear,ProviderIds,ChildCount"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    if (items != null && items.isNotEmpty()) {
                        return@withContext items.map { element ->
                            val obj = element.jsonObject
                            val seriesId = obj["Id"]?.jsonPrimitive?.contentOrNull
                                ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            val seriesName = obj["Name"]?.jsonPrimitive?.contentOrNull
                                ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            val overview = obj["Overview"]?.jsonPrimitive?.contentOrNull
                            val rating = obj["CommunityRating"]?.jsonPrimitive?.floatOrNull
                            val year = obj["ProductionYear"]?.jsonPrimitive?.intOrNull
                                ?: parseYearFromDate(obj["PremiereDate"]?.jsonPrimitive?.contentOrNull)
                            val providerIds = obj["ProviderIds"]?.jsonObject ?: obj["providerIds"]?.jsonObject
                            val tmdbId = providerIds?.get("Tmdb")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: providerIds?.get("tmdb")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                            SeriesDetail(
                                id = seriesId,
                                name = seriesName,
                                overview = overview,
                                posterUrl = JellyfinImageUrlBuilder.buildPrimaryImageUrl(session.serverUrl, seriesId),
                                backdropUrl = JellyfinImageUrlBuilder.buildBackdropImageUrl(session.serverUrl, seriesId),
                                communityRating = rating,
                                premiereYear = year,
                                seasons = emptyList(),
                                nextUpEpisode = null,
                                tmdbId = tmdbId
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JELLY_DEBUG", "getAllSeries parse error", e)
                }
            }
        }
        MockMediaDataset.mockSeriesDetails.values.toList()
    }

    override suspend fun getSeriesDetail(seriesId: String): SeriesDetail? = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Users/${session.userId}/Items/$seriesId"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val obj = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val resolvedId = obj["Id"]?.jsonPrimitive?.contentOrNull
                        ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: seriesId
                    val seriesName = obj["Name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val overview = obj["Overview"]?.jsonPrimitive?.contentOrNull
                    val rating = obj["CommunityRating"]?.jsonPrimitive?.floatOrNull
                    val year = obj["ProductionYear"]?.jsonPrimitive?.intOrNull
                        ?: parseYearFromDate(obj["PremiereDate"]?.jsonPrimitive?.contentOrNull)
                    val providerIds = obj["ProviderIds"]?.jsonObject ?: obj["providerIds"]?.jsonObject
                    val tmdbId = providerIds?.get("Tmdb")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: providerIds?.get("tmdb")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                    val seasons = getSeasonsForSeries(resolvedId)
                    val nextUp = getNextUpForSeries(resolvedId)

                    return@withContext SeriesDetail(
                        id = resolvedId,
                        name = seriesName,
                        overview = overview,
                        posterUrl = JellyfinImageUrlBuilder.buildPrimaryImageUrl(session.serverUrl, resolvedId),
                        backdropUrl = JellyfinImageUrlBuilder.buildBackdropImageUrl(session.serverUrl, resolvedId),
                        communityRating = rating,
                        premiereYear = year,
                        seasons = seasons,
                        nextUpEpisode = nextUp,
                        tmdbId = tmdbId
                    )
                } catch (_: Exception) {
                    // Fall through to mock dataset
                }
            }
        }
        MockMediaDataset.getSeriesDetail(seriesId)
    }

    override suspend fun getNextUpForSeries(seriesId: String): EpisodeItem? = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Shows/NextUp" +
                    "?seriesId=$seriesId&userId=${session.userId}&fields=Overview,RunTimeTicks"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    if (items != null && items.isNotEmpty()) {
                        val cleanSeriesId = seriesId.removePrefix("series:").trim()
                        val matching = items.firstOrNull { element ->
                            val obj = element.jsonObject
                            val sId = (obj["SeriesId"]?.jsonPrimitive?.contentOrNull
                                ?: obj["seriesId"]?.jsonPrimitive?.contentOrNull
                                ?: "").removePrefix("series:").trim()
                            sId.equals(cleanSeriesId, ignoreCase = true)
                        }
                        if (matching != null) {
                            return@withContext parseEpisodeItem(matching.jsonObject, seriesId)
                        }
                        // Jellyfin returns global NextUp if seriesId is unknown or a movie.
                        // Never return an episode belonging to a different show!
                        return@withContext null
                    }
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }
        MockMediaDataset.getNextUp(seriesId)
    }

    override suspend fun getFirstEpisodeForSeries(seriesId: String): EpisodeItem? = withContext(ioDispatcher) {
        val seasons = getSeasonsForSeries(seriesId)
        val s1 = seasons.firstOrNull { it.seasonNumber == 1 } ?: seasons.firstOrNull()
        s1?.episodes?.firstOrNull() ?: getAllEpisodesForSeries(seriesId).firstOrNull()
    }

    override suspend fun getAllEpisodesForSeries(seriesId: String): List<EpisodeItem> = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Shows/$seriesId/Episodes" +
                    "?userId=${session.userId}&fields=Overview,RunTimeTicks"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    if (items != null && items.isNotEmpty()) {
                        return@withContext items.map { parseEpisodeItem(it.jsonObject, seriesId) }
                    }
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }
        MockMediaDataset.getAllEpisodes(seriesId)
    }

    override suspend fun getSeasonsForSeries(seriesId: String): List<SeasonItem> = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Shows/$seriesId/Seasons?userId=${session.userId}"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    if (items != null && items.isNotEmpty()) {
                        return@withContext items.map { element ->
                            val obj = element.jsonObject
                            val seasonId = obj["Id"]?.jsonPrimitive?.contentOrNull
                                ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            val seasonName = obj["Name"]?.jsonPrimitive?.contentOrNull
                                ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: "Season"
                            val seasonNumber = obj["IndexNumber"]?.jsonPrimitive?.intOrNull ?: 1
                            val episodes = getEpisodesForSeason(seriesId, seasonId)

                            SeasonItem(
                                id = seasonId,
                                seriesId = seriesId,
                                seasonNumber = seasonNumber,
                                name = seasonName,
                                episodes = episodes
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }
        MockMediaDataset.getSeasons(seriesId)
    }

    override suspend fun getEpisodesForSeason(seriesId: String, seasonId: String): List<EpisodeItem> = withContext(ioDispatcher) {
        val session = sessionStorage?.getSession()
        if (session != null) {
            val url = "${session.serverUrl.removeSuffix("/")}/Shows/$seriesId/Episodes" +
                    "?seasonId=$seasonId&userId=${session.userId}&fields=Overview,RunTimeTicks"
            val responseBody = executeGet(url, session.accessToken)
            if (responseBody != null) {
                try {
                    val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                    val items = root["Items"]?.jsonArray
                    if (items != null && items.isNotEmpty()) {
                        return@withContext items.map { parseEpisodeItem(it.jsonObject, seriesId, seasonId) }
                    }
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }
        MockMediaDataset.getEpisodesForSeason(seriesId, seasonId)
    }

    private fun executeGet(url: String, accessToken: String): String? {
        return try {
            val authHeader = JellyfinApiService.buildEmbyAuthHeader(token = accessToken)
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Emby-Token", accessToken)
                .addHeader("X-Emby-Authorization", authHeader)
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEpisodeItem(
        obj: kotlinx.serialization.json.JsonObject,
        seriesId: String,
        fallbackSeasonId: String? = null
    ): EpisodeItem {
        val id = obj["Id"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val name = obj["Name"]?.jsonPrimitive?.contentOrNull
            ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: "Episode"
        val sId = obj["SeriesId"]?.jsonPrimitive?.contentOrNull
            ?: obj["seriesId"]?.jsonPrimitive?.contentOrNull ?: seriesId
        val seriesName = obj["SeriesName"]?.jsonPrimitive?.contentOrNull
            ?: obj["seriesName"]?.jsonPrimitive?.contentOrNull ?: ""
        val seasonId = obj["SeasonId"]?.jsonPrimitive?.contentOrNull
            ?: obj["seasonId"]?.jsonPrimitive?.contentOrNull ?: fallbackSeasonId ?: ""
        val seasonNumber = obj["ParentIndexNumber"]?.jsonPrimitive?.intOrNull ?: 1
        val indexNumber = obj["IndexNumber"]?.jsonPrimitive?.intOrNull ?: 1
        val runTimeTicks = obj["RunTimeTicks"]?.jsonPrimitive?.longOrNull
            ?: (obj["RunTimeTicks"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L)
        val overview = obj["Overview"]?.jsonPrimitive?.contentOrNull
        val locationType = obj["LocationType"]?.jsonPrimitive?.contentOrNull
            ?: obj["locationType"]?.jsonPrimitive?.contentOrNull ?: "FileSystem"
        val isMissing = obj["IsMissing"]?.jsonPrimitive?.booleanOrNull
            ?: obj["isMissing"]?.jsonPrimitive?.booleanOrNull ?: false
        val mediaType = obj["MediaType"]?.jsonPrimitive?.contentOrNull
            ?: obj["mediaType"]?.jsonPrimitive?.contentOrNull ?: "Video"
        val userData = obj["UserData"]?.jsonObject ?: obj["userData"]?.jsonObject
        val playCount = userData?.get("PlayCount")?.jsonPrimitive?.doubleOrNull
            ?: userData?.get("playCount")?.jsonPrimitive?.doubleOrNull
            ?: (userData?.get("PlayCount")?.jsonPrimitive?.intOrNull
                ?: userData?.get("playCount")?.jsonPrimitive?.intOrNull)?.toDouble()
            ?: 0.0

        return EpisodeItem(
            id = id,
            name = name,
            seriesId = sId,
            seriesName = seriesName,
            seasonId = seasonId,
            seasonNumber = seasonNumber,
            indexNumber = indexNumber,
            runTimeTicks = runTimeTicks,
            overview = overview,
            playCount = playCount,
            isMissing = isMissing,
            locationType = locationType,
            mediaType = mediaType
        )
    }

    private fun parseYearFromDate(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            dateStr.take(4).toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Rich offline dataset providing realistic fallback media when the server is unreachable.
 */
object MockMediaDataset {

    val virtualFolders: List<VirtualFolder> = listOf(
        VirtualFolder("folder_shows", "TV Shows", "tvshows"),
        VirtualFolder("folder_animation", "Animation & Cartoons", "tvshows"),
        VirtualFolder("folder_movies", "Movies", "movies")
    )

    private val severanceEpisodesSeason1 = listOf(
        EpisodeItem("ep_sev_101", "Good News About Hell", "series_1", "Severance", "season_sev_1", 1, 1, 34200000000L, "Mark Scout leads a team at Lumon Industries whose employees have undergone a severance procedure."),
        EpisodeItem("ep_sev_102", "Half Loop", "series_1", "Severance", "season_sev_1", 1, 2, 31800000000L, "The team trains Helly on macrodata refinement; Mark meets with a mysterious former colleague."),
        EpisodeItem("ep_sev_103", "In Perpetuity", "series_1", "Severance", "season_sev_1", 1, 3, 32400000000L, "Mark takes the team on a field trip to the Perpetuity Wing; Cobel delivers a disciplinary measure."),
        EpisodeItem("ep_sev_104", "The You You Are", "series_1", "Severance", "season_sev_1", 1, 4, 30000000000L, "Helly undergoes a harsh session with Milchick; Mark finds a contraband self-help book."),
        EpisodeItem("ep_sev_105", "The Grim Barbarity of Optics and Design", "series_1", "Severance", "season_sev_1", 1, 5, 32400000000L, "Irving explores the Optics and Design department; Mark forms an unexpected alliance."),
        EpisodeItem("ep_sev_106", "Hide and Seek", "series_1", "Severance", "season_sev_1", 1, 6, 24000000000L, "Graner discovers signs of an external breach; Mark and Dylan investigate after-hours protocols."),
        EpisodeItem("ep_sev_107", "Defiant Jazz", "series_1", "Severance", "season_sev_1", 1, 7, 31200000000L, "Milchick activates a Music Dance Experience for the department, which spirals out of control."),
        EpisodeItem("ep_sev_108", "What's for Dinner?", "series_1", "Severance", "season_sev_1", 1, 8, 25800000000L, "The team prepares for the Overtime Contingency; Mark faces an unexpected revelation outside."),
        EpisodeItem("ep_sev_109", "The We We Are", "series_1", "Severance", "season_sev_1", 1, 9, 27000000000L, "The macrodata team awakens in their outie bodies and races to expose Lumon's secrets.")
    )

    private val severanceEpisodesSeason2 = listOf(
        EpisodeItem("ep_sev_201", "Hello Lumon", "series_1", "Severance", "season_sev_2", 2, 1, 33000000000L, "The aftermath of the Overtime Contingency sends shockwaves through the severed floor."),
        EpisodeItem("ep_sev_202", "Reintegration Protocol", "series_1", "Severance", "season_sev_2", 2, 2, 31500000000L, "Mark investigates rumors of a permanent reversal to the severance barrier.")
    )

    private val slowHorsesEpisodesSeason1 = listOf(
        EpisodeItem("ep_sh_101", "Failure's Contagious", "series_2", "Slow Horses", "season_sh_1", 1, 1, 30000000000L, "After a botched training operation, MI5 agent River Cartwright is exiled to Slough House under Jackson Lamb."),
        EpisodeItem("ep_sh_102", "Work Drinks", "series_2", "Slow Horses", "season_sh_1", 1, 2, 28800000000L, "River and Sid join forces to investigate a suspicious journalist connected to Regent's Park.")
    )

    private val theBearEpisodesSeason1 = listOf(
        EpisodeItem("ep_bear_101", "System", "series_3", "The Bear", "season_bear_1", 1, 1, 18000000000L, "Carmen Berzatto attempts to modernize his deceased brother's chaotic Chicago beef sandwich joint."),
        EpisodeItem("ep_bear_102", "Hands", "series_3", "The Bear", "season_bear_1", 1, 2, 19200000000L, "A surprise health inspection reveals deep structural issues and unpaid purveyor invoices.")
    )

    private val fargoEpisodesSeason1 = listOf(
        EpisodeItem("ep_fargo_101", "The Crocodile's Dilemma", "series_4", "Fargo", "season_fargo_1", 1, 1, 36000000000L, "A manipulative drifter meets a downtrodden insurance salesman in Bemidji, Minnesota.")
    )

    private val successionEpisodesSeason1 = listOf(
        EpisodeItem("ep_succ_101", "Celebration", "series_5", "Succession", "season_succ_1", 1, 1, 36000000000L, "On his 80th birthday, media titan Logan Roy shocks his children by refusing to name a successor.")
    )

    private val futuramaEpisodesSeason1 = listOf(
        EpisodeItem("ep_fut_101", "Space Pilot 3000", "series_futurama", "Futurama", "season_fut_1", 1, 1, 13800000000L, "Philip J. Fry accidentally freezes himself in cryogenic suspension on New Year's Eve 1999 and awakes in 2999.")
    )

    private val cowboyBebopEpisodesSeason1 = listOf(
        EpisodeItem("ep_bebop_101", "Asteroid Blues", "series_cowboy_bebop", "Cowboy Bebop", "season_bebop_1", 1, 1, 14400000000L, "Spike Spiegel and Jet Black pursue a drug-smuggling syndicate fugitive on the asteroid colony of Tijuana.")
    )

    val mockSeriesDetails: Map<String, SeriesDetail> = mapOf(
        "series_1" to SeriesDetail(
            id = "series_1",
            name = "Severance",
            overview = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives.",
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1280&q=80",
            communityRating = 8.7f,
            premiereYear = 2022,
            seasons = listOf(
                SeasonItem("season_sev_1", "series_1", 1, "Season 1", severanceEpisodesSeason1),
                SeasonItem("season_sev_2", "series_1", 2, "Season 2", severanceEpisodesSeason2)
            ),
            nextUpEpisode = severanceEpisodesSeason1[1], // S1E2 "Half Loop"
            tmdbId = 97951
        ),
        "series_2" to SeriesDetail(
            id = "series_2",
            name = "Slow Horses",
            overview = "Follows a dysfunctional team of MI5 agents and their obnoxious boss, the notorious Jackson Lamb.",
            posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1280&q=80",
            communityRating = 8.2f,
            premiereYear = 2022,
            seasons = listOf(
                SeasonItem("season_sh_1", "series_2", 1, "Season 1", slowHorsesEpisodesSeason1),
                SeasonItem("season_sh_2", "series_2", 2, "Season 2", emptyList()),
                SeasonItem("season_sh_3", "series_2", 3, "Season 3", emptyList()),
                SeasonItem("season_sh_4", "series_2", 4, "Season 4", emptyList())
            ),
            nextUpEpisode = slowHorsesEpisodesSeason1[0],
            tmdbId = 119243
        ),
        "series_3" to SeriesDetail(
            id = "series_3",
            name = "The Bear",
            overview = "A young chef from the fine dining world returns home to Chicago to run his family Italian beef sandwich shop.",
            posterUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=1280&q=80",
            communityRating = 8.6f,
            premiereYear = 2022,
            seasons = listOf(
                SeasonItem("season_bear_1", "series_3", 1, "Season 1", theBearEpisodesSeason1),
                SeasonItem("season_bear_2", "series_3", 2, "Season 2", emptyList()),
                SeasonItem("season_bear_3", "series_3", 3, "Season 3", emptyList())
            ),
            nextUpEpisode = theBearEpisodesSeason1[0],
            tmdbId = 136315
        ),
        "series_4" to SeriesDetail(
            id = "series_4",
            name = "Fargo",
            overview = "Various chronicles of deception, intrigue and murder in and around frozen Minnesota.",
            posterUrl = "https://images.unsplash.com/photo-1517411032315-54ef2cb783bb?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1483921020237-2ff51e8e4b22?w=1280&q=80",
            communityRating = 8.9f,
            premiereYear = 2014,
            seasons = listOf(
                SeasonItem("season_fargo_1", "series_4", 1, "Season 1", fargoEpisodesSeason1)
            ),
            nextUpEpisode = fargoEpisodesSeason1[0],
            tmdbId = 57243
        ),
        "series_5" to SeriesDetail(
            id = "series_5",
            name = "Succession",
            overview = "The Roy family is known for controlling the biggest media and entertainment company in the world. However, their world changes when their aging father steps down.",
            posterUrl = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=1280&q=80",
            communityRating = 8.9f,
            premiereYear = 2018,
            seasons = listOf(
                SeasonItem("season_succ_1", "series_5", 1, "Season 1", successionEpisodesSeason1)
            ),
            nextUpEpisode = successionEpisodesSeason1[0],
            tmdbId = 76331
        ),
        "series_futurama" to SeriesDetail(
            id = "series_futurama",
            name = "Futurama",
            overview = "Philip J. Fry, a pizza delivery boy, is accidentally frozen in 1999 and thawed out in 2999.",
            posterUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1280&q=80",
            communityRating = 8.5f,
            premiereYear = 1999,
            seasons = listOf(
                SeasonItem("season_fut_1", "series_futurama", 1, "Season 1", futuramaEpisodesSeason1)
            ),
            nextUpEpisode = futuramaEpisodesSeason1[0],
            tmdbId = 615
        ),
        "series_cowboy_bebop" to SeriesDetail(
            id = "series_cowboy_bebop",
            name = "Cowboy Bebop",
            overview = "In 2071, bounty hunters Spike Spiegel and Jet Black drift through the solar system on the spaceship Bebop.",
            posterUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1280&q=80",
            communityRating = 8.9f,
            premiereYear = 1998,
            seasons = listOf(
                SeasonItem("season_bebop_1", "series_cowboy_bebop", 1, "Season 1", cowboyBebopEpisodesSeason1)
            ),
            nextUpEpisode = cowboyBebopEpisodesSeason1[0],
            tmdbId = 2996
        )
    )

    private val mockMovieDetails: Map<String, SeriesDetail> = mapOf(
        "movie_dune2" to SeriesDetail(
            id = "movie_dune2",
            name = "Dune: Part Two",
            overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
            posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1280&q=80",
            communityRating = 8.6f,
            premiereYear = 2024,
            seasons = emptyList(),
            nextUpEpisode = null,
            tmdbId = 693134
        ),
        "movie_bladerunner" to SeriesDetail(
            id = "movie_bladerunner",
            name = "Blade Runner 2049",
            overview = "Young Blade Runner K's discovery of a long-buried secret leads him to track down former Blade Runner Rick Deckard.",
            posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1280&q=80",
            communityRating = 8.0f,
            premiereYear = 2017,
            seasons = emptyList(),
            nextUpEpisode = null,
            tmdbId = 335984
        ),
        "movie_interstellar" to SeriesDetail(
            id = "movie_interstellar",
            name = "Interstellar",
            overview = "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot is tasked to pilot a spacecraft to find a new planet.",
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1280&q=80",
            communityRating = 8.7f,
            premiereYear = 2014,
            seasons = emptyList(),
            nextUpEpisode = null,
            tmdbId = 157336
        )
    )

    fun getSeriesForFolder(folderId: String): List<SeriesDetail> {
        return when (folderId) {
            "folder_animation" -> listOf(
                mockSeriesDetails["series_futurama"]!!,
                mockSeriesDetails["series_cowboy_bebop"]!!
            )
            "folder_movies" -> mockMovieDetails.values.toList()
            else -> listOf(
                mockSeriesDetails["series_1"]!!,
                mockSeriesDetails["series_2"]!!,
                mockSeriesDetails["series_3"]!!,
                mockSeriesDetails["series_4"]!!,
                mockSeriesDetails["series_5"]!!
            )
        }
    }

    fun getSeriesDetail(seriesId: String): SeriesDetail? {
        return mockSeriesDetails[seriesId]
            ?: mockMovieDetails[seriesId]
            ?: mockSeriesDetails.values.firstOrNull { it.name.equals(seriesId, ignoreCase = true) }
            ?: mockMovieDetails.values.firstOrNull { it.name.equals(seriesId, ignoreCase = true) }
    }

    fun getNextUp(seriesId: String): EpisodeItem? {
        return getSeriesDetail(seriesId)?.nextUpEpisode
    }

    fun getSeasons(seriesId: String): List<SeasonItem> {
        return getSeriesDetail(seriesId)?.seasons ?: emptyList()
    }

    fun getEpisodesForSeason(seriesId: String, seasonId: String): List<EpisodeItem> {
        val series = getSeriesDetail(seriesId) ?: return emptyList()
        val season = series.seasons.firstOrNull { it.id == seasonId }
            ?: series.seasons.firstOrNull { "season_${it.seasonNumber}" == seasonId }
        return season?.episodes ?: emptyList()
    }

    fun getAllEpisodes(seriesId: String): List<EpisodeItem> {
        val series = getSeriesDetail(seriesId) ?: return emptyList()
        return series.seasons.flatMap { it.episodes }
    }

    fun getSeriesPoster(itemId: String): String? {
        val cleanId = itemId.removePrefix("series:").removePrefix("channel:").trim()
        return mockSeriesDetails[cleanId]?.posterUrl
            ?: mockMovieDetails[cleanId]?.posterUrl
            ?: mockSeriesDetails.values.firstOrNull { it.name.equals(cleanId, ignoreCase = true) }?.posterUrl
            ?: mockMovieDetails.values.firstOrNull { it.name.equals(cleanId, ignoreCase = true) }?.posterUrl
    }

    fun getSeriesBackdrop(itemId: String): String? {
        val cleanId = itemId.removePrefix("series:").removePrefix("channel:").trim()
        return mockSeriesDetails[cleanId]?.backdropUrl
            ?: mockMovieDetails[cleanId]?.backdropUrl
            ?: mockSeriesDetails.values.firstOrNull { it.name.equals(cleanId, ignoreCase = true) }?.backdropUrl
            ?: mockMovieDetails.values.firstOrNull { it.name.equals(cleanId, ignoreCase = true) }?.backdropUrl
    }
}
