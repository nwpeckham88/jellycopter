package org.jellycopter.playback

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import kotlin.math.roundToInt

/**
 * Subtitle Luminance Dimmer.
 *
 * Attenuates subtitle luminance by ~50% during Night Mode to prevent blinding glare
 * on high-brightness HDR10 and Dolby Vision displays in dark rooms.
 *
 * Supports both Media3 CaptionStyleCompat and libmpv sub-color arguments.
 */
@UnstableApi
class SubtitleDimmer(
    initialDimmed: Boolean = false,
    initialDimRatio: Float = 0.5f
) {

    var isDimmed: Boolean = initialDimmed
    var dimRatio: Float = initialDimRatio.coerceIn(0.1f, 0.9f)

    /**
     * Produces a CaptionStyleCompat for Media3 SubtitleView.
     */
    fun getCaptionStyle(): CaptionStyleCompat {
        return if (isDimmed) {
            CaptionStyleCompat(
                dimColor(COLOR_NORMAL_TEXT, dimRatio),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                COLOR_DIMMED_EDGE,
                null
            )
        } else {
            CaptionStyleCompat(
                COLOR_NORMAL_TEXT,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                COLOR_NORMAL_EDGE,
                null
            )
        }
    }

    /**
     * Generates MPV subtitle color options.
     */
    fun getMpvSubColorOptions(): Map<String, String> {
        return if (isDimmed) {
            val factor = 1.0f - dimRatio
            val r = String.format(java.util.Locale.US, "%.2f", 1.0f * factor)
            val g = String.format(java.util.Locale.US, "%.2f", 1.0f * factor)
            val b = String.format(java.util.Locale.US, "%.2f", 1.0f * factor)
            mapOf(
                "sub-color" to "$r/$g/$b/0.8",
                "sub-border-color" to "0.1/0.1/0.1/0.8"
            )
        } else {
            mapOf(
                "sub-color" to "1.0/1.0/1.0/1.0",
                "sub-border-color" to "0.0/0.0/0.0/1.0"
            )
        }
    }

    companion object {
        const val COLOR_NORMAL_TEXT: Int = 0xFFFFFFFF.toInt()
        const val COLOR_NORMAL_EDGE: Int = 0xFF000000.toInt()

        // 50% luminance grey (#80B0B0B0: RGB 176,176,176 with ~80% alpha)
        const val COLOR_DIMMED_TEXT: Int = 0xB0B0B0B0.toInt()
        const val COLOR_DIMMED_EDGE: Int = 0xB0101010.toInt()

        /**
         * Attenuates an ARGB color's luminance by [dimRatio].
         */
        fun dimColor(colorArgb: Int, dimRatio: Float = 0.5f): Int {
            val factor = 1.0f - dimRatio.coerceIn(0.0f, 1.0f)
            val a = (colorArgb ushr 24) and 0xFF
            val r = (((colorArgb ushr 16) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
            val g = (((colorArgb ushr 8) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
            val b = ((colorArgb and 0xFF) * factor).roundToInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        /**
         * Computes perceived relative luminance (ITU-R BT.709 formula):
         * Y = 0.2126 * R + 0.7152 * G + 0.0722 * B
         */
        fun computeRelativeLuminance(colorArgb: Int): Float {
            val r = ((colorArgb ushr 16) and 0xFF) / 255.0f
            val g = ((colorArgb ushr 8) and 0xFF) / 255.0f
            val b = (colorArgb and 0xFF) / 255.0f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
    }
}
