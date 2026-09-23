"""
Tier 3 Cross-Feature Combination Tests: Shuffle Deck + Dolby Vision Playback Fallback
Verifies that playing a shuffled exhaustion deck containing Dolby Vision Profile 7 files
correctly invokes the Profile 8.1 rewriter on Shield TV, retains deck progression,
and handles libmpv engine fallback without losing deck state.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem
from harness.playback_engine import (
    DolbyVisionTrackSelector, VideoFormat, DeviceCapabilities, PlayerEngineController
)


class TestComboShuffleDvPlayback(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.shield_caps = DeviceCapabilities(
            device_name="Shield TV Pro",
            is_dv_supported=True,
            is_dv_profile7_supported=False,
            is_dv_profile8_supported=True,
            is_hdr10_supported=True
        )
        self.episodes = [
            EpisodeItem(f"dv_ep_{i}", f"DV Episode {i}", "show_dv", "s1", 1, i)
            for i in range(1, 15)
        ]
        self.deck = self.engine.generate_single_show_deck("show_dv", "Dolby Vision Show", self.episodes)
        self.player = PlayerEngineController(self.shield_caps)

    def test_shuffle_card_resolves_to_profile_8_on_shield(self):
        """Combo 1: Playing a shuffle card with Profile 7 MKV stream resolves to Profile 8.1."""
        current_card = self.deck.cards[self.deck.current_index]
        self.assertEqual(current_card.id, self.deck.cards[0].id)

        # Profile 7 stream metadata
        raw_fmt = VideoFormat(
            sample_mime_type="video/dolby-vision",
            codecs="dvh1.07.06",
            dv_profile=7
        )
        resolved_fmt = DolbyVisionTrackSelector.resolve_track(raw_fmt, self.shield_caps)
        self.assertEqual(resolved_fmt.codecs, "dvh1.08.06")
        self.assertEqual(resolved_fmt.dv_profile, 8)

    def test_decoder_crash_during_shuffle_handoffs_to_mpv_preserving_deck_position(self):
        """Combo 2: MediaCodec crash during shuffle playback hands off to libmpv at exact timestamp."""
        # 12 minutes into episode
        self.player.update_position(720000)
        self.assertEqual(self.player.active_engine, "MEDIA3")

        # Decoder initialization crashes on Profile 7 FEL residual data
        self.player.handle_player_error("ERROR_CODE_DECODER_INIT_FAILED")
        self.assertEqual(self.player.active_engine, "LIBMPV")
        self.assertEqual(self.player.position_ms, 720000)

        # Ensure deck progression state was not corrupted by the engine switch
        self.assertEqual(self.deck.current_index, 0)

    def test_deck_advances_normally_after_mpv_fallback(self):
        """Combo 3: After falling back to libmpv, completing episode advances deck to card index 1."""
        self.player.handle_player_error("ERROR_CODE_DECODER_INIT_FAILED")
        self.assertEqual(self.player.active_engine, "LIBMPV")

        # Playback finishes on libmpv engine
        self.deck, event = self.engine.advance_playback(
            self.deck, position_ms=1750000, duration_ms=1800000, is_user_skip=False
        )
        self.assertEqual(event, "CONSUMED_NATURAL")
        self.assertEqual(self.deck.current_index, 1)

    def test_non_dv_display_cleans_dv_stream_while_shuffling(self):
        """Combo 4: On non-DV HDR10 display, shuffle stream is cleanly converted to standard HDR10."""
        hdr10_caps = DeviceCapabilities("HDR TV", False, False, False, True)
        raw_fmt = VideoFormat("video/dolby-vision", "dvh1.07.06", dv_profile=7)
        resolved = DolbyVisionTrackSelector.resolve_track(raw_fmt, hdr10_caps)
        self.assertEqual(resolved.sample_mime_type, "video/hevc")
        self.assertEqual(resolved.color_space, "BT2020")

    def test_deck_cycle_completes_over_dv_streams(self):
        """Combo 5: Entire 14-episode DV deck can be cycled through with zero track selection errors."""
        for _ in range(14):
            # Resolve track for each episode
            fmt = DolbyVisionTrackSelector.resolve_track(
                VideoFormat("video/dolby-vision", "dvh1.07.06", dv_profile=7),
                self.shield_caps
            )
            self.assertEqual(fmt.dv_profile, 8)
            self.deck, _ = self.engine.advance_playback(self.deck, position_ms=1800000, duration_ms=1800000)

        self.assertEqual(self.deck.current_index, 0)
        self.assertEqual(self.deck.cycle_number, 2)


if __name__ == "__main__":
    unittest.main()
