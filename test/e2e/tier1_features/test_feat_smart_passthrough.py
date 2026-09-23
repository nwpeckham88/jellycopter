"""
Tier 1 Feature Tests: Smart Passthrough Handoff, Scheduler & Subtitle Dimmer
Requirements: R2, ORIGINAL_REQUEST.md § R2, PROJECT.md Features 14 & 15.
Verifies bitstream-to-PCM DSP state transitions, time-based scheduler with midnight rollover,
and 50% subtitle luminance attenuation.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
from datetime import time as dtime
from harness.playback_engine import (
    DeviceCapabilities, PlayerEngineController, AutoNightModeScheduler
)


class TestFeatureSmartPassthrough(unittest.TestCase):

    def setUp(self):
        self.caps = DeviceCapabilities(
            device_name="Shield TV",
            is_dv_supported=True,
            is_dv_profile7_supported=False,
            is_dv_profile8_supported=True,
            is_hdr10_supported=True,
            supports_bitstream_passthrough=True
        )
        self.controller = PlayerEngineController(self.caps)

    def test_normal_playback_uses_bitstream_passthrough(self):
        """Test 1: When Night Mode and Force Stereo are disabled, audio mode is BITSTREAM_PASSTHROUGH."""
        self.controller.set_night_mode(False)
        self.controller.set_force_stereo(False)
        self.assertEqual(self.controller.get_audio_mode(), "BITSTREAM_PASSTHROUGH")

    def test_night_mode_triggers_pcm_dsp_handoff(self):
        """Test 2: Enabling Night Mode transparently hands off to DECODED_PCM_DSP."""
        self.controller.set_night_mode(True)
        self.assertEqual(self.controller.get_audio_mode(), "DECODED_PCM_DSP")

    def test_force_stereo_triggers_pcm_dsp_handoff(self):
        """Test 3: Enabling Force Stereo independently hands off to DECODED_PCM_DSP."""
        self.controller.set_night_mode(False)
        self.controller.set_force_stereo(True)
        self.assertEqual(self.controller.get_audio_mode(), "DECODED_PCM_DSP")

    def test_auto_night_scheduler_cross_midnight_evaluation(self):
        """Test 4: Scheduler spanning 22:30 to 07:00 correctly triggers during night hours."""
        scheduler = AutoNightModeScheduler(
            start_time=dtime(22, 30),
            end_time=dtime(7, 0),
            enabled=True
        )
        # 23:15 is night
        self.assertTrue(scheduler.is_night_time(dtime(23, 15)))
        # 02:45 is night
        self.assertTrue(scheduler.is_night_time(dtime(2, 45)))
        # 06:59 is night
        self.assertTrue(scheduler.is_night_time(dtime(6, 59)))
        # 14:00 is daytime
        self.assertFalse(scheduler.is_night_time(dtime(14, 0)))

    def test_subtitle_luminance_dimmer_activates_in_night_mode(self):
        """Test 5: Subtitle color is white during day (#FFFFFFFF) and dimmed grey (#80B0B0B0) at night."""
        self.controller.set_night_mode(False)
        self.assertEqual(self.controller.get_subtitle_color(), "#FFFFFFFF")

        self.controller.set_night_mode(True)
        self.assertEqual(self.controller.get_subtitle_color(), "#80B0B0B0")


if __name__ == "__main__":
    unittest.main()
