package org.jellycopter.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.jellycopter.core.database.AchievementDao
import org.jellycopter.core.database.AchievementEntity
import org.jellycopter.core.database.DeckDao
import org.jellycopter.core.database.DeckEntity
import org.jellycopter.core.database.DeckProgressEntity
import org.jellycopter.sync.model.AchievementSyncDto
import org.jellycopter.sync.model.DeckSyncDto
import org.jellycopter.sync.model.SyncResult
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Companion Plugin Synchronization Service.
 *
 * Implements R5 & M5: Handshake probe to /Jellycopter/Ping.
 * If present (HTTP 200), synchronizes decks and achievements via set-union logic.
 * If absent (HTTP 404 / connection error), seamlessly defaults to local Room DB with zero loss of feature functionality.
 */
class PluginSyncService(
    private val pluginApi: PluginSyncApi,
    private val deckDao: DeckDao,
    private val achievementDao: AchievementDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    },
    private val sessionProvider: (() -> org.jellycopter.core.session.UserSession?)? = null
) {

    private val _isPluginAvailable = MutableStateFlow<Boolean?>(null)
    val isPluginAvailable: StateFlow<Boolean?> = _isPluginAvailable.asStateFlow()

    private val _serverAudioDrcActive = MutableStateFlow(false)
    val serverAudioDrcActive: StateFlow<Boolean> = _serverAudioDrcActive.asStateFlow()

    private val _serverDownmixAlgorithm = MutableStateFlow<String?>(null)
    val serverDownmixAlgorithm: StateFlow<String?> = _serverDownmixAlgorithm.asStateFlow()

    /**
     * Probes the server for the Jellyfin Jellycopter companion plugin (/Jellycopter/Ping).
     * Returns true if available (200 OK), false otherwise (404, offline, or error).
     */
    suspend fun probePlugin(): Boolean {
        return try {
            val response = pluginApi.ping()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                _isPluginAvailable.value = true
                _serverAudioDrcActive.value = body.serverAudioDrcActive
                _serverDownmixAlgorithm.value = body.downmixAlgorithm
                true
            } else {
                _isPluginAvailable.value = false
                _serverAudioDrcActive.value = false
                _serverDownmixAlgorithm.value = null
                false
            }
        } catch (e: Exception) {
            _isPluginAvailable.value = false
            _serverAudioDrcActive.value = false
            _serverDownmixAlgorithm.value = null
            false
        }
    }

    /**
     * Synchronizes shuffle decks with the companion plugin.
     * Uses set-union cycle merging. If plugin is not available, defaults to Room DB.
     */
    suspend fun syncDecks(localDecks: List<DeckEntity>, userId: String? = null): SyncResult {
        if (_isPluginAvailable.value == null) {
            probePlugin()
        }

        if (_isPluginAvailable.value != true) {
            return SyncResult.FallbackLocal(
                "Companion plugin not available; defaulting to local Room DB."
            )
        }

        return try {
            var syncedCount = 0
            for (local in localDecks) {
                val exhaustedIds = parseJsonList(local.exhaustedCardIdsJson)
                val progress = deckDao.getProgress(local.deckId)
                val lastPlayedId = progress?.currentEpisodeId?.takeIf { it.isNotBlank() }
                val lastTicks = (progress?.midwayResumeMs ?: 0L) * 10_000L

                val dto = DeckSyncDto(
                    deckId = local.deckId,
                    seriesId = local.seriesId,
                    channelId = local.channelId,
                    cycleNumber = local.cycleNumber,
                    exhaustedItemIds = exhaustedIds,
                    lastPlayedItemId = lastPlayedId,
                    lastResumePositionTicks = lastTicks,
                    updatedAtUtc = formatIsoTimestamp(local.updatedAt)
                )

                val response = pluginApi.upsertDeck(dto, userId)
                if (response.isSuccessful && response.body() != null) {
                    val serverDeck = response.body()!!
                    val mergedExhausted = (exhaustedIds + serverDeck.exhaustedItemIds).distinct()
                    val mergedCycle = maxOf(local.cycleNumber, serverDeck.CycleNumber())
                    val updatedDeck = local.copy(
                        cycleNumber = mergedCycle,
                        exhaustedCardIdsJson = json.encodeToString(mergedExhausted),
                        syncedAtUtc = currentIsoTimestamp(),
                        updatedAt = System.currentTimeMillis()
                    )
                    deckDao.upsertDeck(updatedDeck)

                    // If server deck provides resume position, synchronize progress
                    if (!serverDeck.lastPlayedItemId.isNullOrBlank() && serverDeck.lastResumePositionTicks > 0) {
                        val serverMs = serverDeck.lastResumePositionTicks / 10_000L
                        if (progress == null || (serverMs > 0 && serverMs != progress.midwayResumeMs)) {
                            deckDao.upsertProgress(
                                DeckProgressEntity(
                                    deckId = local.deckId,
                                    currentCardId = serverDeck.lastPlayedItemId,
                                    currentEpisodeId = serverDeck.lastPlayedItemId,
                                    currentIndex = progress?.currentIndex ?: 0,
                                    midwayResumeMs = serverMs,
                                    durationMs = progress?.durationMs ?: 0L,
                                    lastWatchedTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    syncedCount++
                }
            }

            // Pull any remote decks missing locally
            val remoteListResponse = pluginApi.getDecks(userId)
            if (remoteListResponse.isSuccessful && remoteListResponse.body() != null) {
                val remoteDecks = remoteListResponse.body()!!
                for (r in remoteDecks) {
                    val existing = deckDao.getDeckById(r.deckId)
                    if (existing == null) {
                        val newEntity = DeckEntity(
                            deckId = r.deckId,
                            name = r.seriesId ?: r.channelId ?: r.deckId,
                            seriesId = r.seriesId,
                            channelId = r.channelId,
                            cycleNumber = r.cycleNumber,
                            exhaustedCardIdsJson = json.encodeToString(r.exhaustedItemIds),
                            syncedAtUtc = currentIsoTimestamp()
                        )
                        deckDao.upsertDeck(newEntity)

                        if (!r.lastPlayedItemId.isNullOrBlank() && r.lastResumePositionTicks > 0) {
                            deckDao.upsertProgress(
                                DeckProgressEntity(
                                    deckId = r.deckId,
                                    currentCardId = r.lastPlayedItemId,
                                    currentEpisodeId = r.lastPlayedItemId,
                                    currentIndex = 0,
                                    midwayResumeMs = r.lastResumePositionTicks / 10_000L,
                                    durationMs = 0L,
                                    lastWatchedTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            SyncResult.Success("Decks synced with companion plugin", syncedCount)
        } catch (e: Exception) {
            _isPluginAvailable.value = false
            SyncResult.FallbackLocal("Sync failed (${e.message}); fallback to local Room DB.")
        }
    }

    /**
     * Synchronizes achievements with the companion plugin.
     * Earliest unlock timestamp and max progress wins.
     */
    suspend fun syncAchievements(localAchievements: List<AchievementEntity>, userId: String? = null): SyncResult {
        if (_isPluginAvailable.value == null) {
            probePlugin()
        }

        if (_isPluginAvailable.value != true) {
            return SyncResult.FallbackLocal(
                "Companion plugin not available; defaulting to local Room DB achievements."
            )
        }

        return try {
            // Push local achievements to server
            val pushList = localAchievements.map {
                AchievementSyncDto(
                    id = it.id,
                    title = it.title,
                    unlockedAtUtc = it.unlockedAtUtc,
                    progress = it.progress,
                    maxProgress = it.maxProgress,
                    isUnlocked = it.isUnlocked
                )
            }
            pluginApi.upsertAchievements(pushList, userId)

            // Pull remote achievements and merge into Room DB
            val getResponse = pluginApi.getAchievements(userId)
            if (getResponse.isSuccessful && getResponse.body() != null) {
                val remoteList = getResponse.body()!!
                for (remote in remoteList) {
                    val existing = achievementDao.getAchievementById(remote.id)
                    if (existing != null) {
                        val isUnlocked = existing.isUnlocked || remote.isUnlocked
                        val existingUtc = existing.unlockedAtUtc?.takeIf { it.isNotBlank() }
                        val remoteUtc = remote.unlockedAtUtc?.takeIf { it.isNotBlank() }
                        val earliestUnlock = when {
                            existingUtc != null && remoteUtc != null -> {
                                if (existingUtc <= remoteUtc) existingUtc else remoteUtc
                            }
                            existingUtc != null -> existingUtc
                            remoteUtc != null -> remoteUtc
                            else -> null
                        }
                        val maxProg = maxOf(existing.progress, remote.progress)
                        val merged = existing.copy(
                            isUnlocked = isUnlocked,
                            unlockedAtUtc = earliestUnlock,
                            progress = maxProg,
                            updatedAt = System.currentTimeMillis()
                        )
                        achievementDao.upsertAchievement(merged)
                    }
                }
            }

            SyncResult.Success("Achievements synced with companion plugin", localAchievements.size)
        } catch (e: Exception) {
            _isPluginAvailable.value = false
            SyncResult.FallbackLocal("Achievement sync failed (${e.message}); fallback to local Room DB.")
        }
    }

    private fun parseJsonList(jsonStr: String): List<String> {
        return runCatching {
            json.decodeFromString<List<String>>(jsonStr)
        }.getOrDefault(emptyList())
    }

    private fun DeckSyncDto.CycleNumber(): Int = cycleNumber

    private fun currentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun formatIsoTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    companion object {
        fun createDynamic(
            sessionProvider: () -> org.jellycopter.core.session.UserSession?,
            deckDao: DeckDao,
            achievementDao: AchievementDao
        ): PluginSyncService {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                coerceInputValues = true
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val session = sessionProvider()
                    val reqBuilder = chain.request().newBuilder()
                    val token = session?.accessToken
                    if (!token.isNullOrBlank()) {
                        reqBuilder.header("X-Emby-Token", token)
                        val authHeader = org.jellycopter.core.network.JellyfinApiService.buildEmbyAuthHeader(token = token)
                        reqBuilder.header("X-Emby-Authorization", authHeader)
                        reqBuilder.header("Authorization", authHeader)
                    }

                    // Dynamically rewrite base URL if session provides a different server URL
                    val currentReq = reqBuilder.build()
                    val sessionUrl = session?.serverUrl?.trim()?.removeSuffix("/")
                    if (!sessionUrl.isNullOrBlank()) {
                        val uri = runCatching { java.net.URI.create(sessionUrl) }.getOrNull()
                        if (uri != null && uri.host != null) {
                            val newUrl = currentReq.url.newBuilder()
                                .scheme(uri.scheme ?: currentReq.url.scheme)
                                .host(uri.host)
                                .port(if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80)
                                .build()
                            return@addInterceptor chain.proceed(currentReq.newBuilder().url(newUrl).build())
                        }
                    }
                    chain.proceed(currentReq)
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("http://localhost:8096/")
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(PluginSyncApi::class.java)
            return PluginSyncService(api, deckDao, achievementDao, json, sessionProvider)
        }
    }
}
