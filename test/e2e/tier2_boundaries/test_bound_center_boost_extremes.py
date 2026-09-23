"""
Tier 2 Boundary Tests: 0dB and Maximum Center Boost Extremes
Requirements: ITU-R BS.775 center dialogue boost clamping and anti-clipping stability.
Verifies boundary values 0.0 dB, 6.0 dB, negative clamping, overshoot clamping, and normalization.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import math
from harness.dsp_engine import IturBs775Downmixer


class TestBoundCenterBoostExtremes(unittest.TestCase):

    def test_zero_db_lower_bound(self):
        """Boundary 1: Center boost at 0.0 dB produces alpha_c = 1/sqrt(2) approx 0.7071."""
        downmixer = IturBs775Downmixer(center_boost_db=0.0)
        self.assertAlmostEqual(downmixer.alpha_c, 1.0 / math.sqrt(2.0), places=4)

    def test_six_db_upper_bound(self):
        """Boundary 2: Center boost at 6.0 dB produces alpha_c = sqrt(2) approx 1.4142."""
        downmixer = IturBs775Downmixer(center_boost_db=6.0)
        self.assertAlmostEqual(downmixer.alpha_c, math.sqrt(2.0), places=4)

    def test_negative_boost_clamped_to_zero(self):
        """Boundary 3: Passing negative value (-5.0 dB) clamps center boost to 0.0 dB."""
        downmixer = IturBs775Downmixer(center_boost_db=-5.0)
        self.assertEqual(downmixer.center_boost_db, 0.0)
        self.assertAlmostEqual(downmixer.alpha_c, 1.0 / math.sqrt(2.0), places=4)

    def test_excessive_boost_clamped_to_six(self):
        """Boundary 4: Passing overshoot value (+20.0 dB) clamps center boost to 6.0 dB."""
        downmixer = IturBs775Downmixer(center_boost_db=20.0)
        self.assertEqual(downmixer.center_boost_db, 6.0)
        self.assertAlmostEqual(downmixer.alpha_c, math.sqrt(2.0), places=4)

    def test_anti_clipping_at_all_boundary_levels(self):
        """Boundary 5: Normalization prevents clipping at both 0dB and 6dB with unit channel amplitudes."""
        for boost in [0.0, 1.5, 3.0, 4.5, 6.0]:
            dm = IturBs775Downmixer(center_boost_db=boost, enable_normalization=True)
            l_out, r_out = dm.downmix_5_1(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
            self.assertLessEqual(l_out, 1.0001, f"Clipping detected at boost {boost} dB: {l_out}")
            self.assertLessEqual(r_out, 1.0001, f"Clipping detected at boost {boost} dB: {r_out}")


if __name__ == "__main__":
    unittest.main()
