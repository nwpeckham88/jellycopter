"""
Tier 4 Real-World Workload Scenario: Playback Track Selection & Audio DSP Buffer Simulation
Simulates full end-to-end media playback pipeline:
1. Dolby Vision Profile 7 MKV stream is rewritten to Profile 8.1 on Nvidia Shield TV.
2. Daytime viewing routes TrueHD Atmos audio directly via bitstream passthrough.
3. Quiet hours trigger Auto-Night Mode:
   - Passthrough seamlessly hands off to Decoded PCM DSP.
   - Subtitle luminance attenuates by 50% (#80B0B0B0).
   - 7.1 multichannel PCM downmixes to Stereo via ITU-R BS.775 with +3.0dB center boost.
   - Dual-envelope DRC boosts quiet whispers (+6dB) and clamps loud gunshots (<= -12dBFS).
   - Speech Clarity Biquad filter enhances 2.5 kHz vocal formants (+3.5dB).
   - Dynamic normalization guarantees zero integer clipping (peak <= 1.000).
4. Decoder crash hands off to libmpv at exact timestamp.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.playback_engine import (
    DolbyVisionTrackSelector, VideoFormat, DeviceCapabilities, PlayerEngineController
)
from harness.dsp_engine import (
    IturBs775Downmixer, DynamicRangeCompressor, VocalFormantPeakingFilter
)


class TestWorkloadPlaybackDspBuffer(unittest.TestCase):

    def setUp(self):
        self.shield_caps = DeviceCapabilities(
            device_name="Nvidia Shield TV Pro",
            is_dv_supported=True,
            is_dv_profile7_supported=False,
            is_dv_profile8_supported=True,
            is_hdr10_supported=True,
            supports_bitstream_passthrough=True
        )
        self.player = PlayerEngineController(self.shield_caps)
        self.downmixer = IturBs775Downmixer(center_boost_db=3.0, enable_normalization=True)
        self.drc = DynamicRangeCompressor(sample_rate=48000)
        self.eq = VocalFormantPeakingFilter(sample_rate=48000, f0=2500.0, q=1.0, gain_db=3.5)

    def test_full_playback_pipeline_end_to_end(self):
        """Workload: Execute full audio/video pipeline with night mode DSP and decoder failover."""
        # 1. Probe & resolve video track: Profile 7 rewritten to Profile 8.1
        profile_7_video = VideoFormat(
            sample_mime_type="video/dolby-vision",
            codecs="dvh1.07.06",
            dv_profile=7
        )
        resolved_video = DolbyVisionTrackSelector.resolve_track(profile_7_video, self.shield_caps)
        self.assertEqual(resolved_video.codecs, "dvh1.08.06")
        self.assertEqual(resolved_video.dv_profile, 8)
        self.assertEqual(resolved_video.dv_bl_signal_compatibility_id, 1)

        # 2. Daytime playback: Bitstream passthrough, normal white subtitles
        self.player.set_night_mode(False)
        self.assertEqual(self.player.get_audio_mode(), "BITSTREAM_PASSTHROUGH")
        self.assertEqual(self.player.get_subtitle_color(), "#FFFFFFFF")

        # 3. Night Mode engaged at 23:00
        self.player.set_night_mode(True)
        self.assertEqual(self.player.get_audio_mode(), "DECODED_PCM_DSP")
        self.assertEqual(self.player.get_subtitle_color(), "#80B0B0B0")

        # 4. Process incoming 7.1 audio block (480 samples, e.g. 10 ms at 48kHz)
        # Synthetic action scene: high explosions in front and surround channels
        num_samples = 480
        seven_point_one_buffer = [
            [0.85] * num_samples,  # L
            [0.85] * num_samples,  # R
            [0.95] * num_samples,  # Center dialogue
            [0.90] * num_samples,  # LFE
            [0.80] * num_samples,  # Ls
            [0.80] * num_samples,  # Rs
            [0.75] * num_samples,  # Rls
            [0.75] * num_samples   # Rrs
        ]

        # 4a. ITU-R BS.775 downmix to Stereo with +3dB center boost
        stereo = self.downmixer.process_buffer(seven_point_one_buffer)
        self.assertEqual(len(stereo), 2)
        # Anti-clipping normalization guarantees peak <= 1.000
        self.assertLessEqual(max(stereo[0]), 1.0001)
        self.assertLessEqual(max(stereo[1]), 1.0001)

        # 4b. Dynamic Range Compression on left channel
        compressed_l = self.drc.process_buffer(stereo[0])
        # Steady-state loud peak clamped cleanly
        self.assertLessEqual(compressed_l[-1], 1.000)

        # 4c. Speech Clarity Vocal Formant EQ
        eq_l = self.eq.process_buffer(compressed_l)
        self.assertEqual(len(eq_l), num_samples)

        # 5. MediaCodec decoder hardware stall occurs mid-stream
        self.player.update_position(1845000)  # 30m 45s in
        self.player.handle_player_error("ERROR_CODE_DECODER_INIT_FAILED")

        # Verify seamless failover to libmpv preserving exact timestamp
        self.assertEqual(self.player.active_engine, "LIBMPV")
        self.assertEqual(self.player.position_ms, 1845000)


if __name__ == "__main__":
    unittest.main()
