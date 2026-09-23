"""
Tier 1 Feature Tests: Continuous Autoplay, Midway Resume, and Skip Recycling
Requirements: R1, ORIGINAL_REQUEST.md § R1, PROJECT.md Feature 8.
Verifies resume preservation at <80%, natural consumption at >=80%,
and unexhausted skip recycling into pool vs strict queue mode.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem


class TestFeatureAutoplayResumeRecycling(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = [
            EpisodeItem(f"ep_{i}", f"Episode {i}", "show_auto", "s1", 1, i, run_time_ticks=18000000000)
            for i in range(1, 21)
        ]
        self.deck = self.engine.generate_single_show_deck("show_auto", "Autoplay Show", self.episodes)

    def test_midway_pause_saves_resume_position(self):
        """Test 1: Exiting at 45% (<80%) saves midway resume ms and does NOT advance deck index."""
        initial_index = self.deck.current_index
        # 45% of 30 min (1800000 ms) = 810000 ms; remaining = 990000 ms (> 90000 ms)
        deck, event = self.engine.advance_playback(
            self.deck, position_ms=810000, duration_ms=1800000, is_user_skip=False
        )
        self.assertEqual(event, "SAVED_MIDWAY_RESUME")
        self.assertEqual(deck.current_index, initial_index)
        self.assertEqual(deck.midway_resume_ms, 810000)

    def test_eighty_percent_threshold_consumes_episode(self):
        """Test 2: Reaching 82% (>=80%) consumes card, resets resume to 0, and advances index."""
        initial_index = self.deck.current_index
        initial_card_id = self.deck.cards[initial_index].id

        # 82% of 1800000 = 1476000 ms
        deck, event = self.engine.advance_playback(
            self.deck, position_ms=1476000, duration_ms=1800000, is_user_skip=False
        )
        self.assertEqual(event, "CONSUMED_NATURAL")
        self.assertEqual(deck.current_index, initial_index + 1)
        self.assertEqual(deck.midway_resume_ms, 0)
        self.assertIn(initial_card_id, deck.exhausted_card_ids)

    def test_default_mode_skip_recycles_card_into_pool(self):
        """Test 3: Skipping in default mode recycles card >= min(10, remaining) slots ahead."""
        skipped_card = self.deck.cards[self.deck.current_index]
        skipped_id = skipped_card.id

        deck, event = self.engine.advance_playback(
            self.deck, position_ms=10000, duration_ms=1800000, is_user_skip=True
        )
        self.assertEqual(event, "RECYCLED_SKIP")
        # Skipped card must NOT be in exhausted set
        self.assertNotIn(skipped_id, deck.exhausted_card_ids)
        # Skipped card must still exist in the remaining deck
        remaining_ids = [c.id for c in deck.cards[deck.current_index:]]
        self.assertIn(skipped_id, remaining_ids)
        # Position should be at least min(10, remaining) slots ahead
        recycled_slot = remaining_ids.index(skipped_id)
        self.assertGreaterEqual(recycled_slot, min(10, len(remaining_ids) - 1))

    def test_strict_queue_mode_permanently_consumes_on_skip(self):
        """Test 4: With strictQueueMode=True, skipping immediately consumes card and advances."""
        self.deck.strict_queue_mode = True
        skipped_card = self.deck.cards[self.deck.current_index]
        skipped_id = skipped_card.id

        deck, event = self.engine.advance_playback(
            self.deck, position_ms=10000, duration_ms=1800000, is_user_skip=True
        )
        self.assertEqual(event, "CONSUMED_BY_STRICT_SKIP")
        self.assertIn(skipped_id, deck.exhausted_card_ids)
        self.assertEqual(deck.current_index, 1)

    def test_last_90_seconds_remaining_treated_as_complete(self):
        """Test 5: If remaining time <= 90 seconds, episode is consumed even if slightly under 80%."""
        # Duration 1000s, position 915s -> remaining 85s (<= 90s)
        deck, event = self.engine.advance_playback(
            self.deck, position_ms=915000, duration_ms=1000000, is_user_skip=False
        )
        self.assertEqual(event, "CONSUMED_NATURAL")
        self.assertEqual(deck.midway_resume_ms, 0)
        self.assertEqual(deck.current_index, 1)


if __name__ == "__main__":
    unittest.main()
