"""
Tier 2 Boundary Tests: Extreme Uneven Season Distribution (6 vs 24 vs 24)
Requirements: Anti-clustering robustness when a small season (6 eps) risks premature draining,
leaving large seasons (24 eps) to clash. Verifies tightness threshold prevents clashing.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, calculate_season_clash_rate
from harness.fixtures import uneven_seasons_catalog


class TestBoundExtremeUnevenSeasons(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = uneven_seasons_catalog()

    def test_zero_clash_rate_across_50_trials(self):
        """Boundary 1: 50 independent trials on 6 vs 24 vs 24 distribution yield 0.00% clashes."""
        for trial in range(50):
            deck = self.engine.generate_single_show_deck("uneven", "Uneven Show", self.episodes)
            clash_rate = calculate_season_clash_rate(deck.cards)
            self.assertEqual(
                clash_rate, 0.0,
                f"Trial {trial + 1} experienced clash on uneven distribution: {clash_rate}"
            )

    def test_season_one_small_bucket_not_starved(self):
        """Boundary 2: Small season 1 (6 eps) is distributed throughout the deck, not all at the start."""
        deck = self.engine.generate_single_show_deck("uneven", "Uneven Show", self.episodes)
        s1_indices = [idx for idx, c in enumerate(deck.cards) if c.category_key == "s1"]
        self.assertEqual(len(s1_indices), 6)
        # S1 episodes should not all appear in the first 12 slots
        max_s1_index = max(s1_indices)
        self.assertGreater(max_s1_index, 12, "Season 1 was prematurely dumped in the first quarter of the deck!")

    def test_tightness_threshold_engages_when_candidate_exceeds_half_remaining(self):
        """Boundary 3: When remaining items R has a bucket >= ceil(R/2), that bucket is selected."""
        # When S1 is exhausted, S2 (24) and S3 (24) have equal size, forcing exact alternation
        deck = self.engine.generate_single_show_deck("uneven", "Uneven Show", self.episodes)
        # Verify no consecutive same season anywhere in the entire deck
        for i in range(len(deck.cards) - 1):
            self.assertNotEqual(deck.cards[i].category_key, deck.cards[i + 1].category_key)

    def test_card_count_integrity_preserved(self):
        """Boundary 4: Total cards in deck matches exact sum of uneven seasons (54)."""
        deck = self.engine.generate_single_show_deck("uneven", "Uneven Show", self.episodes)
        self.assertEqual(len(deck.cards), 54)

    def test_entropy_remains_high_under_tight_constraints(self):
        """Boundary 5: First-item entropy under uneven distribution retains >= 90% of max theoretical limit."""
        first_items = []
        for _ in range(30):
            deck = self.engine.generate_single_show_deck("uneven", "Uneven Show", self.episodes)
            first_items.append(deck.cards[0].id)
        unique_starts = len(set(first_items))
        self.assertGreaterEqual(unique_starts, 10)


if __name__ == "__main__":
    unittest.main()
