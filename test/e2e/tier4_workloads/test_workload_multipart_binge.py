"""
Tier 4 Real-World Workload Scenario: Multi-Part Story Arc Binge
Simulates multi-part episodes ('Chain of Command, Part 1' and 'Part 2',
'All Good Things..., Part 1' and 'Part 2') during marathon shuffle viewing.

Validates:
1. 100% contiguity across 100 independent shuffle trials.
2. Chronological sequence: Part 1 strictly precedes Part 2.
3. MediaCard bundles both items into a single atomic playable unit.
4. Completing Part 1 immediately cascades into Part 2 during playback.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine
from harness.fixtures import star_trek_tng_multipart_catalog


class TestWorkloadMultipartBinge(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.episodes = star_trek_tng_multipart_catalog()

    def test_one_hundred_trials_one_hundred_percent_contiguity(self):
        """Workload 1: Over 100 independent shuffle runs, Part 1 and Part 2 are 100% contiguous."""
        for trial in range(100):
            deck = self.engine.generate_single_show_deck("tng", "Star Trek TNG", self.episodes)

            # Find 'Chain of Command' card
            chain_card = next((c for c in deck.cards if "Chain of Command" in c.title), None)
            self.assertIsNotNone(chain_card, f"Trial {trial}: Chain of Command not found")
            self.assertEqual(len(chain_card.items), 2, f"Trial {trial}: Items not bonded")
            self.assertEqual(chain_card.items[0].name, "Chain of Command, Part 1")
            self.assertEqual(chain_card.items[1].name, "Chain of Command, Part 2")

            # Find 'All Good Things...' card
            agt_card = next((c for c in deck.cards if "All Good Things" in c.title), None)
            self.assertIsNotNone(agt_card, f"Trial {trial}: All Good Things not found")
            self.assertEqual(len(agt_card.items), 2, f"Trial {trial}: Items not bonded")
            self.assertEqual(agt_card.items[0].name, "All Good Things..., Part 1")
            self.assertEqual(agt_card.items[1].name, "All Good Things..., Part 2")

    def test_multi_part_atomic_playback_chaining(self):
        """Workload 2: Playing a multi-part card plays Part 1 then seamlessly advances to Part 2."""
        cards = self.engine.build_media_cards(self.episodes)
        chain_card = next(c for c in cards if "Chain of Command" in c.title)

        # Player receives atomic card with 2 items
        self.assertEqual(len(chain_card.items), 2)
        # Part 1 item
        part_1 = chain_card.items[0]
        # Part 2 item
        part_2 = chain_card.items[1]

        self.assertEqual(part_1.name, "Chain of Command, Part 1")
        self.assertEqual(part_2.name, "Chain of Command, Part 2")
        self.assertEqual(part_2.index_number, part_1.index_number + 1)

    def test_deck_pointer_increments_by_one_card_for_two_episodes(self):
        """Workload 3: Consuming the 2-part arc advances deck current_index by exactly 1 card."""
        deck = self.engine.generate_single_show_deck("tng", "Star Trek TNG", self.episodes)
        # Locate index of chain card in deck
        chain_idx = next(i for i, c in enumerate(deck.cards) if "Chain of Command" in c.title)
        deck.current_index = chain_idx

        # Consume the 2-part card
        deck, event = self.engine.advance_playback(deck, position_ms=3600000, duration_ms=3600000)
        self.assertIn(event, ["CONSUMED_NATURAL", "CYCLE_RESET"])
        if event == "CONSUMED_NATURAL":
            self.assertEqual(deck.current_index, chain_idx + 1)
        else:
            self.assertEqual(deck.current_index, 0)


if __name__ == "__main__":
    unittest.main()
