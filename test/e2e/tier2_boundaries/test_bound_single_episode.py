"""
Tier 2 Boundary Tests: Single-Episode Show Handling
Requirements: Robustness under miniseries, 1-episode standalone specials, and minimal states.
Verifies single episode deck generation, boundary swap bypass, cycle completion, and repeat protection.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine
from harness.fixtures import single_episode_show_catalog


class TestBoundSingleEpisode(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = single_episode_show_catalog()

    def test_single_episode_generates_one_card(self):
        """Boundary 1: Single episode catalog produces exactly 1 MediaCard."""
        deck = self.engine.generate_single_show_deck("miniseries", "Miniseries", self.episodes)
        self.assertEqual(len(deck.cards), 1)
        self.assertEqual(deck.cards[0].id, "single_1")

    def test_single_episode_advance_triggers_cycle_reset(self):
        """Boundary 2: Playing the single episode consumes it and triggers cycle 2."""
        deck = self.engine.generate_single_show_deck("miniseries", "Miniseries", self.episodes)
        deck, event = self.engine.advance_playback(
            deck,
            raw_episodes_provider=lambda sid: self.episodes,
            position_ms=1800000,
            duration_ms=1800000
        )
        self.assertEqual(event, "CYCLE_RESET")
        self.assertEqual(deck.cycle_number, 2)
        self.assertEqual(deck.current_index, 0)

    def test_cross_cycle_swap_bypassed_for_single_item(self):
        """Boundary 3: Cross-cycle boundary swap is cleanly bypassed when N <= 3."""
        cards = self.engine.generate_cross_cycle_deck(
            self.episodes, prev_deck_order=["single_1"], cycle_number=2
        )
        self.assertEqual(len(cards), 1)
        self.assertEqual(cards[0].id, "single_1")

    def test_single_episode_cannot_trigger_multipart_grouping(self):
        """Boundary 4: Single episode titled 'Pilot and Finale' is not flagged as multi-part."""
        cards = self.engine.build_media_cards(self.episodes)
        self.assertEqual(len(cards), 1)
        self.assertFalse(cards[0].is_multi_part)

    def test_single_episode_in_mix_channel(self):
        """Boundary 5: Single episode show in a mix channel repeats safely across the channel."""
        other_eps = [self.episodes[0].__class__("other_1", "Other Ep", "other", "s1", 1, 1)]
        shows = [
            ("miniseries", "Miniseries", self.episodes, 1),
            ("other", "Other", other_eps, 1)
        ]
        deck = self.engine.generate_mix_channel_deck("mix", "Mix", shows, target_total_episodes=6)
        self.assertEqual(len(deck.cards), 6)
        # Verifies anti-clustering still alternates between the two 1-episode shows
        for i in range(len(deck.cards) - 1):
            self.assertNotEqual(deck.cards[i].series_id, deck.cards[i + 1].series_id)


if __name__ == "__main__":
    unittest.main()
