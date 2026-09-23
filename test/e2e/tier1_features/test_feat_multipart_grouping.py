"""
Tier 1 Feature Tests: Multi-Part Episode Grouping (4-Point Invariant)
Requirements: R1, ORIGINAL_REQUEST.md § R1, PROJECT.md Feature 4.
Verifies regex parsing, strict Roman numeral conversion, contiguous 100% back-to-back binding,
directional ordering (Part 1 before Part 2), and anti-false-positive blacklists.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
from harness.shuffle_engine import SmartShuffleEngine
from harness.fixtures import (
    star_trek_tng_multipart_catalog, generic_titles_catalog, roman_numeral_catalog
)


class TestFeatureMultipartGrouping(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()

    def test_same_season_sequential_parts_are_bonded_100_percent(self):
        """Test 1: Chain of Command Part 1 and Part 2 are bonded into a single atomic card."""
        tng_eps = star_trek_tng_multipart_catalog()
        cards = self.engine.build_media_cards(tng_eps)

        chain_card = next((c for c in cards if "Chain of Command" in c.title), None)
        self.assertIsNotNone(chain_card, "Chain of Command card not found")
        self.assertTrue(chain_card.is_multi_part)
        self.assertEqual(len(chain_card.items), 2)
        self.assertEqual(chain_card.items[0].name, "Chain of Command, Part 1")
        self.assertEqual(chain_card.items[1].name, "Chain of Command, Part 2")

    def test_directional_ordering_is_strictly_preserved(self):
        """Test 2: Part 1 strictly precedes Part 2 inside the bonded MediaCard (never inverted)."""
        tng_eps = star_trek_tng_multipart_catalog()
        # Even across 50 shuffles, the order within the atomic card remains [Part 1, Part 2]
        for _ in range(50):
            deck = self.engine.generate_single_show_deck("tng", "Star Trek TNG", tng_eps)
            for card in deck.cards:
                if card.is_multi_part and len(card.items) >= 2:
                    self.assertIn("Part 1", card.items[0].name)
                    self.assertIn("Part 2", card.items[1].name)

    def test_roman_numerals_grouped_correctly(self):
        """Test 3: Roman numerals (Part I, Part II, Part III) are parsed and bonded."""
        rom_eps = roman_numeral_catalog()[:3]
        cards = self.engine.build_media_cards(rom_eps)
        self.assertEqual(len(cards), 1, "Three-part Roman numeral arc should produce 1 card")
        self.assertEqual(len(cards[0].items), 3)
        self.assertTrue(cards[0].is_multi_part)

    def test_cross_season_episodes_are_not_bonded(self):
        """Test 4: Best of Both Worlds Part 1 (S3) and Part 2 (S4) are kept separate by SeasonId invariant."""
        tng_eps = star_trek_tng_multipart_catalog()
        cards = self.engine.build_media_cards(tng_eps)

        bobw_cards = [c for c in cards if "The Best of Both Worlds" in c.title]
        self.assertEqual(len(bobw_cards), 2, "Cross-season parts must not be merged into single card")
        self.assertEqual(len(bobw_cards[0].items), 1)
        self.assertEqual(len(bobw_cards[1].items), 1)

    def test_generic_titles_are_blacklisted_from_false_clustering(self):
        """Test 5: Episodes titled 'Episode 1', 'Episode 2', 'Chapter 1' are NOT bonded."""
        gen_eps = generic_titles_catalog()
        cards = self.engine.build_media_cards(gen_eps)
        # Should be 5 distinct standalone cards, NOT 2 merged cards
        self.assertEqual(len(cards), 5)
        for card in cards:
            self.assertFalse(card.is_multi_part)
            self.assertEqual(len(card.items), 1)


if __name__ == "__main__":
    unittest.main()
