"""
Tier 1 Feature Tests: Season Anti-Clustering & Round-Robin Distribution
Requirements: R1, ORIGINAL_REQUEST.md § R1, PROJECT.md Feature 5.
Verifies tightness-aware bucket round-robin, zero adjacent season collisions,
and high entropy preservation.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import random
from harness.shuffle_engine import SmartShuffleEngine, calculate_season_clash_rate
from harness.fixtures import bobs_burgers_catalog, uneven_seasons_catalog


class TestFeatureAntiClustering(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()

    def test_bobs_burgers_zero_adjacent_season_clashes(self):
        """Test 1: 14-season Bob's Burgers catalog achieves 0.00% adjacent season clashes."""
        episodes = bobs_burgers_catalog()
        for trial in range(25):
            deck = self.engine.generate_single_show_deck("bb", "Bob's Burgers", episodes)
            clash_rate = calculate_season_clash_rate(deck.cards)
            self.assertEqual(
                clash_rate, 0.0,
                f"Trial {trial + 1} produced adjacent season repeat! Clash rate: {clash_rate}"
            )

    def test_tightness_aware_selection_prevents_uneven_season_deadlock(self):
        """Test 2: Uneven seasons (6 vs 24 vs 24) achieve 0.00% clashes via tightness threshold."""
        episodes = uneven_seasons_catalog()
        # N=54, max(24) <= ceil(54/2) = 27. Feasible for 0 clashes.
        for trial in range(25):
            deck = self.engine.generate_single_show_deck("uneven", "Uneven Show", episodes)
            clash_rate = calculate_season_clash_rate(deck.cards)
            self.assertEqual(
                clash_rate, 0.0,
                f"Trial {trial + 1} had clash on uneven distribution: {clash_rate}"
            )

    def test_no_two_consecutive_identical_category_keys(self):
        """Test 3: Checks explicitly that category_key(i) != category_key(i+1) across all slots."""
        episodes = bobs_burgers_catalog()
        deck = self.engine.generate_single_show_deck("bb", "Bob's Burgers", episodes)
        for i in range(len(deck.cards) - 1):
            curr_cat = deck.cards[i].category_key
            next_cat = deck.cards[i + 1].category_key
            self.assertNotEqual(
                curr_cat, next_cat,
                f"Adjacent cards at index {i} and {i+1} have identical category key: {curr_cat}"
            )

    def test_season_representation_is_interleaved(self):
        """Test 4: Each window of 5 cards contains episodes from distinct seasons (>=2 distinct seasons, 0 clashes)."""
        episodes = bobs_burgers_catalog()
        seeded_engine = SmartShuffleEngine(rng=random.Random(42))
        deck = seeded_engine.generate_single_show_deck("bb", "Bob's Burgers", episodes)
        for i in range(len(deck.cards) - 5):
            window = deck.cards[i:i + 5]
            distinct_seasons = set(c.category_key for c in window)
            self.assertGreaterEqual(
                len(distinct_seasons), 2,
                f"Window starting at {i} only contains {len(distinct_seasons)} distinct seasons"
            )

    def test_noise_perturbation_preserves_entropy_across_runs(self):
        """Test 5: Anti-clustering does not produce deterministic repeating season sequences."""
        episodes = bobs_burgers_catalog()
        deck1 = self.engine.generate_single_show_deck("bb", "Bob's Burgers", episodes)
        deck2 = self.engine.generate_single_show_deck("bb", "Bob's Burgers", episodes)

        keys1 = [c.category_key for c in deck1.cards[:30]]
        keys2 = [c.category_key for c in deck2.cards[:30]]
        self.assertNotEqual(keys1, keys2, "Two independent decks produced identical season sequences!")


if __name__ == "__main__":
    unittest.main()
