package org.jellycopter

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import org.jellycopter.core.session.EncryptedSessionStorage
import org.jellycopter.core.session.UserSession
import org.jellycopter.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.java.KoinJavaComponent

/**
 * Main application class for Jellycopter.
 * Initializes Koin dependency injection and Coil ImageLoader on application startup.
 */
class JellycopterApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@JellycopterApplication)
            modules(appModules)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val baseOkHttp = try {
            org.koin.java.KoinJavaComponent.get(OkHttpClient::class.java)
        } catch (_: Exception) {
            OkHttpClient.Builder().build()
        }

        val imageOkHttp = baseOkHttp.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()

                val sessionStorage: EncryptedSessionStorage? = try {
                    KoinJavaComponent.get(EncryptedSessionStorage::class.java)
                } catch (_: Exception) { null }
                val session: UserSession? = try { sessionStorage?.getSession() } catch (_: Exception) { null }

                val reqUrl = req.url.toString()
                val serverUrl = session?.serverUrl?.removeSuffix("/")
                val isJellyfinRequest = serverUrl != null && reqUrl.startsWith(serverUrl)

                if (session != null && isJellyfinRequest) {
                    val mediaAuthHeader = """MediaBrowser Client="Jellycopter", Device="AndroidTV", DeviceId="jellycopter-android", Version="1.0.0", Token="${session.accessToken}""""
                    builder.header("Authorization", mediaAuthHeader)
                    builder.header("X-Emby-Authorization", mediaAuthHeader)
                } else {
                    // Fallback: mirror X-Emby-Authorization to Authorization if present
                    val embyAuth = req.header("X-Emby-Authorization")
                    if (embyAuth != null && req.header("Authorization") == null) {
                        builder.header("Authorization", embyAuth)
                    }
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttp)
            .crossfade(150)
            .allowHardware(true)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
