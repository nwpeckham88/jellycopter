package org.jellycopter.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.roundToInt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracted color swatches from poster artwork.
 */
data class ExtractedPalette(
    val dominantColor: Color = Color(0xFF1E293B),
    val vibrantColor: Color = Color(0xFF38BDF8),
    val darkVibrantColor: Color = Color(0xFF0F172A),
    val lightVibrantColor: Color = Color(0xFF7DD3FC)
)

/**
 * Dynamic Adaptive Palette Extractor.
 *
 * Efficiently downsamples poster images to 120x180 px thumbnails for
 * rapid (<15ms) dominant and vibrant color extraction.
 * Offloads heavy extraction to [Dispatchers.Default] and caches swatches
 * in an in-memory LRU cache to prevent UI thread jank.
 */
object PaletteExtractor {

    const val TARGET_WIDTH = 120
    const val TARGET_HEIGHT = 180
    private const val DEFAULT_CACHE_MAX_SIZE = 64

    /**
     * In-memory LRU cache for extracted color palettes.
     */
    class PaletteMemoryLruCache(private val maxSize: Int = DEFAULT_CACHE_MAX_SIZE) {
        private val map = object : LinkedHashMap<Int, ExtractedPalette>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ExtractedPalette>?): Boolean {
                return size > maxSize
            }
        }

        @Synchronized
        fun get(key: Int): ExtractedPalette? = map[key]

        @Synchronized
        fun put(key: Int, value: ExtractedPalette): ExtractedPalette? = map.put(key, value)

        @Synchronized
        fun clear() = map.clear()

        @Synchronized
        fun size(): Int = map.size
    }

    val cache = PaletteMemoryLruCache()

    /**
     * Calculates optimal downsampled dimensions preserving aspect ratio within 120x180.
     */
    fun downsampleDimensions(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int = TARGET_WIDTH,
        targetHeight: Int = TARGET_HEIGHT
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return Pair(targetWidth, targetHeight)
        val widthRatio = targetWidth.toFloat() / srcWidth
        val heightRatio = targetHeight.toFloat() / srcHeight
        val scale = minOf(widthRatio, heightRatio)
        val dstWidth = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val dstHeight = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        return Pair(dstWidth, dstHeight)
    }

    /**
     * Asynchronously extracts an [ExtractedPalette] from an Android [Bitmap] on [Dispatchers.Default],
     * utilizing an in-memory LRU cache to prevent UI thread frame drops.
     */
    suspend fun extractFromBitmap(bitmap: Bitmap): ExtractedPalette = withContext(Dispatchers.Default) {
        extractFromBitmapSync(bitmap)
    }

    /**
     * Extracts an ExtractedPalette from an Android Bitmap synchronously with LRU caching.
     * Downsamples to 120x180 px before running AndroidX Palette generation.
     */
    fun extractFromBitmapSync(bitmap: Bitmap): ExtractedPalette {
        val cacheKey = bitmap.hashCode()
        cache.get(cacheKey)?.let { return it }

        val (dstW, dstH) = downsampleDimensions(bitmap.width, bitmap.height)
        val scaled = if (bitmap.width != dstW || bitmap.height != dstH) {
            Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
        } else {
            bitmap
        }

        val palette = Palette.from(scaled)
            .maximumColorCount(16)
            .generate()

        val dominant = palette.dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF1E293B)
        val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: dominant
        val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF0F172A)
        val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF7DD3FC)

        val result = ExtractedPalette(
            dominantColor = dominant,
            vibrantColor = vibrant,
            darkVibrantColor = darkVibrant,
            lightVibrantColor = lightVibrant
        )
        cache.put(cacheKey, result)
        return result
    }

    /**
     * Asynchronously extracts palette from raw pixels on [Dispatchers.Default] with LRU caching.
     */
    suspend fun extractFromPixelsAsync(pixels: IntArray, width: Int, height: Int): ExtractedPalette =
        withContext(Dispatchers.Default) {
            extractFromPixels(pixels, width, height)
        }

    /**
     * Algorithmic pixel-based palette extraction that runs cleanly without Android framework runtime.
     * Computes dominant color, highest saturation vibrant color, and dark vibrant color with LRU caching.
     */
    fun extractFromPixels(pixels: IntArray, width: Int, height: Int): ExtractedPalette {
        if (pixels.isEmpty()) return ExtractedPalette()

        val cacheKey = pixels.contentHashCode() xor (width * 31 + height)
        cache.get(cacheKey)?.let { return it }

        val colorFrequency = mutableMapOf<Int, Int>()
        val bucketRepresentative = mutableMapOf<Int, Int>()
        var bestVibrant = pixels[0]
        var maxSat = -1.0f

        var bestDarkVibrant = pixels[0]
        var minLumDark = 1.0f

        for (color in pixels) {
            val a = (color ushr 24) and 0xFF
            if (a < 128) continue // Ignore transparent pixels

            val r = ((color ushr 16) and 0xFF) / 255.0f
            val g = ((color ushr 8) and 0xFF) / 255.0f
            val b = (color and 0xFF) / 255.0f

            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min

            val lum = (max + min) / 2.0f
            val sat = if (delta == 0.0f) 0.0f else delta / (1.0f - kotlin.math.abs(2.0f * lum - 1.0f))

            // Frequency for dominant color (quantized)
            val quantized = (color and 0x00F0F0F0)
            colorFrequency[quantized] = (colorFrequency[quantized] ?: 0) + 1
            if (!bucketRepresentative.containsKey(quantized)) {
                bucketRepresentative[quantized] = color
            }

            // Vibrant: high saturation and moderate luminance
            if (sat > maxSat && lum in 0.3f..0.8f) {
                maxSat = sat
                bestVibrant = color
            }

            // Dark vibrant: decent saturation and low luminance
            if (lum in 0.1f..0.4f && sat > 0.2f && lum < minLumDark) {
                minLumDark = lum
                bestDarkVibrant = color
            }
        }

        val dominantQuantized = colorFrequency.maxByOrNull { it.value }?.key
        val dominantInt = dominantQuantized?.let { bucketRepresentative[it] } ?: pixels[0]

        val result = ExtractedPalette(
            dominantColor = Color(dominantInt or 0xFF000000.toInt()),
            vibrantColor = Color(bestVibrant or 0xFF000000.toInt()),
            darkVibrantColor = Color(bestDarkVibrant or 0xFF000000.toInt()),
            lightVibrantColor = Color(bestVibrant or 0xFF000000.toInt())
        )
        cache.put(cacheKey, result)
        return result
    }

    /**
     * Blends two colors using linear RGB interpolation.
     */
    fun blendColors(base: Color, overlay: Color, ratio: Float): Color {
        val clamped = ratio.coerceIn(0.0f, 1.0f)
        val r = base.red + (overlay.red - base.red) * clamped
        val g = base.green + (overlay.green - base.green) * clamped
        val b = base.blue + (overlay.blue - base.blue) * clamped
        val a = base.alpha + (overlay.alpha - base.alpha) * clamped
        return Color(r, g, b, a)
    }
}
