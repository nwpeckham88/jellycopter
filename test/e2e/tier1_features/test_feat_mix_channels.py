"""
Tier 1 Feature Tests: Multi-Show Mix Channels
Requirements: R1, ORIGINAL_REQUEST.md § R1, PROJECT.md Feature 6.
Verifies hierarchical 2-level anti-clustering, zero adjacent show collisions,
and independent asynchronous sub-deck recycling.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem


class TestFeatureMixChannels(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        # Create 3 shows with varying episode counts:
        # Show A: 20 episodes
        # Show B: 10 episodes (short show)
        # Show C: 15 episodes
        self.show_a = [EpisodeItem(f"a_{i}", f"Show A Ep {i}", "show_a", f"s_{(i%3)+1}", (i%3)+1, i) for i in range(1, 21)]
        self.show_b = [EpisodeItem(f"b_{i}", f"Show B Ep {i}", "show_b", f"s_{(i%2)+1}", (i%2)+1, i) for i in range(1, 11)]
        self.show_c = [EpisodeItem(f"c_{i}", f"Show C Ep {i}", "show_c", f"s_{(i%2)+1}", (i%2)+1, i) for i in range(1, 16)]

    def test_zero_adjacent_show_repetition(self):
        """Test 1: Level 1 anti-clustering ensures no two adjacent episodes are from the same show."""
        shows = [
            ("show_a", "Show A", self.show_a, 1),
            ("show_b", "Show B", self.show_b, 1),
            ("show_c", "Show C", self.show_c, 1)
        ]
        deck = self.engine.generate_mix_channel_deck("cartoons_mix", "Saturday Cartoons", shows, target_total_episodes=40)
        for i in range(len(deck.cards) - 1):
            show_i = deck.cards[i].series_id
            show_next = deck.cards[i + 1].series_id
            self.assertNotEqual(
                show_i, show_next,
                f"Adjacent cards at slot {i} and {i+1} have the same show ID: {show_i}"
            )

    def test_independent_sub_deck_asynchronous_recycling(self):
        """Test 2: Short show (10 eps) recycles its sub-deck independently across a 50-episode channel mix."""
        shows = [
            ("show_a", "Show A", self.show_a, 1),
            ("show_b", "Show B", self.show_b, 1),  # Only 10 episodes
            ("show_c", "Show C", self.show_c, 1)
        ]
        # In 50 episodes, show B will be picked ~16 times (>10), requiring its sub-deck to reset cleanly
        deck = self.engine.generate_mix_channel_deck("mix_channel", "Morning Block", shows, target_total_episodes=50)
        show_b_cards = [c for c in deck.cards if c.series_id == "show_b"]
        self.assertGreater(len(show_b_cards), 10, "Show B should have been served more than 10 times via recycling")
        # Ensure show B cards are valid and unbroken
        for c in show_b_cards:
            self.assertEqual(c.series_id, "show_b")

    def test_show_weight_proportional_frequency(self):
        """Test 3: Show with weight 2 appears with higher frequency than show with weight 1."""
        shows = [
            ("show_a", "Show A", self.show_a, 2),  # Weight 2
            ("show_b", "Show B", self.show_b, 1),  # Weight 1
            ("show_c", "Show C", self.show_c, 1)   # Weight 1
        ]
        total_a = 0
        total_b = 0
        total_c = 0
        for _ in range(5):
            deck = self.engine.generate_mix_channel_deck("weighted_mix", "Weighted Channel", shows, target_total_episodes=100)
            total_a += sum(1 for c in deck.cards if c.series_id == "show_a")
            total_b += sum(1 for c in deck.cards if c.series_id == "show_b")
            total_c += sum(1 for c in deck.cards if c.series_id == "show_c")

        self.assertGreater(total_a, total_b, f"Show A ({total_a}) was not more frequent than Show B ({total_b})")
        self.assertGreater(total_a, total_c, f"Show A ({total_a}) was not more frequent than Show C ({total_c})")

    def test_within_show_season_balancing_is_maintained(self):
        """Test 4: Episodes from Show A inside the mix channel still adhere to season anti-clustering."""
        shows = [
            ("show_a", "Show A", self.show_a, 1),
            ("show_b", "Show B", self.show_b, 1),
            ("show_c", "Show C", self.show_c, 1)
        ]
        deck = self.engine.generate_mix_channel_deck("mix_season", "Mix", shows, target_total_episodes=45)
        show_a_cards = [c for c in deck.cards if c.series_id == "show_a"]
        for i in range(len(show_a_cards) - 1):
            self.assertNotEqual(
                show_a_cards[i].category_key, show_a_cards[i + 1].category_key,
                f"Show A sub-deck had adjacent season repeat at index {i}"
            )

    def test_multipart_grouping_preserved_inside_mix_channels(self):
        """Test 5: Multi-part episodes inside a participating show remain contiguous in the mix channel."""
        show_with_parts = [
            EpisodeItem("mp_1", "Space Arc, Part 1", "show_mp", "s1", 1, 1),
            EpisodeItem("mp_2", "Space Arc, Part 2", "show_mp", "s1", 1, 2),
            EpisodeItem("mp_3", "Standalone Ep", "show_mp", "s1", 1, 3),
        ]
        shows = [
            ("show_mp", "Show MP", show_with_parts, 1),
            ("show_b", "Show B", self.show_b, 1)
        ]
        deck = self.engine.generate_mix_channel_deck("mp_mix", "MP Mix", shows, target_total_episodes=15)
        mp_cards = [c for c in deck.cards if c.is_multi_part]
        self.assertGreaterEqual(len(mp_cards), 1)
        self.assertEqual(len(mp_cards[0].items), 2)
        self.assertEqual(mp_cards[0].items[0].name, "Space Arc, Part 1")
        self.assertEqual(mp_cards[0].items[1].name, "Space Arc, Part 2")


if __name__ == "__main__":
    unittest.main()
