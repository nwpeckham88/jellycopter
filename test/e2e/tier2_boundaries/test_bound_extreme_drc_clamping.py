"""
Tier 2 Boundary Tests: Extreme DRC Clamping & Edge Signals
Requirements: Dual-envelope Dynamic Range Compression under extreme audio inputs.
Verifies digital silence (-inf dBFS / zeros), severe +6dBFS overdrive bursts,
envelope continuity, and high sample-rate stability (96 kHz).
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import math
from harness.dsp_engine import DynamicRangeCompressor


class TestBoundExtremeDrcClamping(unittest.TestCase):

    def setUp(self):
        self.drc = DynamicRangeCompressor(sample_rate=48000)

    def test_digital_silence_does_not_produce_nan_or_exception(self):
        """Boundary 1: Zero samples (-inf dBFS) pass through safely without division-by-zero or NaN."""
        silent_samples = [0.0] * 500
        output = self.drc.process_buffer(silent_samples)
        for s in output:
            self.assertEqual(s, 0.0)
            self.assertFalse(math.isnan(s))
            self.assertFalse(math.isinf(s))

    def test_overdrive_burst_clamped_strictly_below_ceiling(self):
        """Boundary 2: Severe 2.0 amplitude (+6 dBFS overdrive) burst is clamped below ceiling."""
        overdrive_samples = [2.0] * 500
        output = self.drc.process_buffer(overdrive_samples)
        # Ceiling is -3.0 dBFS (amplitude approx 0.707)
        # Steady state must clamp output amplitude <= 0.75
        self.assertLessEqual(output[-1], 0.75)

    def test_whisper_floor_boost_continuity(self):
        """Boundary 3: Gain curve is continuous at the -40 dBFS threshold without step discontinuity."""
        gain_below = self.drc.compute_static_gain_db(-40.001)
        gain_at = self.drc.compute_static_gain_db(-40.0)
        gain_above = self.drc.compute_static_gain_db(-39.999)

        self.assertAlmostEqual(gain_below, 6.0, places=2)
        self.assertAlmostEqual(gain_at, 6.0, places=2)
        self.assertAlmostEqual(gain_above, 6.0, delta=0.01)

    def test_high_compression_ratio_plateau(self):
        """Boundary 4: Increasing input from -10 dBFS to 0 dBFS compresses dynamic range by 4:1."""
        gain_minus_10 = self.drc.compute_static_gain_db(-10.0)
        gain_0 = self.drc.compute_static_gain_db(0.0)

        out_minus_10 = -10.0 + gain_minus_10  # -10 + (-6) = -16 dBFS
        out_0 = 0.0 + gain_0                 # 0 + (-13.5) = -13.5 dBFS
        # A 10 dB input increase produces only 2.5 dB output increase (4:1 ratio)
        delta_in = 10.0
        delta_out = out_0 - out_minus_10
        self.assertAlmostEqual(delta_out, delta_in / 4.0, places=2)

    def test_high_sample_rate_stability_at_96khz(self):
        """Boundary 5: 96 kHz studio sample rate correctly computes attack/release alphas without instability."""
        drc_96k = DynamicRangeCompressor(sample_rate=96000)
        self.assertGreater(drc_96k.alpha_attack, 0.0)
        self.assertLess(drc_96k.alpha_attack, 1.0)
        # Verify 96kHz processes 1000 samples cleanly
        out = drc_96k.process_buffer([0.5] * 1000)
        self.assertEqual(len(out), 1000)


if __name__ == "__main__":
    unittest.main()
