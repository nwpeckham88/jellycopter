"""
Tier 5 Adversarial Stress Tests: Smart Shuffle Engine
Empirical stress-testing of distribution boundaries, multi-part title parsing,
entropy measurements, and season collision guarantees.
"""

import math
import unittest
from typing import List
from harness.shuffle_engine import (
    SmartShuffleEngine,
    EpisodeItem,
    calculate_season_clash_rate,
    calculate_shuffle_entropy_metrics,
    MULTI_PART_REGEX,
    parse_roman_or_int,
    normalize_title,
    GENERIC_BLACKLIST,
)


class TestAdversarialShuffleStress(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()

    # -------------------------------------------------------------
    # 1. Extreme Uneven Distributions
    # -------------------------------------------------------------

    def test_extreme_100_seasons_with_1_episode_each(self):
        """
        Stress 1: 100 seasons with 1 episode each (N=100).
        max(bucket_size) = 1 <= ceil(100/2) = 50.
        Theoretical adjacent collision rate MUST BE EXACTLY 0.00%.
        """
        episodes = [
            EpisodeItem(
                id=f"s{s}_e1",
                name=f"Episode {s}-1",
                series_id="show_100",
                season_id=f"season_{s}",
                season_number=s,
                index_number=1,
            )
            for s in range(1, 101)
        ]

        trials = 20
        all_runs: List[List[str]] = []
        for t in range(trials):
            deck = self.engine.generate_single_show_deck("show_100", "100 Seasons Show", episodes)
            self.assertEqual(len(deck.cards), 100, "Deck size mismatch")
            clash_rate = calculate_season_clash_rate(deck.cards)
            self.assertEqual(
                clash_rate, 0.0,
                f"Trial {t}: Non-zero clash rate {clash_rate} on 100 1-ep seasons!"
            )
            all_runs.append([c.id for c in deck.cards])

        metrics = calculate_shuffle_entropy_metrics(all_runs)
        self.assertGreater(metrics["first_entropy_ratio"], 0.85)

    def test_extreme_dominant_season_500_vs_2_episodes(self):
        """
        Stress 2: 1 season with 500 episodes and 1 season with 2 episodes (N=502).
        max(bucket_size) = 500 > ceil(502/2) = 251.
        By Pigeonhole principle, collisions are mathematically unavoidable.
        Deficit = 500 - 2 - 1 = 497 unavoidable collisions minimum.
        The algorithm must NOT deadlock, infinite loop, or drop any cards.
        The 2 minority episodes must be separated (gap >= 2).
        """
        episodes_s1 = [
            EpisodeItem(
                id=f"s1_e{e}",
                name=f"Season 1 Ep {e}",
                series_id="show_500_2",
                season_id="season_1",
                season_number=1,
                index_number=e,
            )
            for e in range(1, 501)
        ]
        episodes_s2 = [
            EpisodeItem(
                id=f"s2_e{e}",
                name=f"Season 2 Ep {e}",
                series_id="show_500_2",
                season_id="season_2",
                season_number=2,
                index_number=e,
            )
            for e in range(1, 3)
        ]
        all_eps = episodes_s1 + episodes_s2

        deck = self.engine.generate_single_show_deck("show_500_2", "500 vs 2 Show", all_eps)
        self.assertEqual(len(deck.cards), 502, "Cards were lost or duplicated!")

        s1_count = sum(1 for c in deck.cards if c.category_key == "season_1")
        s2_count = sum(1 for c in deck.cards if c.category_key == "season_2")
        self.assertEqual(s1_count, 500)
        self.assertEqual(s2_count, 2)

        # Minority season spacing: the 2 S2 episodes must not be adjacent
        s2_indices = [idx for idx, c in enumerate(deck.cards) if c.category_key == "season_2"]
        self.assertEqual(len(s2_indices), 2)
        self.assertGreaterEqual(
            s2_indices[1] - s2_indices[0], 2,
            f"Minority season episodes are clumped: {s2_indices}"
        )

        # Collisions should be minimal possible:
        clashes = sum(
            1 for i in range(len(deck.cards) - 1)
            if deck.cards[i].category_key == deck.cards[i + 1].category_key
        )
        self.assertGreaterEqual(clashes, 497)
        self.assertLessEqual(clashes, 500)

    def test_zero_episode_show(self):
        """
        Stress 3: 0-episode show (empty list).
        Must produce empty deck gracefully without throwing unhandled exceptions.
        """
        deck = self.engine.generate_single_show_deck("show_empty", "Empty Show", [])
        self.assertEqual(len(deck.cards), 0)
        self.assertEqual(deck.current_index, 0)

        deck, event = self.engine.advance_playback(deck, position_ms=0, duration_ms=1000)
        self.assertEqual(event, "CYCLE_COMPLETE")

    def test_single_episode_series(self):
        """
        Stress 4: Single episode series (1 season, 1 episode).
        Must produce 1-card deck with 0 collisions and advance/recycle cleanly.
        """
        eps = [
            EpisodeItem(
                id="ep_pilot",
                name="Pilot",
                series_id="single_show",
                season_id="season_1",
                season_number=1,
                index_number=1,
            )
        ]
        deck = self.engine.generate_single_show_deck("single_show", "Single Show", eps)
        self.assertEqual(len(deck.cards), 1)
        clash_rate = calculate_season_clash_rate(deck.cards)
        self.assertEqual(clash_rate, 0.0)

        # Cycle advance
        deck, event = self.engine.advance_playback(deck, position_ms=1800000, duration_ms=1800000)
        self.assertEqual(event, "CYCLE_RESET")
        self.assertEqual(deck.cycle_number, 2)
        self.assertEqual(deck.current_index, 0)

    # -------------------------------------------------------------
    # 2. Adversarial Episode Titles & Multi-Part Grouping
    # -------------------------------------------------------------

    def test_roman_numeral_edge_cases(self):
        """
        Stress 5: Roman numeral parser edge cases:
        Valid: I, IV, IX, XL, XC, CD, CM, MMXXIV (2024).
        Invalid: IIII, VX, IC, "", random non-numerals.
        """
        self.assertEqual(parse_roman_or_int("IV"), 4)
        self.assertEqual(parse_roman_or_int("IX"), 9)
        self.assertEqual(parse_roman_or_int("XL"), 40)
        self.assertEqual(parse_roman_or_int("XC"), 90)
        self.assertEqual(parse_roman_or_int("CD"), 400)
        self.assertEqual(parse_roman_or_int("CM"), 900)
        self.assertEqual(parse_roman_or_int("MMXXIV"), 2024)

        # Subtraction rule validation
        self.assertIsNone(parse_roman_or_int("IIII"))  # Non-standard
        self.assertIsNone(parse_roman_or_int("VX"))    # Invalid subtraction
        self.assertIsNone(parse_roman_or_int("IC"))    # Invalid subtraction
        self.assertIsNone(parse_roman_or_int("ZZZ"))   # Not roman

    def test_false_positive_part_titles(self):
        """
        Stress 6: Titles containing words like 'Part', 'Party', 'Apartment', 'Counterpart'
        that should NOT be falsely recognized as multi-part numbers unless structured properly.
        """
        m_party = MULTI_PART_REGEX.match("Party Time")
        self.assertIsNone(m_party)

        m_particle = MULTI_PART_REGEX.match("Particle Physics")
        self.assertIsNone(m_particle)

        m_counterpart = MULTI_PART_REGEX.match("Counterpart")
        self.assertIsNone(m_counterpart)

    def test_special_characters_and_bracketed_part_titles(self):
        """
        Stress 7: Titles with brackets, slashes, colons, emojis, and hyphens.
        """
        t1 = "Show: The Beginning [Part 1]"
        t2 = "Show: The Beginning [Part 2]"
        m1 = MULTI_PART_REGEX.match(t1)
        m2 = MULTI_PART_REGEX.match(t2)
        self.assertIsNotNone(m1)
        self.assertIsNotNone(m2)
        self.assertEqual(normalize_title(m1.group("base")), normalize_title(m2.group("base")))
        self.assertEqual(parse_roman_or_int(m1.group("num")), 1)
        self.assertEqual(parse_roman_or_int(m2.group("num")), 2)

        eps = [
            EpisodeItem("ep1", t1, "s1", "sea1", 1, 1),
            EpisodeItem("ep2", t2, "s1", "sea1", 1, 2),
        ]
        cards = self.engine.build_media_cards(eps)
        self.assertEqual(len(cards), 1, "Failed to cluster bracketed multi-part titles!")
        self.assertTrue(cards[0].is_multi_part)
        self.assertEqual(len(cards[0].items), 2)

    def test_multipart_arc_spanning_more_than_5_episodes(self):
        """
        Stress 8: Large multi-part arc spanning 10 episodes (e.g. anime finale arc: Part 1 to Part 10).
        Must cluster all 10 into a SINGLE MediaCard atomically.
        """
        eps = [
            EpisodeItem(
                id=f"arc_ep_{i}",
                name=f"Final Battle: Part {i}",
                series_id="anime_show",
                season_id="season_4",
                season_number=4,
                index_number=i,
            )
            for i in range(1, 11)
        ]
        cards = self.engine.build_media_cards(eps)
        self.assertEqual(len(cards), 1, f"Expected 1 card for 10-part arc, got {len(cards)}")
        self.assertTrue(cards[0].is_multi_part)
        self.assertEqual(len(cards[0].items), 10)
        self.assertEqual([ep.id for ep in cards[0].items], [f"arc_ep_{i}" for i in range(1, 11)])

    def test_broken_multipart_sequence_does_not_cluster(self):
        """
        Stress 9: Incomplete or broken sequence (Part 1, Part 3 - Part 2 missing).
        Invariant 4 (sequential part number) fails -> must NOT cluster together.
        """
        eps = [
            EpisodeItem("ep1", "The War: Part 1", "s1", "sea1", 1, 1),
            EpisodeItem("ep3", "The War: Part 3", "s1", "sea1", 1, 2),
        ]
        cards = self.engine.build_media_cards(eps)
        self.assertEqual(len(cards), 2, "Clustered non-consecutive parts!")
        self.assertFalse(cards[0].is_multi_part)
        self.assertFalse(cards[1].is_multi_part)

    def test_generic_title_blacklist_never_clusters(self):
        """
        Stress 10: Generic titles like 'Episode 1', 'Episode 2', 'Chapter 1', 'Chapter 2'.
        Must NOT cluster into a 2-part arc because base title is generic.
        """
        eps = [
            EpisodeItem("ep1", "Episode 1", "s1", "sea1", 1, 1),
            EpisodeItem("ep2", "Episode 2", "s1", "sea1", 1, 2),
        ]
        cards = self.engine.build_media_cards(eps)
        self.assertEqual(len(cards), 2, "Generic 'Episode N' titles were wrongly clustered!")
        self.assertFalse(cards[0].is_multi_part)
        self.assertFalse(cards[1].is_multi_part)

    # -------------------------------------------------------------
    # 3. Shannon Entropy & Adjacent Collision Rigorous Evaluation
    # -------------------------------------------------------------

    def test_entropy_and_zero_collision_on_balanced_seasons(self):
        """
        Stress 11: 4 seasons with 10 episodes each (N=40).
        Evaluates 100 independent shuffle runs.
        - Collision rate must be 0.00% across all 100 runs.
        - First-item entropy ratio >= 0.90.
        - Positional entropy ratio >= 0.85.
        - Transition entropy >= log2(N - season_size).
        """
        episodes = []
        for s in range(1, 5):
            for e in range(1, 11):
                episodes.append(
                    EpisodeItem(
                        id=f"s{s}_e{e}",
                        name=f"S0{s}E{e:02d}",
                        series_id="balanced_4",
                        season_id=f"season_{s}",
                        season_number=s,
                        index_number=e,
                    )
                )

        trials = 100
        runs: List[List[str]] = []
        for t in range(trials):
            deck = self.engine.generate_single_show_deck("balanced_4", "Balanced 4 Show", episodes)
            clash_rate = calculate_season_clash_rate(deck.cards)
            self.assertEqual(
                clash_rate, 0.0,
                f"Run {t + 1} produced season clash: {clash_rate}"
            )
            runs.append([c.id for c in deck.cards])

        metrics = calculate_shuffle_entropy_metrics(runs)
        self.assertGreaterEqual(
            metrics["first_entropy_ratio"], 0.90,
            f"First item entropy ratio too low: {metrics['first_entropy_ratio']:.3f}"
        )
        self.assertGreaterEqual(
            metrics["pos_entropy_ratio"], 0.85,
            f"Positional entropy ratio too low: {metrics['pos_entropy_ratio']:.3f}"
        )


if __name__ == "__main__":
    unittest.main()
