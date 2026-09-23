"""
Tier 5 Adversarial Stress Tests: Audio/Video DSP Subsystem
Stress-testing multi-channel downmixing bounds, DRC overdrive behavior,
filter stability, and extreme synthetic buffers.
"""

import math
import unittest
from harness.dsp_engine import (
    IturBs775Downmixer,
    DynamicRangeCompressor,
    VocalFormantPeakingFilter,
)


class TestAdversarialAudioDspStress(unittest.TestCase):

    # -------------------------------------------------------------
    # 1. ITU-R BS.775 Normalization & Clipping Bounds
    # -------------------------------------------------------------

    def test_simultaneous_full_scale_clipping_5_1(self):
        """
        Stress 1: Simultaneous 1.0 FS clipping on ALL 6 channels of 5.1.
        Channels: L=1.0, R=1.0, C=1.0, LFE=1.0, Ls=1.0, Rs=1.0.
        Across center boost values in [0.0, 6.0] dB.
        Output peak MUST strictly be <= 1.0000001 (no digital clipping).
        """
        for boost in [0.0, 1.5, 3.0, 4.5, 6.0]:
            dmx = IturBs775Downmixer(center_boost_db=boost, enable_normalization=True)
            # Process 1000 frames of full-scale 1.0
            frames = 1000
            channels_51 = [[1.0] * frames for _ in range(6)]
            out = dmx.process_buffer(channels_51)

            max_l = max(out[0])
            max_r = max(out[1])
            self.assertLessEqual(
                max_l, 1.000001,
                f"Boost {boost} dB: 5.1 Left channel exceeded full scale: {max_l}"
            )
            self.assertLessEqual(
                max_r, 1.000001,
                f"Boost {boost} dB: 5.1 Right channel exceeded full scale: {max_r}"
            )

    def test_simultaneous_full_scale_clipping_7_1(self):
        """
        Stress 2: Simultaneous 1.0 FS clipping on ALL 8 channels of 7.1.
        Channels: L=1.0, R=1.0, C=1.0, LFE=1.0, Ls=1.0, Rs=1.0, Rls=1.0, Rrs=1.0.
        Output peak MUST strictly be <= 1.0 (no digital clipping).
        """
        for boost in [0.0, 3.0, 6.0]:
            dmx = IturBs775Downmixer(center_boost_db=boost, enable_normalization=True)
            frames = 500
            channels_71 = [[1.0] * frames for _ in range(8)]
            out = dmx.process_buffer(channels_71)

            max_l = max(out[0])
            max_r = max(out[1])
            # Check whether 7.1 normalization factor bounds output to 1.0
            print(f"7.1 boost={boost} max_l={max_l}, max_r={max_r}")
            self.assertLessEqual(
                max_l, 1.000001,
                f"7.1 Boost {boost} dB: Left channel clipped: {max_l}"
            )
            self.assertLessEqual(
                max_r, 1.000001,
                f"7.1 Boost {boost} dB: Right channel clipped: {max_r}"
            )

    def test_digital_silence_downmix(self):
        """
        Stress 3: Digital silence (all channels 0.0).
        Output must be identical to 0.0 with no NaN, Inf, or denormal anomalies.
        """
        dmx = IturBs775Downmixer(center_boost_db=3.0)
        channels_51 = [[0.0] * 100 for _ in range(6)]
        out = dmx.process_buffer(channels_51)
        self.assertEqual(max(out[0]), 0.0)
        self.assertEqual(min(out[0]), 0.0)
        self.assertEqual(max(out[1]), 0.0)
        self.assertEqual(min(out[1]), 0.0)

    def test_dc_offset_buffer_downmix(self):
        """
        Stress 4: Constant DC offset at -0.95 and +0.95 across channels.
        Output must remain within [-1.0, 1.0].
        """
        dmx = IturBs775Downmixer(center_boost_db=3.0)
        for dc in [-0.95, +0.95]:
            channels = [[dc] * 200 for _ in range(6)]
            out = dmx.process_buffer(channels)
            self.assertLessEqual(max(out[0]), 1.0)
            self.assertGreaterEqual(min(out[0]), -1.0)

    def test_extreme_nyquist_high_frequency_bursts(self):
        """
        Stress 5: Alternating Nyquist square wave [+1.0, -1.0, +1.0, -1.0].
        No phase cancellation failure or resonance blowup.
        """
        dmx = IturBs775Downmixer(center_boost_db=3.0)
        nyquist = [1.0 if i % 2 == 0 else -1.0 for i in range(500)]
        channels = [nyquist.copy() for _ in range(6)]
        out = dmx.process_buffer(channels)
        self.assertLessEqual(max(out[0]), 1.000001)
        self.assertGreaterEqual(min(out[0]), -1.000001)

    # -------------------------------------------------------------
    # 2. DRC Transfer Curve & Extreme Overdrive (+12 dBFS)
    # -------------------------------------------------------------

    def test_drc_extreme_overdrive_plus_12dbfs(self):
        """
        Stress 6: Extreme overdrive signal at +12 dBFS (amplitude = 3.981).
        DRC ceiling is -3.0 dBFS (amplitude 0.7079).
        Output MUST be clamped strictly <= ceiling amplitude (0.7079).
        Static gain must be clamped to ceiling_db - in_db = -3 - 12 = -15 dB.
        """
        drc = DynamicRangeCompressor(sample_rate=48000, ceiling_db=-3.0)
        overdrive_amp = math.pow(10.0, 12.0 / 20.0)  # ~3.98107
        ceiling_amp = math.pow(10.0, -3.0 / 20.0)    # ~0.707946

        # Feed sustained overdrive burst
        samples = [overdrive_amp] * 5000  # >100ms, well beyond 10ms attack
        processed = drc.process_buffer(samples)

        # In steady state (after attack envelope settles), output must not exceed ceiling
        steady_state = processed[2000:]
        max_steady = max(steady_state)
        self.assertLessEqual(
            max_steady, ceiling_amp + 1e-4,
            f"Overdrive not clamped by ceiling! Max was {max_steady}, expected <= {ceiling_amp}"
        )

        # Check static gain function
        gain_db = drc.compute_static_gain_db(+12.0)
        self.assertLessEqual(gain_db, -15.0)

    def test_drc_silent_noise_floor_stability(self):
        """
        Stress 7: Digital silence and low noise floor (-100 dBFS).
        Envelope must not blow up or produce NaN/Inf.
        Silence guard must kick in smoothly and apply +6 dB boost without division by zero.
        """
        drc = DynamicRangeCompressor(sample_rate=48000)
        silent_samples = [0.0] * 1000
        processed = drc.process_buffer(silent_samples)
        self.assertTrue(all(p == 0.0 for p in processed))

        # Quiet noise floor: -80 dBFS (amplitude 1e-4)
        noise_amp = 1e-4
        quiet_samples = [noise_amp] * 1000
        processed_quiet = drc.process_buffer(quiet_samples)
        # Gain should be +6dB (amplitude * 1.995)
        expected_boost = noise_amp * math.pow(10.0, 6.0 / 20.0)
        # After release settles, quiet samples get +6dB
        self.assertAlmostEqual(processed_quiet[-1], expected_boost, places=5)

    # -------------------------------------------------------------
    # 3. Peaking Biquad Filter Numerical Stability
    # -------------------------------------------------------------

    def test_biquad_extreme_impulse_and_dc_stability(self):
        """
        Stress 8: Unit impulse [1.0, 0.0, 0.0, ...] and constant DC [1.0, 1.0, ...].
        Verifies filter stability (no unbounded ringing or numeric overflow).
        """
        biquad = VocalFormantPeakingFilter(sample_rate=48000, f0=2500.0, q=1.0, gain_db=3.5)
        # 1. Impulse response
        impulse = [1.0] + [0.0] * 499
        out_impulse = biquad.process_buffer(impulse)
        # Filter must decay to 0
        tail = out_impulse[-50:]
        for s in tail:
            self.assertAlmostEqual(s, 0.0, places=5)

        # 2. DC response: peaking filter at f0=2500Hz has 0 dB gain at DC (0 Hz)
        dc_biquad = VocalFormantPeakingFilter(sample_rate=48000, f0=2500.0, q=1.0, gain_db=3.5)
        dc_input = [1.0] * 1000
        out_dc = dc_biquad.process_buffer(dc_input)
        # Steady state DC must be exactly 1.0 (0 dB gain)
        self.assertAlmostEqual(out_dc[-1], 1.0, places=3)


if __name__ == "__main__":
    unittest.main()
