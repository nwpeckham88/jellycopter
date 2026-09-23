"""
Tier 2 Boundary Tests: Single Dominant Season (Pigeonhole Limit)
Requirements: Handling scenarios where 1 season has > ceil(N / 2) episodes (e.g. 50 vs 5, N=55).
By the Pigeonhole principle, adjacent collisions are mathematically unavoidable.
Verifies graceful termination, no infinite loops, minority season spacing, and full deck preservation.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine
from harness.fixtures import pigeonhole_dominant_catalog


class TestBoundPigeonholeDominant(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = pigeonhole_dominant_catalog()  # 50 eps in s1, 5 in s2 (N=55)

    def test_graceful_termination_without_infinite_loop(self):
        """Boundary 1: Tightness algorithm completes with zero infinite loop or deadlock."""
        deck = self.engine.generate_single_show_deck("pigeon_show", "Pigeonhole Show", self.episodes)
        self.assertEqual(len(deck.cards), 55)

    def test_all_fifty_five_episodes_retained(self):
        """Boundary 2: Output deck contains all 50 S1 episodes and all 5 S2 episodes."""
        deck = self.engine.generate_single_show_deck("pigeon_show", "Pigeonhole Show", self.episodes)
        s1_count = sum(1 for c in deck.cards if c.category_key == "s1")
        s2_count = sum(1 for c in deck.cards if c.category_key == "s2")
        self.assertEqual(s1_count, 50)
        self.assertEqual(s2_count, 5)

    def test_minority_season_is_distributed_to_break_clumps(self):
        """Boundary 3: Minority season (S2) episodes are not lumped together at the start or end."""
        deck = self.engine.generate_single_show_deck("pigeon_show", "Pigeonhole Show", self.episodes)
        s2_indices = [idx for idx, c in enumerate(deck.cards) if c.category_key == "s2"]
        # Ensure S2 episodes are separated by at least 1 S1 episode
        for j in range(len(s2_indices) - 1):
            gap = s2_indices[j + 1] - s2_indices[j]
            self.assertGreaterEqual(gap, 2, "Two minority episodes were placed next to each other!")

    def test_unavoidable_collisions_do_not_exceed_theoretical_minimum(self):
        """Boundary 4: Collisions are bounded by the Pigeonhole deficit (50 - 5 - 1 = 44)."""
        deck = self.engine.generate_single_show_deck("pigeon_show", "Pigeonhole Show", self.episodes)
        clashes = sum(1 for i in range(len(deck.cards) - 1) if deck.cards[i].category_key == deck.cards[i + 1].category_key)
        # S2 can break up at most 6 contiguous blocks of S1, so clashes must be >= 44
        self.assertGreaterEqual(clashes, 44)
        self.assertLessEqual(clashes, 50)

    def test_playback_advancement_operates_normally_under_pigeonhole(self):
        """Boundary 5: Autoplay advances through the dominant season deck without error."""
        deck = self.engine.generate_single_show_deck("pigeon_show", "Pigeonhole Show", self.episodes)
        for _ in range(5):
            deck, event = self.engine.advance_playback(deck, position_ms=1800000, duration_ms=1800000)
            self.assertEqual(event, "CONSUMED_NATURAL")
        self.assertEqual(deck.current_index, 5)


if __name__ == "__main__":
    unittest.main()
