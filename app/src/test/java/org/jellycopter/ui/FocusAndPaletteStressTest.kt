package org.jellycopter.ui

import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Color
import org.jellycopter.ui.components.FocusEngine
import org.jellycopter.ui.theme.BaseThemeMode
import org.jellycopter.ui.theme.JellycopterThemeDefaults
import org.jellycopter.ui.theme.PaletteExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAndPaletteStressTest {

    // -------------------------------------------------------------
    // 1. Focus Engine Scaling & Invariant Tests
    // -------------------------------------------------------------

    @Test
    fun testFocusScaleInvariants() {
        assertEquals(1.08f, FocusEngine.FOCUS_SCALE, 0.0001f)
        assertEquals(1.0f, FocusEngine.UNFOCUSED_SCALE, 0.0001f)

        val focused = FocusEngine.calculateScale(isFocused = true)
        val unfocused = FocusEngine.calculateScale(isFocused = false)

        assertEquals(1.08f, focused, 0.0001f)
        assertEquals(1.0f, unfocused, 0.0001f)

        // Glow elevations
        assertEquals(16.0f, FocusEngine.calculateGlowElevationDp(isFocused = true), 0.001f)
        assertEquals(2.0f, FocusEngine.calculateGlowElevationDp(isFocused = false), 0.001f)

        // Border widths
        assertEquals(3.0f, FocusEngine.calculateBorderWidthDp(isFocused = true), 0.001f)
        assertEquals(0.0f, FocusEngine.calculateBorderWidthDp(isFocused = false), 0.001f)

        // Spring Spec
        val spec = FocusEngine.FocusSpringSpec
        assertEquals(Spring.DampingRatioLowBouncy, spec.dampingRatio, 0.001f)
        assertEquals(Spring.StiffnessMedium, spec.stiffness, 0.001f)
    }

    @Test
    fun testPivotOffsetAcrossTelevisionResolutions() {
        val testResolutions = listOf(
            Pair(1280f, 102.4f),  // 720p
            Pair(1920f, 153.6f),  // 1080p
            Pair(2560f, 204.8f),  // 1440p
            Pair(3840f, 307.2f),  // 4K
            Pair(7680f, 614.4f)   // 8K
        )

        for ((viewportWidth, expectedOffset) in testResolutions) {
            val offset = FocusEngine.calculateSnapOffset(
                cardWidthPx = 300f,
                viewportWidthPx = viewportWidth,
                parentFraction = FocusEngine.PIVOT_PARENT_FRACTION
            )
            assertEquals(expectedOffset, offset, 0.05f)
        }
    }

    // -------------------------------------------------------------
    // 2. Palette Extractor Monochrome & Corrupted Pixel Data Stress
    // -------------------------------------------------------------

    @Test
    fun testPaletteExtractionPureBlackMonochrome() {
        // 100 pixels of pure black (#000000)
        val blackPixels = IntArray(100) { 0xFF000000.toInt() }
        val palette = PaletteExtractor.extractFromPixels(blackPixels, 10, 10)
        assertNotNull(palette)
        assertEquals(Color(0xFF000000), palette.dominantColor)
    }

    @Test
    fun testPaletteExtractionPureWhiteMonochrome() {
        // 100 pixels of pure white (#FFFFFF) - delta == 0, lum == 1.0f
        val whitePixels = IntArray(100) { 0xFFFFFFFF.toInt() }
        val palette = PaletteExtractor.extractFromPixels(whitePixels, 10, 10)
        assertNotNull(palette)
        assertEquals(Color(0xFFFFFFFF), palette.dominantColor)
    }

    @Test
    fun testPaletteExtractionPureGrayMonochrome() {
        // 100 pixels of medium gray (#808080) - delta == 0, sat == 0
        val grayPixels = IntArray(100) { 0xFF808080.toInt() }
        val palette = PaletteExtractor.extractFromPixels(grayPixels, 10, 10)
        assertNotNull(palette)
        assertNotNull(palette.dominantColor)
    }

    @Test
    fun testPaletteExtractionAllTransparentPixels() {
        // 100 pixels with alpha < 128 (fully transparent)
        val transparentPixels = IntArray(100) { 0x00FFFFFF }
        val palette = PaletteExtractor.extractFromPixels(transparentPixels, 10, 10)
        assertNotNull(palette)
        // Alpha coerced to 0xFF on return
        assertEquals(1.0f, palette.dominantColor.alpha, 0.001f)
    }

    @Test
    fun testPaletteExtractionEmptyPixels() {
        val emptyPixels = IntArray(0)
        val palette = PaletteExtractor.extractFromPixels(emptyPixels, 0, 0)
        assertNotNull(palette)
        assertNotNull(palette.dominantColor)
    }

    @Test
    fun testPaletteExtractionSinglePixel() {
        val singlePixel = intArrayOf(0xFFFF6600.toInt()) // Vibrant orange
        val palette = PaletteExtractor.extractFromPixels(singlePixel, 1, 1)
        assertNotNull(palette)
        assertEquals(1.0f, palette.dominantColor.red, 0.05f)
    }

    // -------------------------------------------------------------
    // 3. Downsampling Dimension Bounds & Blending
    // -------------------------------------------------------------

    @Test
    fun testDownsamplingGiantPosterDimensions() {
        // 10000 x 15000 image
        val (w, h) = PaletteExtractor.downsampleDimensions(10000, 15000)
        assertEquals(120, w)
        assertEquals(180, h)
        assertTrue(w <= 120)
        assertTrue(h <= 180)
    }

    @Test
    fun testDownsamplingDegenerateDimensions() {
        val (w0, h0) = PaletteExtractor.downsampleDimensions(0, 0)
        assertEquals(120, w0)
        assertEquals(180, h0)

        val (wNeg, hNeg) = PaletteExtractor.downsampleDimensions(-100, -200)
        assertEquals(120, wNeg)
        assertEquals(180, hNeg)
    }

    @Test
    fun testColorBlendingInvariantsAndClamping() {
        val red = Color(1.0f, 0.0f, 0.0f, 1.0f)
        val blue = Color(0.0f, 0.0f, 1.0f, 1.0f)

        // Exact boundaries
        val b0 = PaletteExtractor.blendColors(red, blue, 0.0f)
        assertEquals(1.0f, b0.red, 0.001f)
        assertEquals(0.0f, b0.blue, 0.001f)

        val b1 = PaletteExtractor.blendColors(red, blue, 1.0f)
        assertEquals(0.0f, b1.red, 0.001f)
        assertEquals(1.0f, b1.blue, 0.001f)

        // Midpoint
        val bMid = PaletteExtractor.blendColors(red, blue, 0.5f)
        assertEquals(0.5f, bMid.red, 0.01f)
        assertEquals(0.5f, bMid.blue, 0.01f)

        // Out of range clamping (<0 and >1)
        val bUnder = PaletteExtractor.blendColors(red, blue, -0.5f)
        assertEquals(1.0f, bUnder.red, 0.001f)

        val bOver = PaletteExtractor.blendColors(red, blue, 1.5f)
        assertEquals(1.0f, bOver.blue, 0.001f)
    }

    @Test
    fun testMasterBaseThemeColorValues() {
        val warm = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.WARM_DARK)
        assertEquals(Color(0xFF14110E), warm.background)
        assertEquals(Color(0xFFF59E0B), warm.primaryAccent)
        assertEquals(Color(0xFFE07A5F), warm.secondaryAccent)

        val oled = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.OLED_PURE_BLACK)
        assertEquals(Color(0xFF000000), oled.background)

        val purple = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.JELLYFIN_PURPLE)
        assertEquals(Color(0xFF050714), purple.background)
        assertEquals(Color(0xFFAA5CC3), purple.primaryAccent)

        val cyberpunk = JellycopterThemeDefaults.getBaseColors(BaseThemeMode.CYBERPUNK_NEON)
        assertEquals(Color(0xFF070913), cyberpunk.background)
        assertEquals(Color(0xFF00F0FF), cyberpunk.primaryAccent)
    }
}
