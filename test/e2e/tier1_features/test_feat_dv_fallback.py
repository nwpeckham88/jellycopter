"""
Tier 1 Feature Tests: Dolby Vision Profile 7 -> 8.1 Rewriting & Player Fallback
Requirements: R2, ORIGINAL_REQUEST.md § R2, PROJECT.md Features 9 & 10.
Verifies Profile 7 rewriting, clean HDR10 base layer fallback (fixing green/purple tint),
and Media3 -> libmpv automated engine handoff.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
from harness.playback_engine import (
    DolbyVisionTrackSelector, VideoFormat, DeviceCapabilities, PlayerEngineController
)


class TestFeatureDolbyVisionFallback(unittest.TestCase):

    def setUp(self):
        # Shield TV / Onn 4K Pro capability profile
        self.shield_caps = DeviceCapabilities(
            device_name="Nvidia Shield TV Pro",
            is_dv_supported=True,
            is_dv_profile7_supported=False,
            is_dv_profile8_supported=True,
            is_hdr10_supported=True
        )
        # HDR10-only TV (no Dolby Vision license)
        self.hdr10_tv_caps = DeviceCapabilities(
            device_name="Samsung QLED HDR10",
            is_dv_supported=False,
            is_dv_profile7_supported=False,
            is_dv_profile8_supported=False,
            is_hdr10_supported=True
        )

    def test_shield_rewrites_profile_7_to_profile_8_1(self):
        """Test 1: Profile 7 MKV stream is rewritten to Profile 8.1 on Shield TV (eliminating purple tint)."""
        input_fmt = VideoFormat(
            sample_mime_type="video/dolby-vision",
            codecs="dvh1.07.06",
            dv_profile=7,
            dv_bl_signal_compatibility_id=6
        )
        resolved = DolbyVisionTrackSelector.resolve_track(input_fmt, self.shield_caps)
        self.assertEqual(resolved.sample_mime_type, "video/dolby-vision")
        self.assertEqual(resolved.codecs, "dvh1.08.06")
        self.assertEqual(resolved.dv_profile, 8)
        self.assertEqual(resolved.dv_bl_signal_compatibility_id, 1)

    def test_hdr10_tv_clean_base_layer_fallback(self):
        """Test 2: Non-DV display cleanly strips DV metadata and selects standard HDR10 HEVC BT.2020 PQ."""
        input_fmt = VideoFormat(
            sample_mime_type="video/dolby-vision",
            codecs="dvh1.07.06",
            dv_profile=7,
            dv_bl_signal_compatibility_id=6
        )
        resolved = DolbyVisionTrackSelector.resolve_track(input_fmt, self.hdr10_tv_caps)
        self.assertEqual(resolved.sample_mime_type, "video/hevc")
        self.assertIsNone(resolved.dv_profile)
        self.assertIsNone(resolved.dv_bl_signal_compatibility_id)
        self.assertEqual(resolved.color_space, "BT2020")
        self.assertEqual(resolved.color_transfer, "SMPTE_ST_2084_PQ")

    def test_non_dv_formats_unmodified(self):
        """Test 3: Standard H.264 or Profile 8 streams are passed through without modification."""
        standard_h264 = VideoFormat(sample_mime_type="video/avc", codecs="avc1.640028")
        resolved = DolbyVisionTrackSelector.resolve_track(standard_h264, self.shield_caps)
        self.assertEqual(resolved.sample_mime_type, "video/avc")
        self.assertEqual(resolved.codecs, "avc1.640028")

    def test_decoder_init_failure_triggers_seamless_mpv_handoff(self):
        """Test 4: MediaCodec decoder initialization crash automatically switches to libmpv."""
        controller = PlayerEngineController(self.shield_caps)
        controller.update_position(142500)  # 2m 22.5s in
        self.assertEqual(controller.active_engine, "MEDIA3")

        # Simulate hardware decoder failure
        controller.handle_player_error("ERROR_CODE_DECODER_INIT_FAILED")
        self.assertEqual(controller.active_engine, "LIBMPV")
        self.assertTrue(controller.fallback_occurred)
        self.assertEqual(controller.position_ms, 142500)  # Exact timestamp preserved!

    def test_excessive_frame_drops_triggers_mpv_fallback(self):
        """Test 5: Greater than 30 sustained dropped frames in 5s triggers automated libmpv fallback."""
        controller = PlayerEngineController(self.shield_caps)
        controller.update_position(50000)

        # 25 drops -> stays on Media3
        controller.record_dropped_frames(25)
        self.assertEqual(controller.active_engine, "MEDIA3")

        # 10 more drops (total 35 > 30) -> triggers fallback to libmpv
        controller.record_dropped_frames(10)
        self.assertEqual(controller.active_engine, "LIBMPV")
        self.assertIn("SURPASSED_30", controller.fallback_reason)


if __name__ == "__main__":
    unittest.main()
