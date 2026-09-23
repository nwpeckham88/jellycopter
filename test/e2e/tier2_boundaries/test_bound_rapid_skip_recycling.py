"""
Tier 2 Boundary Tests: Rapid 10-Episode Skip Recycling
Requirements: User rapidly skips 10 episodes in succession.
Verifies unexhausted cards re-enter pool, card counts are preserved,
no immediate same-episode repetition, and strict mode contrasts.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem


class TestBoundRapidSkipRecycling(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = [
            EpisodeItem(f"ep_{i}", f"Episode {i}", "show_skip", "s1", 1, i)
            for i in range(1, 31)
        ]
        self.deck = self.engine.generate_single_show_deck("show_skip", "Skip Show", self.episodes)

    def test_rapid_10_skips_keeps_all_episodes_unexhausted(self):
        """Boundary 1: Skipping 10 consecutive times in default mode leaves exhausted set empty."""
        initial_card_count = len(self.deck.cards)
        for _ in range(10):
            self.deck, event = self.engine.advance_playback(
                self.deck, position_ms=5000, duration_ms=1800000, is_user_skip=True
            )
            self.assertEqual(event, "RECYCLED_SKIP")

        self.assertEqual(len(self.deck.exhausted_card_ids), 0)
        self.assertEqual(len(self.deck.cards), initial_card_count)

    def test_skipped_card_not_immediately_re_served(self):
        """Boundary 2: Skipping a card guarantees the very next card served is NOT the same card."""
        for _ in range(5):
            curr_id = self.deck.cards[self.deck.current_index].id
            self.deck, _ = self.engine.advance_playback(
                self.deck, position_ms=1000, duration_ms=1800000, is_user_skip=True
            )
            next_id = self.deck.cards[self.deck.current_index].id
            self.assertNotEqual(curr_id, next_id, "Skipped card was re-served immediately!")

    def test_all_30_original_episodes_remain_in_deck(self):
        """Boundary 3: Deck still contains all 30 unique episodes after 10 rapid skips."""
        for _ in range(10):
            self.deck, _ = self.engine.advance_playback(
                self.deck, position_ms=2000, duration_ms=1800000, is_user_skip=True
            )
        unique_ids = set(c.id for c in self.deck.cards)
        self.assertEqual(len(unique_ids), 30)

    def test_rapid_skips_in_strict_queue_mode_consumes_all_ten(self):
        """Boundary 4: In strict queue mode, 10 rapid skips consume 10 episodes and advance index to 10."""
        self.deck.strict_queue_mode = True
        for _ in range(10):
            self.deck, event = self.engine.advance_playback(
                self.deck, position_ms=2000, duration_ms=1800000, is_user_skip=True
            )
            self.assertEqual(event, "CONSUMED_BY_STRICT_SKIP")

        self.assertEqual(self.deck.current_index, 10)
        self.assertEqual(len(self.deck.exhausted_card_ids), 10)

    def test_interleaved_playback_and_skips(self):
        """Boundary 5: Alternating between natural consumption (>=80%) and skips maintains state cleanly."""
        # Play ep 1 to 85%
        self.deck, ev1 = self.engine.advance_playback(self.deck, position_ms=1600000, duration_ms=1800000, is_user_skip=False)
        self.assertEqual(ev1, "CONSUMED_NATURAL")
        self.assertEqual(len(self.deck.exhausted_card_ids), 1)

        # Skip ep 2
        self.deck, ev2 = self.engine.advance_playback(self.deck, position_ms=5000, duration_ms=1800000, is_user_skip=True)
        self.assertEqual(ev2, "RECYCLED_SKIP")
        self.assertEqual(len(self.deck.exhausted_card_ids), 1)


if __name__ == "__main__":
    unittest.main()
