package org.jellycopter.playback.dsp

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(UnstableApi::class)
class ItuRBs775DownmixerTest {

    @Test
    fun testStandard51CoefficientsAtZeroCenterBoost() {
        val coeffs = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = 0.0f)

        // Standard ITU-R BS.775: -3dB attenuation = 1/sqrt(2) approx 0.7071
        assertEquals(0.7071f, coeffs.centerWeight, 0.001f)
        assertEquals(0.7071f, coeffs.surroundWeight, 0.001f)

        // Normalization factor: 1 / (1 + 0.7071 + 0.7071) = 1 / 2.4142 approx 0.4142
        val expectedNorm = 1.0f / (1.0f + 2.0f * 0.70710678f)
        assertEquals(expectedNorm, coeffs.normalizationFactor, 0.001f)
    }

    @Test
    fun testCenterDialogueBoostAt3dBAnd6dB() {
        // At +3dB boost: (-3dB + 3dB) = 0dB -> alphaC = 1.0
        val coeffs3dB = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = 3.0f)
        assertEquals(1.0000f, coeffs3dB.centerWeight, 0.001f)

        // At +6dB boost: (-3dB + 6dB) = +3dB -> alphaC = 10^(3/20) approx 1.4125
        val coeffs6dB = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = 6.0f)
        assertEquals(1.4125f, coeffs6dB.centerWeight, 0.005f)
    }

    @Test
    fun testCenterBoostClamping() {
        val clampedLow = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = -10.0f)
        val expectedLow = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = 0.0f)
        assertEquals(expectedLow.centerWeight, clampedLow.centerWeight, 0.0001f)

        val clampedHigh = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = 15.0f)
        val expectedHigh = ItuRBs775Downmixer.calculate51Coefficients(centerBoostDb = 6.0f)
        assertEquals(expectedHigh.centerWeight, clampedHigh.centerWeight, 0.0001f)
    }

    @Test
    fun testAntiClippingNormalizationWithSimultaneousMaximumSignals() {
        // Worst-case condition: All 6 channels saturated at +1.0 with max dialogue boost (+6 dB)
        val channels = 6
        val frameCount = 100
        val input = FloatArray(frameCount * channels) { 1.0f }

        val stereoOutput = ItuRBs775Downmixer.downmixFloat(input, channels = channels, centerBoostDb = 6.0f)

        assertEquals(frameCount * 2, stereoOutput.size)
        var maxPeak = 0.0f
        for (sample in stereoOutput) {
            val absSample = abs(sample)
            if (absSample > maxPeak) {
                maxPeak = absSample
            }
            assertTrue("Sample clipped: $sample > 1.0", sample <= 1.0f)
            assertTrue("Sample clipped: $sample < -1.0", sample >= -1.0f)
        }

        // Peak should be normalized right at 1.0 (0 dBFS) without digital clipping
        assertEquals(1.0f, maxPeak, 0.001f)
    }

    @Test
    fun test71DownmixingWithMaximumSignals() {
        // 7.1 input: 8 channels
        val channels = 8
        val frameCount = 50
        val input = FloatArray(frameCount * channels) { 1.0f }

        val stereoOutput = ItuRBs775Downmixer.downmixFloat(input, channels = channels, centerBoostDb = 3.0f)

        assertEquals(frameCount * 2, stereoOutput.size)
        for (sample in stereoOutput) {
            assertTrue("7.1 Downmixed sample exceeded 1.0: $sample", sample <= 1.0f)
            assertTrue("7.1 Downmixed sample below -1.0: $sample", sample >= -1.0f)
        }
    }

    @Test
    fun testZeroCostPassthroughForMonoAndStereo() {
        val stereoInput = floatArrayOf(0.5f, -0.5f, 0.8f, -0.8f)
        val result = ItuRBs775Downmixer.downmixFloat(stereoInput, channels = 2)
        assertEquals(stereoInput.size, result.size)
        for (i in stereoInput.indices) {
            assertEquals(stereoInput[i], result[i], 0.0001f)
        }
    }

    @Test
    fun test16BitDownmixingNoIntegerWrapping() {
        val channels = 6
        val frameCount = 50
        // Saturated 16-bit PCM input (+32767 on all channels)
        val input = ShortArray(frameCount * channels) { 32767.toShort() }

        val output = ItuRBs775Downmixer.downmix16Bit(input, channels = channels, centerBoostDb = 6.0f)

        assertEquals(frameCount * 2, output.size)
        for (s in output) {
            // Must not wrap around to negative numbers or overflow
            assertTrue("16-bit sample clipped or wrapped: $s", s in 0..32767)
        }
    }
}
