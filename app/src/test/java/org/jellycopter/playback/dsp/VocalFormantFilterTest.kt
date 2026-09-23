package org.jellycopter.playback.dsp

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(UnstableApi::class)
class VocalFormantFilterTest {

    @Test
    fun testBiquadCoefficientsAt48kHz() {
        val coeffs = VocalFormantFilter.calculateCoefficients(
            sampleRate = 48000,
            f0 = 2500.0f,
            gainDb = 3.5f,
            q = 1.0f
        )

        // Matching Robert Bristow-Johnson Audio EQ Cookbook values
        assertEquals(1.0576f, coeffs.b0, 0.002f)
        assertEquals(-1.6739f, coeffs.b1, 0.002f)
        assertEquals(0.7101f, coeffs.b2, 0.002f)
        assertEquals(-1.6739f, coeffs.a1, 0.002f)
        assertEquals(0.7677f, coeffs.a2, 0.002f)
    }

    @Test
    fun testFrequencyResponsePeakingAt2500Hz() {
        val sampleRate = 48000

        // At center frequency 2500 Hz, response must be exactly +3.5 dB
        val gainAt2500 = VocalFormantFilter.getMagnitudeResponseDb(
            frequencyHz = 2500.0f,
            sampleRate = sampleRate,
            f0 = 2500.0f,
            gainDb = 3.5f,
            q = 1.0f
        )
        assertEquals(3.50, gainAt2500, 0.05)

        // At 100 Hz (bass/low frequency), response should be essentially flat (0.0 dB)
        val gainAt100 = VocalFormantFilter.getMagnitudeResponseDb(
            frequencyHz = 100.0f,
            sampleRate = sampleRate,
            f0 = 2500.0f,
            gainDb = 3.5f,
            q = 1.0f
        )
        assertEquals(0.0, gainAt100, 0.05)

        // At 15 kHz (high treble), response should also be flat
        val gainAt15k = VocalFormantFilter.getMagnitudeResponseDb(
            frequencyHz = 15000.0f,
            sampleRate = sampleRate,
            f0 = 2500.0f,
            gainDb = 3.5f,
            q = 1.0f
        )
        assertEquals(0.0, gainAt15k, 0.15)
    }

    @Test
    fun testSignalAmplificationAtCenterFrequency() {
        val sampleRate = 48000
        val f0 = 2500.0f
        val numSamples = 2400 // 50 ms of sine wave
        val input = FloatArray(numSamples) { i ->
            (0.5 * sin(2.0 * PI * f0 * i / sampleRate)).toFloat()
        }

        val output = VocalFormantFilter.processFloatBuffer(
            input = input,
            channels = 1,
            sampleRate = sampleRate,
            f0 = f0,
            gainDb = 3.5f,
            q = 1.0f
        )

        // Steady-state amplitude after filter transients (check second half)
        var maxIn = 0.0f
        var maxOut = 0.0f
        for (i in 1200 until numSamples) {
            maxIn = maxOf(maxIn, abs(input[i]))
            maxOut = maxOf(maxOut, abs(output[i]))
        }

        val observedGain = maxOut / maxIn
        val expectedGain = Math.pow(10.0, 3.5 / 20.0).toFloat() // approx 1.496

        assertEquals(expectedGain, observedGain, 0.05f)
    }

    @Test
    fun testFilterStability() {
        val sampleRate = 48000
        val input = FloatArray(1000) { 0.5f } // DC step input

        val output = VocalFormantFilter.processFloatBuffer(
            input = input,
            channels = 1,
            sampleRate = sampleRate
        )

        // Filter must remain stable and not diverge
        for (sample in output) {
            assertTrue("Filter output diverged: $sample", abs(sample) < 2.0f)
            assertTrue("Sample is NaN", !sample.isNaN())
        }
    }
}
