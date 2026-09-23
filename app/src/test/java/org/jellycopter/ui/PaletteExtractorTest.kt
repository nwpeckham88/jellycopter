package org.jellycopter.ui

import androidx.compose.ui.graphics.Color
import org.jellycopter.ui.theme.BaseThemeMode
import org.jellycopter.ui.theme.JellycopterThemeDefaults
import org.jellycopter.ui.theme.PaletteExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteExtractorTest {

    @Test
    fun testDownsampleDimensionsStandardPoster() {
        // 1000x1500 poster (2:3 ratio) must scale down to 120x180
        val (w, h) = PaletteExtractor.downsampleDimensions(1000, 1500)
        assertEquals(120, w)
        assertEquals(180, h)
    }

    @Test
    fun testDownsampleDimensionsLandscape() {
        // 1920x1080 landscape: width is constrained to 120, height scales proportionally
        val (w, h) = PaletteExtractor.downsampleDimensions(1920, 1080)
        assertEquals(120, w)
        // 1080 * (120 / 1920) = 67.5 -> 68
        assertEquals(68, h)
        assertTrue(w <= 120)
        assertTrue(h <= 180)
    }

    @Test
    fun testDownsampleDimensionsBoundaryCases() {
        // Invalid dimensions fallback safely
        val (w, h) = PaletteExtractor.downsampleDimensions(0, 0)
        assertEquals(120, w)
        assertEquals(180, h)
    }

    @Test
    fun testDirectPixelPaletteExtraction() {
        // Synthetic image:
        // 80 dominant purple pixels (0xFF6B21A8)
        // 15 bright vibrant cyan pixels (0xFF06B6D4)
        // 5 dark vibrant indigo pixels (0xFF1E1B4B)
        val pixels = IntArray(100)
        for (i in 0 until 80) pixels[i] = 0xFF6B21A8.toInt()
        for (i in 80 until 95) pixels[i] = 0xFF06B6D4.toInt()
        for (i in 95 until 100) pixels[i] = 0xFF1E1B4B.toInt()

        val palette = PaletteExtractor.extractFromPixels(pixels, 10, 10)
        assertNotNull(palette)
        assertNotNull(palette.dominantColor)
        assertNotNull(palette.vibrantColor)
        assertNotNull(palette.darkVibrantColor)
    }

    @Test
    fun testColorBlending() {
        val base = Color(1.0f, 0.0f, 0.0f, 1.0f) // Pure Red
        val overlay = Color(0.0f, 0.0f, 1.0f, 1.0f) // Pure Blue

        // 0% blend -> Base
        val blend0 = PaletteExtractor.blendColors(base, overlay, 0.0f)
        assertEquals(1.0f, blend0.red, 0.01f)
        assertEquals(0.0f, blend0.blue, 0.01f)

        // 100% blend -> Overlay
        val blend100 = PaletteExtractor.blendColors(base, overlay, 1.0f)
        assertEquals(0.0f, blend100.red, 0.01f)
        assertEquals(1.0f, blend100.blue, 0.01f)

        // 50% blend -> Midpoint
        val blend50 = PaletteExtractor.blendColors(base, overlay, 0.5f)
        assertEquals(0.5f, blend50.red, 0.01f)
        assertEquals(0.5f, blend50.blue, 0.01f)
    }

    @Test
    fun testMasterBaseThemeDefaults() {
        // Warm Amber Dark
        val warm = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.WARM_DARK)
        assertEquals(Color(0xFF14110E), warm.background)
        assertEquals(Color(0xFFF59E0B), warm.primaryAccent)

        // OLED Pure Black: 0x000000 background for pixel shutoff
        val oled = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.OLED_PURE_BLACK)
        assertEquals(Color(0xFF000000), oled.background)

        // Jellyfin Royal Purple
        val purple = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.JELLYFIN_PURPLE)
        assertEquals(Color(0xFF050714), purple.background)
        assertEquals(Color(0xFFAA5CC3), purple.primaryAccent)

        // Cyberpunk Neon
        val cyberpunk = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.CYBERPUNK_NEON)
        assertEquals(Color(0xFF070913), cyberpunk.background)
        assertEquals(Color(0xFF00F0FF), cyberpunk.primaryAccent)
    }
}
