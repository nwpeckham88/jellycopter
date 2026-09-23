package org.jellycopter.playback

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class SubtitleDimmerTest {

    @Test
    fun testColorDimming50PercentLuminance() {
        // Pure White: ARGB 0xFFFFFFFF
        val white = 0xFFFFFFFF.toInt()
        val originalLuminance = SubtitleDimmer.computeRelativeLuminance(white)
        assertEquals(1.0f, originalLuminance, 0.01f)

        // Dim by 50%
        val dimmedColor = SubtitleDimmer.dimColor(white, dimRatio = 0.5f)
        val dimmedLuminance = SubtitleDimmer.computeRelativeLuminance(dimmedColor)

        // Perceived luminance should be approx 0.50
        assertEquals(0.50f, dimmedLuminance, 0.02f)
    }

    @Test
    fun testMpvSubColorOptions() {
        val dimmer = SubtitleDimmer(initialDimmed = false)

        val normalOptions = dimmer.getMpvSubColorOptions()
        assertEquals("1.0/1.0/1.0/1.0", normalOptions["sub-color"])

        dimmer.isDimmed = true
        dimmer.dimRatio = 0.5f
        val dimmedOptions = dimmer.getMpvSubColorOptions()
        assertEquals("0.50/0.50/0.50/0.8", dimmedOptions["sub-color"])
        assertEquals("0.1/0.1/0.1/0.8", dimmedOptions["sub-border-color"])
    }

    @Test
    fun testCaptionStyleGeneration() {
        val dimmer = SubtitleDimmer(initialDimmed = false)
        val normalStyle = dimmer.getCaptionStyle()
        assertEquals(SubtitleDimmer.COLOR_NORMAL_TEXT, normalStyle.foregroundColor)

        dimmer.isDimmed = true
        val dimmedStyle = dimmer.getCaptionStyle()
        assertTrue(normalStyle.foregroundColor != dimmedStyle.foregroundColor)
    }
}
