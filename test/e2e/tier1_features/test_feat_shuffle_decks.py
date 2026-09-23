"""
Tier 1 Feature Tests: Exhaustion Deck Management & Shuffling
Requirements: R1, ORIGINAL_REQUEST.md § R1, PROJECT.md Feature 3.
Verifies deck creation, Shannon entropy preservation, cycle reset at exhaustion,
and boundary repeat buffering across cycles.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import math
from harness.shuffle_engine import (
    SmartShuffleEngine, calculate_shuffle_entropy_metrics
)
from harness.fixtures import bobs_burgers_catalog


class TestFeatureShuffleDecks(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = bobs_burgers_catalog()

    def test_deck_generation_preserves_card_count(self):
        """Test 1: Shuffled deck contains all episodes without omission or duplication."""
        deck = self.engine.generate_single_show_deck(
            series_id="show_bobs_burgers",
            series_name="Bob's Burgers",
            episodes=self.episodes
        )
        total_episodes_in_deck = sum(len(card.items) for card in deck.cards)
        self.assertEqual(total_episodes_in_deck, len(self.episodes))
        self.assertEqual(deck.current_index, 0)
        self.assertEqual(deck.cycle_number, 1)

    def test_shannon_entropy_exceeds_90_percent_of_theoretical_max(self):
        """Test 2: Entropy of first item and positions meets reference benchmarks (>=90% of max)."""
        trials = 60
        runs = []
        for _ in range(trials):
            deck = self.engine.generate_single_show_deck("bb", "Bob's Burgers", self.episodes)
            runs.append([c.id for c in deck.cards])

        metrics = calculate_shuffle_entropy_metrics(runs)
        self.assertGreaterEqual(
            metrics["first_entropy_ratio"], 0.90,
            f"First item entropy ratio {metrics['first_entropy_ratio']:.3f} was below 0.90 target"
        )
        self.assertGreaterEqual(
            metrics["pos_entropy_ratio"], 0.90,
            f"Positional entropy ratio {metrics['pos_entropy_ratio']:.3f} was below 0.90 target"
        )

    def test_cycle_boundary_swap_protects_tail_items(self):
        """Test 3: Cross-cycle buffering swaps previous cycle's tail episodes out of new cycle head."""
        first_deck = self.engine.generate_single_show_deck("bb", "Bob's Burgers", self.episodes)
        prev_tail_ids = set(c.id for c in first_deck.cards[-10:])

        # Next cycle deck with previous tail history
        prev_order = [c.id for c in first_deck.cards]
        new_cards = self.engine.generate_cross_cycle_deck(self.episodes, prev_deck_order=prev_order, cycle_number=2)

        # First 5 items of new deck should not contain the immediate tail items from prev cycle
        new_head_ids = set(c.id for c in new_cards[:5])
        overlap = new_head_ids.intersection(prev_tail_ids)
        self.assertEqual(len(overlap), 0, f"Tail items repeated in immediate head: {overlap}")

    def test_cycle_reset_at_exhaustion(self):
        """Test 4: Consuming all cards in a deck triggers cycle increment and resets current_index to 0."""
        deck = self.engine.generate_single_show_deck("bb", "Bob's Burgers", self.episodes)
        total_cards = len(deck.cards)

        # Advance through all cards
        for _ in range(total_cards - 1):
            deck, event = self.engine.advance_playback(deck, position_ms=1800000, duration_ms=1800000)
            self.assertEqual(event, "CONSUMED_NATURAL")

        # Advance final card to trigger cycle reset
        deck, event = self.engine.advance_playback(
            deck,
            raw_episodes_provider=lambda sid: self.episodes,
            position_ms=1800000,
            duration_ms=1800000
        )
        self.assertEqual(event, "CYCLE_RESET")
        self.assertEqual(deck.cycle_number, 2)
        self.assertEqual(deck.current_index, 0)
        self.assertEqual(len(deck.exhausted_card_ids), 0)

    def test_non_video_and_virtual_filtering(self):
        """Test 5: Virtual items, specials with zero runtime, and non-video types are excluded."""
        dirty_episodes = list(self.episodes)
        dirty_episodes.append(self.episodes[0].__class__(
            id="virtual_special_1", name="Missing Special", series_id="bb",
            season_id="s0", season_number=0, index_number=1,
            location_type="Virtual"
        ))
        dirty_episodes.append(self.episodes[0].__class__(
            id="audio_podcast_1", name="Audio Commentary", series_id="bb",
            season_id="s0", season_number=0, index_number=2,
            media_type="Audio"
        ))
        dirty_episodes.append(self.episodes[0].__class__(
            id="zero_runtime_1", name="Broken Metadata", series_id="bb",
            season_id="s0", season_number=0, index_number=3,
            run_time_ticks=0
        ))

        deck = self.engine.generate_single_show_deck("bb", "Bob's Burgers", dirty_episodes)
        all_ids = set()
        for c in deck.cards:
            all_ids.update(c.item_ids)

        self.assertNotIn("virtual_special_1", all_ids)
        self.assertNotIn("audio_podcast_1", all_ids)
        self.assertNotIn("zero_runtime_1", all_ids)
        self.assertEqual(len(all_ids), len(self.episodes))


if __name__ == "__main__":
    unittest.main()
