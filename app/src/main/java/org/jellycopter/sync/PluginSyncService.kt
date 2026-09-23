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
    }
) {

    private val _isPluginAvailable = MutableStateFlow<Boolean?>(null)
    val isPluginAvailable: StateFlow<Boolean?> = _isPluginAvailable.asStateFlow()

    /**
     * Probes the server for the Jellyfin Jellycopter companion plugin (/Jellycopter/Ping).
     * Returns true if available (200 OK), false otherwise (404, offline, or error).
     */
    suspend fun probePlugin(): Boolean {
        return try {
            val response = pluginApi.ping()
            if (response.isSuccessful && response.body() != null) {
                _isPluginAvailable.value = true
                true
            } else {
                _isPluginAvailable.value = false
                false
            }
        } catch (e: Exception) {
            _isPluginAvailable.value = false
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
                val dto = DeckSyncDto(
                    deckId = local.deckId,
                    seriesId = local.seriesId,
                    channelId = local.channelId,
                    cycleNumber = local.cycleNumber,
                    exhaustedItemIds = exhaustedIds,
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
        fun create(
            baseUrl: String,
            deckDao: DeckDao,
            achievementDao: AchievementDao,
            apiKey: String? = null
        ): PluginSyncService {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val okHttpClientBuilder = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)

            if (!apiKey.isNullOrBlank()) {
                okHttpClientBuilder.addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("X-Emby-Token", apiKey)
                        .build()
                    chain.proceed(req)
                }
            }

            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                coerceInputValues = true
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClientBuilder.build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(PluginSyncApi::class.java)
            return PluginSyncService(api, deckDao, achievementDao, json)
        }
    }
}
