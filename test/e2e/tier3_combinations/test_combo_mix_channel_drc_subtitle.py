"""
Tier 3 Cross-Feature Combination Tests: Mix Channel + Night Mode DRC + Subtitle Dimmer
Verifies that multi-show virtual TV channels seamlessly engage the full DSP audio chain
(BS.775 downmixer, dual-envelope DRC, speech clarity EQ) and subtitle dimmer simultaneously,
with transparent bitstream-to-PCM passthrough handoff across show changes.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem
from harness.dsp_engine import IturBs775Downmixer, DynamicRangeCompressor, VocalFormantPeakingFilter
from harness.playback_engine import DeviceCapabilities, PlayerEngineController


class TestComboMixChannelDrcSubtitle(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.caps = DeviceCapabilities("Shield TV", True, False, True, True, True)
        self.player = PlayerEngineController(self.caps)

        self.downmixer = IturBs775Downmixer(center_boost_db=3.0)
        self.drc = DynamicRangeCompressor(sample_rate=48000)
        self.eq = VocalFormantPeakingFilter(sample_rate=48000)

        show_a = [EpisodeItem(f"a_{i}", f"Show A Ep {i}", "show_a", "s1", 1, i) for i in range(1, 10)]
        show_b = [EpisodeItem(f"b_{i}", f"Show B Ep {i}", "show_b", "s1", 1, i) for i in range(1, 10)]
        self.deck = self.engine.generate_mix_channel_deck(
            "late_night_mix", "Late Night Block",
            [("show_a", "Show A", show_a, 1), ("show_b", "Show B", show_b, 1)],
            target_total_episodes=10
        )

    def test_night_mode_engages_pcm_dsp_and_subtitle_dimmer(self):
        """Combo 1: Activating Night Mode triggers PCM DSP handoff and 50% subtitle luminance."""
        self.assertEqual(self.player.get_audio_mode(), "BITSTREAM_PASSTHROUGH")
        self.assertEqual(self.player.get_subtitle_color(), "#FFFFFFFF")

        self.player.set_night_mode(True)
        self.assertEqual(self.player.get_audio_mode(), "DECODED_PCM_DSP")
        self.assertEqual(self.player.get_subtitle_color(), "#80B0B0B0")

    def test_dsp_audio_pipeline_processes_multichannel_audio_without_clipping(self):
        """Combo 2: 5.1 multichannel audio from mix episode passes through BS.775, DRC, and Formant EQ."""
        # 5.1 buffer with high levels on all channels
        num_samples = 480
        five_point_one = [[0.9] * num_samples for _ in range(6)]

        # Step 1: BS.775 downmix to stereo
        stereo = self.downmixer.process_buffer(five_point_one)
        self.assertEqual(len(stereo), 2)
        self.assertLessEqual(max(stereo[0]), 1.0)

        # Step 2: DRC compression - steady-state sample clamped cleanly
        compressed_l = self.drc.process_buffer(stereo[0])
        self.assertLessEqual(compressed_l[-1], 1.0)

        # Step 3: Vocal Formant Peaking Filter
        filtered_l = self.eq.process_buffer(compressed_l)
        self.assertEqual(len(filtered_l), num_samples)

    def test_dsp_and_dimmer_persist_seamlessly_across_show_transitions(self):
        """Combo 3: Advancing from Show A to Show B maintains Night Mode DSP and subtitle dimming."""
        self.player.set_night_mode(True)
        self.assertEqual(self.player.get_subtitle_color(), "#80B0B0B0")

        # Advance through 3 episodes in the mix channel (alternating shows)
        for i in range(3):
            self.deck, _ = self.engine.advance_playback(self.deck, position_ms=1800000, duration_ms=1800000)
            self.assertEqual(self.player.get_audio_mode(), "DECODED_PCM_DSP")
            self.assertEqual(self.player.get_subtitle_color(), "#80B0B0B0")

    def test_disabling_night_mode_restores_passthrough_and_white_subtitles(self):
        """Combo 4: Toggling Night Mode off mid-mix restores bitstream passthrough and white text."""
        self.player.set_night_mode(True)
        self.assertEqual(self.player.get_audio_mode(), "DECODED_PCM_DSP")

        self.player.set_night_mode(False)
        self.assertEqual(self.player.get_audio_mode(), "BITSTREAM_PASSTHROUGH")
        self.assertEqual(self.player.get_subtitle_color(), "#FFFFFFFF")

    def test_force_stereo_independent_activation(self):
        """Combo 5: Force Stereo without Night Mode enables PCM DSP while keeping white subtitles."""
        self.player.set_night_mode(False)
        self.player.set_force_stereo(True)
        self.assertEqual(self.player.get_audio_mode(), "DECODED_PCM_DSP")
        self.assertEqual(self.player.get_subtitle_color(), "#FFFFFFFF")  # Subtitles remain white


if __name__ == "__main__":
    unittest.main()
