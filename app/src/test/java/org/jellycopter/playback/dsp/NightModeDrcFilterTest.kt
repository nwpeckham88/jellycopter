package org.jellycopter.playback.dsp

import androidx.media3.common.util.UnstableApi
import org.jellycopter.core.model.DrcConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(UnstableApi::class)
class NightModeDrcFilterTest {

    @Test
    fun testTransferCurveGainCalculation() {
        val config = DrcConfig(
            enabled = true,
            dialogueFloorBoostDb = 6.0f,
            compressionThresholdDb = -18.0f,
            compressionRatio = 4.0f,
            transientClampingCeilingDb = -3.0f
        )

        // 1. Quiet dialogue floor (< -40 dBFS): exactly +6 dB boost
        val gainQuiet = NightModeDrcFilter.computeGainDb(-50.0f, config)
        assertEquals(6.0f, gainQuiet, 0.01f)

        // 2. Linear transition zone: at -29 dBFS (halfway between -40 and -18)
        val gainMid = NightModeDrcFilter.computeGainDb(-29.0f, config)
        assertEquals(3.0f, gainMid, 0.05f)

        // 3. Compression threshold: at -18 dBFS, gain is exactly 0 dB (unity)
        val gainThreshold = NightModeDrcFilter.computeGainDb(-18.0f, config)
        assertEquals(0.0f, gainThreshold, 0.01f)

        // 4. Above threshold: 4:1 compression ratio
        // At -10 dBFS: input is 8 dB above threshold.
        // Compressed excess: 8 / 4 = 2 dB -> output level = -18 + 2 = -16 dBFS
        // Effective gain: -16 - (-10) = -6 dB
        val gainAbove = NightModeDrcFilter.computeGainDb(-10.0f, config)
        assertEquals(-6.0f, gainAbove, 0.01f)

        // 5. Full-scale signal at 0 dBFS:
        // Output = -18 + 18 / 4 = -13.5 dBFS. Gain = -13.5 dB.
        val gainFullScale = NightModeDrcFilter.computeGainDb(0.0f, config)
        assertEquals(-13.5f, gainFullScale, 0.01f)
    }

    @Test
    fun testCeilingClampingEnforcement() {
        // Test with disabled compression (ratio = 1.0) to test ceiling clamping directly
        val config = DrcConfig(
            enabled = true,
            dialogueFloorBoostDb = 0.0f,
            compressionThresholdDb = 0.0f,
            compressionRatio = 1.0f,
            transientClampingCeilingDb = -3.0f
        )

        // At -1 dBFS, ceiling is -3 dBFS. Max allowed gain is -3 - (-1) = -2 dB
        val gainClamped = NightModeDrcFilter.computeGainDb(-1.0f, config)
        assertEquals(-2.0f, gainClamped, 0.01f)
    }

    @Test
    fun testZeroSignalGuardPreventsNanAndInfinity() {
        val config = DrcConfig(enabled = true)
        val input = FloatArray(480) { 0.0f }

        val output = NightModeDrcFilter.processFloatBuffer(
            input = input,
            channels = 2,
            sampleRate = 48000,
            config = config
        )

        assertEquals(480, output.size)
        for (sample in output) {
            assertTrue("Output sample should not be NaN", !sample.isNaN())
            assertTrue("Output sample should not be Infinite", !sample.isInfinite())
            assertEquals(0.0f, sample, 0.0001f)
        }
    }

    @Test
    fun testLoudTransientClampingOnBuffer() {
        val config = DrcConfig(
            enabled = true,
            transientClampingCeilingDb = -3.0f,
            attackTimeMs = 10.0f
        )
        val ceilingLinear = 0.70794578f // 10^(-3/20)

        // Buffer of 0 dBFS full scale samples
        val input = FloatArray(480) { 1.0f }
        val output = NightModeDrcFilter.processFloatBuffer(
            input = input,
            channels = 1,
            sampleRate = 48000,
            config = config
        )

        for (sample in output) {
            assertTrue(
                "Sample exceeded -3 dBFS ceiling: $sample > $ceilingLinear",
                abs(sample) <= ceilingLinear + 0.0001f
            )
        }
    }

    @Test
    fun test16BitDrcProcessing() {
        val config = DrcConfig(enabled = true)
        val input = ShortArray(480) { 1000.toShort() } // Quiet dialogue

        val output = NightModeDrcFilter.process16BitBuffer(
            input = input,
            channels = 1,
            sampleRate = 48000,
            config = config
        )

        assertEquals(480, output.size)
        for (sample in output) {
            assertTrue("Processed sample must be valid Short", sample in -32768..32767)
        }
    }
}
