"""
Tier 1 Feature Tests: ITU-R BS.775 Multichannel Force Stereo Downmixer
Requirements: R2, ORIGINAL_REQUEST.md § R2, PROJECT.md Feature 13.
Verifies ITU-R BS.775 downmix matrices, center dialogue boost parameter,
anti-clipping dynamic normalization, and stereo passthrough bypass.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import math
from harness.dsp_engine import IturBs775Downmixer


class TestFeatureIturBs775Downmix(unittest.TestCase):

    def test_standard_coefficients_at_0db_boost(self):
        """Test 1: At 0 dB boost, alpha_c equals standard 1/sqrt(2) approx 0.7071."""
        downmixer = IturBs775Downmixer(center_boost_db=0.0)
        self.assertAlmostEqual(downmixer.alpha_c, 1.0 / math.sqrt(2.0), places=4)
        self.assertAlmostEqual(downmixer.alpha_s, 1.0 / math.sqrt(2.0), places=4)

    def test_center_boost_scaling_at_3db_and_6db(self):
        """Test 2: Center boost scales alpha_c to 1.00 at +3dB and 1.414 at +6dB."""
        downmixer_3db = IturBs775Downmixer(center_boost_db=3.0)
        self.assertAlmostEqual(downmixer_3db.alpha_c, 1.0000, places=3)

        downmixer_6db = IturBs775Downmixer(center_boost_db=6.0)
        self.assertAlmostEqual(downmixer_6db.alpha_c, math.sqrt(2.0), places=3)

    def test_anti_clipping_normalization_on_simultaneous_full_scale_channels(self):
        """Test 3: Simultaneous 1.0 peaks across all 5.1 channels output peak <= 1.0 (zero clipping)."""
        downmixer = IturBs775Downmixer(center_boost_db=6.0, enable_normalization=True)
        # Unit peaks on L, R, C, LFE, Ls, Rs
        l_out, r_out = downmixer.downmix_5_1(l=1.0, r=1.0, c=1.0, lfe=1.0, ls=1.0, rs=1.0)
        self.assertLessEqual(l_out, 1.0001, f"Left channel clipped! Output: {l_out}")
        self.assertLessEqual(r_out, 1.0001, f"Right channel clipped! Output: {r_out}")

    def test_stereo_and_mono_streams_bypass_downmixer(self):
        """Test 4: Input buffers with <= 2 channels act as zero-cost passthrough."""
        downmixer = IturBs775Downmixer(center_boost_db=3.0)
        stereo_buffer = [[0.5, 0.75], [-0.5, -0.75]]
        processed = downmixer.process_buffer(stereo_buffer)
        self.assertEqual(processed, stereo_buffer)

    def test_seven_point_one_surround_effective_summation(self):
        """Test 5: 7.1 downmix sums side and rear surrounds effectively without phase cancellation."""
        downmixer = IturBs775Downmixer(center_boost_db=0.0)
        l_out, r_out = downmixer.downmix_7_1(
            l=0.0, r=0.0, c=0.0, lfe=0.0,
            ls=0.5, rs=0.5, rls=0.5, rrs=0.5
        )
        self.assertGreater(l_out, 0.0)
        self.assertGreater(r_out, 0.0)
        self.assertAlmostEqual(l_out, r_out, places=4)


if __name__ == "__main__":
    unittest.main()
