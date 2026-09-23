"""
Tier 1 Feature Tests: Audio Night Mode DRC & Speech Formant Peaking EQ
Requirements: R2, ORIGINAL_REQUEST.md § R2, PROJECT.md Features 11 & 12.
Verifies +6dB dialogue floor boost, -12dB transient clamping, dual-envelope attack/release,
and +3.5dB vocal formant peaking filter at 2.5 kHz.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import math
from harness.dsp_engine import DynamicRangeCompressor, VocalFormantPeakingFilter


class TestFeatureAudioDrcFormantEq(unittest.TestCase):

    def setUp(self):
        self.drc = DynamicRangeCompressor(sample_rate=48000)
        self.eq = VocalFormantPeakingFilter(sample_rate=48000, f0=2500.0, q=1.0, gain_db=3.5)

    def test_drc_quiet_dialogue_floor_boost(self):
        """Test 1: Input signal below -40 dBFS receives exactly +6.0 dB gain."""
        gain_db = self.drc.compute_static_gain_db(-50.0)
        self.assertAlmostEqual(gain_db, 6.0, places=2)

    def test_drc_loud_transient_clamping(self):
        """Test 2: Full-scale input at 0 dBFS is clamped down to <= -12 dBFS (ceiling at -3 dBFS)."""
        gain_db = self.drc.compute_static_gain_db(0.0)
        output_db = 0.0 + gain_db
        # Dynamic range compression reduces loud 0 dBFS input to <= -12 dBFS
        self.assertLessEqual(output_db, -12.0)
        self.assertLessEqual(output_db, self.drc.ceiling_db)

    def test_drc_attack_envelope_rapid_response(self):
        """Test 3: Sudden high-amplitude burst is clamped rapidly within attack window (10 ms)."""
        # Feed 1000 samples of quiet audio (-45 dBFS = 0.0056)
        quiet_samples = [0.0056] * 1000
        self.drc.process_buffer(quiet_samples)

        # Feed sudden 0 dBFS peak (1.0 amplitude)
        loud_samples = [1.0] * 480  # 10 ms at 48 kHz
        processed = self.drc.process_buffer(loud_samples)

        # By the end of 10 ms, envelope must have engaged and clamped amplitude <= 0.80
        final_sample = processed[-1]
        self.assertLessEqual(final_sample, 0.80)

    def test_vocal_formant_filter_peaking_response_at_2500hz(self):
        """Test 4: Biquad filter achieves exactly +3.50 dB (+/-0.05 dB) gain at center frequency 2500 Hz."""
        gain_at_f0 = self.eq.frequency_response_db(2500.0)
        self.assertAlmostEqual(gain_at_f0, 3.50, delta=0.05)

    def test_vocal_formant_filter_flat_bass_and_treble_response(self):
        """Test 5: Filter leaves low frequencies (100 Hz) and high frequencies (18 kHz) uncolored (0 dB)."""
        gain_at_100hz = self.eq.frequency_response_db(100.0)
        gain_at_18khz = self.eq.frequency_response_db(18000.0)
        self.assertAlmostEqual(gain_at_100hz, 0.00, delta=0.05)
        self.assertAlmostEqual(gain_at_18khz, 0.00, delta=0.10)


if __name__ == "__main__":
    unittest.main()
