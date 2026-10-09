package org.jellycopter.di

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.jellycopter.core.database.JellycopterDatabase
import org.jellycopter.core.network.JellyfinApiService
import org.jellycopter.core.repository.AchievementRepository
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.repository.ChannelRepository
import org.jellycopter.core.repository.DeckRepository
import org.jellycopter.core.repository.DefaultAuthRepository
import org.jellycopter.core.repository.DefaultMediaRepository
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.core.repository.RoomAchievementRepository
import org.jellycopter.core.repository.RoomChannelRepository
import org.jellycopter.core.repository.RoomDeckRepository
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.settings.SettingsRepository
import org.jellycopter.core.settings.SharedPreferencesSettingsRepository
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val storageModule = module {
    single { EncryptedSessionStorage(context = androidContext()) }
    single<SettingsRepository> { SharedPreferencesSettingsRepository(context = androidContext()) }
    single { JellycopterDatabase.getDatabase(androidContext()) }
    single { get<JellycopterDatabase>().deckDao() }
    single { get<JellycopterDatabase>().achievementDao() }
    single { get<JellycopterDatabase>().channelDao() }
}

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val embyAuth = original.header("X-Emby-Authorization")
                val auth = original.header("Authorization")
                val builder = original.newBuilder()
                if (embyAuth != null && auth == null) {
                    builder.header("Authorization", embyAuth)
                }
                chain.proceed(builder.build())
            }
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }
    single<JellyfinApiService> {
        get<Retrofit>().create(JellyfinApiService::class.java)
    }
    single {
        createJellyfin {
            context = androidContext()
            clientInfo = ClientInfo(name = "Jellycopter", version = "1.0.0")
            deviceInfo = DeviceInfo(id = "jellycopter-android", name = "Android Device")
        }
    }
}

val repositoryModule = module {
    single<AuthRepository> {
        DefaultAuthRepository(
            apiService = get(),
            sessionStorage = get()
        )
    }
    single<DeckRepository> {
        RoomDeckRepository(
            deckDao = get(),
            json = get()
        )
    }
    single<ChannelRepository> {
        RoomChannelRepository(
            channelDao = get()
        )
    }
    single<AchievementRepository> {
        RoomAchievementRepository(
            achievementDao = get()
        )
    }
    single<MediaRepository> {
        DefaultMediaRepository(
            sessionStorage = getOrNull(),
            apiService = getOrNull(),
            okHttpClient = getOrNull(),
            json = getOrNull()
        )
    }
}

val viewModelModule = module {
    viewModel { org.jellycopter.ui.viewmodel.LoginViewModel(authRepository = getOrNull()) }
    viewModel {
        org.jellycopter.ui.viewmodel.SettingsViewModel(
            settingsRepository = getOrNull(),
            authRepository = getOrNull(),
            pluginSyncService = getOrNull()
        )
    }
    viewModel {
        org.jellycopter.ui.viewmodel.MainViewModel(
            channelRepository = getOrNull(),
            deckRepository = getOrNull(),
            mediaRepository = getOrNull(),
            jellyseerrClient = getOrNull()
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
val playbackModule = module {
    single<org.jellycopter.shuffle.SmartShuffleEngine> {
        org.jellycopter.shuffle.DefaultSmartShuffleEngine(
            deckRepository = getOrNull()
        )
    }
    single {
        org.jellycopter.shuffle.WatchHistoryDecoupler(
            deckRepository = getOrNull()
        )
    }
    single {
        org.jellycopter.playback.PlaybackCoordinator(
            watchHistoryDecoupler = get(),
            deckRepository = get<DeckRepository>(),
            mediaRepository = get<MediaRepository>(),
            shuffleEngine = get<org.jellycopter.shuffle.SmartShuffleEngine>()
        )
    }
    single {
        val ctx = try { androidContext() } catch (_: Exception) { null }
        org.jellycopter.playback.ExoPlayerAdapter(
            context = ctx
        )
    }
    single {
        org.jellycopter.playback.PlaybackManager(
            primaryPlayer = get<org.jellycopter.playback.ExoPlayerAdapter>(),
            sessionStorage = getOrNull()
        )
    }
    single<org.jellycopter.playback.PlaybackEngine> {
        get<org.jellycopter.playback.PlaybackManager>()
    }
    single {
        org.jellycopter.telemetry.AchievementEngine(
            repository = get()
        )
    }
    single {
        val storage = getOrNull<EncryptedSessionStorage>()
        org.jellycopter.sync.PluginSyncService.createDynamic(
            sessionProvider = { storage?.getSession() },
            deckDao = get(),
            achievementDao = get()
        )
    }
    single {
        val settingsRepo = getOrNull<SettingsRepository>()
        org.jellycopter.discovery.JellyseerrClient.createDynamic(
            urlProvider = {
                val url = settingsRepo?.getJellyseerrUrl()
                if (!url.isNullOrBlank()) url else "http://localhost:5055"
            }
        )
    }
}

val appModules = listOf(
    storageModule,
    networkModule,
    repositoryModule,
    viewModelModule,
    playbackModule
)
