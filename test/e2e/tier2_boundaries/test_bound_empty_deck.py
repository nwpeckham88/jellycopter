"""
Tier 2 Boundary Tests: Empty Deck Handling
Requirements: Robustness under 0 episodes, empty collections, and zero-state conditions.
Verifies empty deck generation, advance edge conditions, and error-free execution.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, ShuffleDeck
from harness.client_state import ClientRoomDatabase


class TestBoundEmptyDeck(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()

    def test_empty_catalog_generates_empty_deck_safely(self):
        """Boundary 1: Generating a deck from an empty episode list returns a deck with 0 cards and no crash."""
        deck = self.engine.generate_single_show_deck("empty_show", "Empty Series", [])
        self.assertEqual(len(deck.cards), 0)
        self.assertEqual(deck.current_index, 0)
        self.assertEqual(deck.cycle_number, 1)

    def test_advancing_empty_deck_returns_cycle_complete(self):
        """Boundary 2: Calling advance_playback on an empty deck immediately returns CYCLE_COMPLETE."""
        deck = self.engine.generate_single_show_deck("empty_show", "Empty Series", [])
        deck, event = self.engine.advance_playback(deck, position_ms=1000, duration_ms=1800000)
        self.assertEqual(event, "CYCLE_COMPLETE")
        self.assertEqual(deck.current_index, 0)

    def test_empty_cards_balance_and_shuffle(self):
        """Boundary 3: balance_and_shuffle_cards on empty list returns empty list."""
        shuffled = self.engine.balance_and_shuffle_cards([])
        self.assertEqual(shuffled, [])

    def test_empty_mix_channel_generation(self):
        """Boundary 4: Mix channel with no shows returns an empty deck safely."""
        deck = self.engine.generate_mix_channel_deck("empty_mix", "Empty Mix", [], target_total_episodes=20)
        self.assertEqual(len(deck.cards), 0)

    def test_room_db_empty_progress_query(self):
        """Boundary 5: Querying Room DB for a non-existent deck returns None without raising SQLite exception."""
        db = ClientRoomDatabase()
        progress = db.get_deck_progress("non_existent_deck_999")
        self.assertIsNone(progress)
        db.close()


if __name__ == "__main__":
    unittest.main()
