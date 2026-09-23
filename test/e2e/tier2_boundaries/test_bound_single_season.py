"""
Tier 2 Boundary Tests: Single-Season Show Handling
Requirements: Robustness when show has only 1 season (bucket anti-clustering has 1 bucket).
Verifies graceful fallback to unconstrained random shuffling, Shannon entropy,
and continuous playback progression.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, calculate_shuffle_entropy_metrics
from harness.fixtures import single_season_show_catalog


class TestBoundSingleSeason(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = single_season_show_catalog()

    def test_single_season_shuffles_all_episodes(self):
        """Boundary 1: Single season show (10 eps) produces 10 cards with zero crashes."""
        deck = self.engine.generate_single_show_deck("single_season", "One Season Show", self.episodes)
        self.assertEqual(len(deck.cards), 10)
        self.assertEqual(len(set(c.id for c in deck.cards)), 10)

    def test_single_season_order_is_non_chronological(self):
        """Boundary 2: Single season show cards are shuffled rather than in 1..10 chronological order."""
        deck = self.engine.generate_single_show_deck("single_season", "One Season Show", self.episodes)
        indices = [c.items[0].index_number for c in deck.cards]
        # Not strictly sorted [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        self.assertNotEqual(indices, list(range(1, 11)))

    def test_single_season_entropy_distribution(self):
        """Boundary 3: Single season achieves high first-item entropy over 40 trials."""
        runs = []
        for _ in range(40):
            deck = self.engine.generate_single_show_deck("s1_show", "S1 Show", self.episodes)
            runs.append([c.id for c in deck.cards])

        metrics = calculate_shuffle_entropy_metrics(runs)
        self.assertGreaterEqual(metrics["first_entropy_ratio"], 0.85)

    def test_single_season_skip_recycling(self):
        """Boundary 4: Skipping an episode in a 1-season show recycles it to the back of the pool."""
        deck = self.engine.generate_single_show_deck("s1_show", "S1 Show", self.episodes)
        first_card_id = deck.cards[0].id
        deck, event = self.engine.advance_playback(deck, position_ms=1000, duration_ms=1800000, is_user_skip=True)
        self.assertEqual(event, "RECYCLED_SKIP")
        self.assertIn(first_card_id, [c.id for c in deck.cards])

    def test_single_season_boundary_repeat_swap(self):
        """Boundary 5: Cross-cycle buffering on 10-episode single-season show swaps previous tail away via single-category bypass."""
        for trial in range(25):
            deck1 = self.engine.generate_single_show_deck("s1_show", "S1 Show", self.episodes)
            prev_tail_id = deck1.cards[-1].id

            prev_order = [c.id for c in deck1.cards]
            deck2_cards = self.engine.generate_cross_cycle_deck(self.episodes, prev_deck_order=prev_order, cycle_number=2)
            # Previous final episode should not immediately be the first episode of the next cycle
            self.assertNotEqual(
                deck2_cards[0].id, prev_tail_id,
                f"Trial {trial + 1}: Cross-cycle buffering failed to swap single-season tail card out of index 0"
            )


if __name__ == "__main__":
    unittest.main()
