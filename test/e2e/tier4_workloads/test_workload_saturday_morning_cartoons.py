"""
Tier 4 Real-World Workload Scenario: Saturday Morning Cartoons Multi-Show Virtual Mix Channel
Simulates an extensive 50-episode virtual channel broadcast interleaving 3 shows:
- The Simpsons (30 episodes, 3 seasons)
- Futurama (12 episodes, 2 seasons - shorter show requiring multiple sub-deck resets)
- King of the Hill (20 episodes, 2 seasons)

Validates:
1. Zero adjacent show repetitions across all 50 episodes.
2. Within each participating show, zero adjacent season repetitions.
3. Independent asynchronous sub-deck cycling when Futurama exhausts its 12 episodes.
4. Continuous autoplay playback progression through the full 50 episodes.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem


class TestWorkloadSaturdayMorningCartoons(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        # Create 3 shows of differing episode counts:
        self.simpsons_eps = [
            EpisodeItem(f"simp_s{s}e{e}", f"Simpsons S{s}E{e}", "simpsons", f"simp_s{s}", s, e)
            for s in range(1, 4) for e in range(1, 11)  # 30 episodes
        ]
        self.futurama_eps = [
            EpisodeItem(f"fut_s{s}e{e}", f"Futurama S{s}E{e}", "futurama", f"fut_s{s}", s, e)
            for s in range(1, 3) for e in range(1, 7)   # 12 episodes (shorter)
        ]
        self.koth_eps = [
            EpisodeItem(f"koth_s{s}e{e}", f"KotH S{s}E{e}", "koth", f"koth_s{s}", s, e)
            for s in range(1, 3) for e in range(1, 11)  # 20 episodes
        ]

        self.shows = [
            ("simpsons", "The Simpsons", self.simpsons_eps, 1),
            ("futurama", "Futurama", self.futurama_eps, 1),
            ("koth", "King of the Hill", self.koth_eps, 1)
        ]

    def test_fifty_episode_mix_channel_zero_show_and_season_collisions(self):
        """Workload 1: Zero adjacent show and within-show season collisions across 50 episodes."""
        channel_deck = self.engine.generate_mix_channel_deck(
            "sat_morning_cartoons", "Saturday Morning Cartoons",
            self.shows, target_total_episodes=50
        )
        self.assertEqual(len(channel_deck.cards), 50)

        # 1. Verify zero adjacent show collisions across all 50 slots
        for i in range(len(channel_deck.cards) - 1):
            curr_show = channel_deck.cards[i].series_id
            next_show = channel_deck.cards[i + 1].series_id
            self.assertNotEqual(
                curr_show, next_show,
                f"Show collision at slot {i} -> {i+1}: both are {curr_show}"
            )

        # 2. Verify within-show season anti-clustering
        for sid in ["simpsons", "futurama", "koth"]:
            show_cards = [c for c in channel_deck.cards if c.series_id == sid]
            self.assertGreater(len(show_cards), 10)
            for j in range(len(show_cards) - 1):
                self.assertNotEqual(
                    show_cards[j].category_key, show_cards[j + 1].category_key,
                    f"Within-show season clash in {sid} at consecutive appearances {j} -> {j+1}"
                )

    def test_shorter_show_recycles_subdeck_independently(self):
        """Workload 2: Futurama (12 eps) resets its sub-deck independently while Simpsons continues."""
        channel_deck = self.engine.generate_mix_channel_deck(
            "sat_morning_cartoons", "Saturday Morning Cartoons",
            self.shows, target_total_episodes=50
        )
        futurama_cards = [c for c in channel_deck.cards if c.series_id == "futurama"]
        # In 50 episodes, Futurama is served ~16-17 times (> 12), requiring sub-deck cycle reset
        self.assertGreater(len(futurama_cards), 12)

        # Verify all 12 original Futurama episodes were served before repeating
        first_cycle_ids = set(c.id for c in futurama_cards[:12])
        self.assertEqual(len(first_cycle_ids), 12, "First 12 Futurama cards should all be unique")

    def test_fifty_episode_continuous_playback_progression(self):
        """Workload 3: Simulates playing through all 50 episodes of the broadcast uninterrupted."""
        channel_deck = self.engine.generate_mix_channel_deck(
            "sat_morning_cartoons", "Saturday Morning Cartoons",
            self.shows, target_total_episodes=50
        )
        for ep_idx in range(50):
            channel_deck, event = self.engine.advance_playback(
                channel_deck, position_ms=1800000, duration_ms=1800000, is_user_skip=False
            )
            if ep_idx < 49:
                self.assertEqual(event, "CONSUMED_NATURAL")
                self.assertEqual(channel_deck.current_index, ep_idx + 1)
            else:
                self.assertEqual(event, "CYCLE_RESET")
                self.assertEqual(channel_deck.current_index, 0)


if __name__ == "__main__":
    unittest.main()
