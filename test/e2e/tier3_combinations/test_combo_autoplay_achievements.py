"""
Tier 3 Cross-Feature Combination Tests: Background Autoplay + Achievement Evaluation
Verifies that continuous shuffle deck autoplay accumulates telemetry metrics,
triggers milestones (Binge Voyager at 8 eps, The Compleatist on cycle completion),
and strictly obeys the Sacred Playback Rule (zero mid-playback toasts).
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem
from harness.client_state import ClientRoomDatabase, AchievementEngine


class TestComboAutoplayAchievements(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()
        self.db = ClientRoomDatabase()
        self.ach_engine = AchievementEngine(self.db)
        self.episodes = [
            EpisodeItem(f"ep_{i}", f"Episode {i}", "show_ach", "s1", 1, i)
            for i in range(1, 15)
        ]
        self.deck = self.engine.generate_single_show_deck("show_ach", "Achievement Show", self.episodes)

    def tearDown(self):
        self.db.close()

    def test_eight_episodes_autoplay_unlocks_binge_voyager(self):
        """Combo 1: Autoplaying through 8 episodes in a session unlocks 'Binge Voyager'."""
        completed_count = 0
        total_time_ms = 0

        for _ in range(8):
            self.deck, event = self.engine.advance_playback(
                self.deck, position_ms=1800000, duration_ms=1800000, is_user_skip=False
            )
            self.assertEqual(event, "CONSUMED_NATURAL")
            completed_count += 1
            total_time_ms += 1800000

        unlocked = self.ach_engine.evaluate_playback_session(
            episodes_completed=completed_count,
            total_watch_time_ms=total_time_ms,
            is_shuffle_play=True,
            deck_cycle_completed=False,
            current_hour=14
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("binge_voyager", unlocked_ids)
        # Verify Sacred Playback Rule: 0 toasts shown mid-playback
        self.assertEqual(self.ach_engine.active_playback_toast_count, 0)
        # Verify achievement queued for post-playback summary screen
        self.assertEqual(len(self.ach_engine.pending_end_screen_celebrations), 1)

    def test_skipped_episodes_do_not_count_towards_completion_achievements(self):
        """Combo 2: Skipping 8 episodes recycles them but does NOT trigger 'Binge Voyager'."""
        completed_count = 0
        for _ in range(8):
            self.deck, event = self.engine.advance_playback(
                self.deck, position_ms=5000, duration_ms=1800000, is_user_skip=True
            )
            self.assertEqual(event, "RECYCLED_SKIP")
            # Skips do not increment completed count

        unlocked = self.ach_engine.evaluate_playback_session(
            episodes_completed=completed_count,
            total_watch_time_ms=40000,
            is_shuffle_play=True,
            deck_cycle_completed=False,
            current_hour=14
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertNotIn("binge_voyager", unlocked_ids)

    def test_full_deck_exhaustion_unlocks_the_compleatist(self):
        """Combo 3: Playing all 14 episodes of the deck triggers cycle completion and 'The Compleatist'."""
        for _ in range(14):
            self.deck, _ = self.engine.advance_playback(
                self.deck, position_ms=1800000, duration_ms=1800000, is_user_skip=False
            )

        self.assertEqual(self.deck.cycle_number, 2)
        unlocked = self.ach_engine.evaluate_playback_session(
            episodes_completed=14,
            total_watch_time_ms=14 * 1800000,
            is_shuffle_play=True,
            deck_cycle_completed=True,
            current_hour=15
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("the_compleatist", unlocked_ids)

    def test_sacred_playback_rule_zero_toasts_across_multiple_unlocks(self):
        """Combo 4: Unlocking multiple achievements simultaneously queues them with 0 active toasts."""
        # Session at 04:00 AM with 8 completed episodes
        unlocked = self.ach_engine.evaluate_playback_session(
            episodes_completed=8,
            total_watch_time_ms=14400000,
            is_shuffle_play=True,
            deck_cycle_completed=False,
            current_hour=4  # 3 AM to 5 AM -> Night Owl
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("binge_voyager", unlocked_ids)
        self.assertIn("night_owl", unlocked_ids)
        self.assertEqual(self.ach_engine.active_playback_toast_count, 0)
        self.assertEqual(len(self.ach_engine.pending_end_screen_celebrations), 2)

    def test_telemetry_event_ledger_persistence(self):
        """Combo 5: Every completed episode records an immutable row in telemetry_events."""
        for i in range(5):
            self.db.record_telemetry_event("DeckItemConsumed", f"ep_{i}", 1800000)
        cur = self.db.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM telemetry_events WHERE event_type = 'DeckItemConsumed'")
        count = cur.fetchone()[0]
        self.assertEqual(count, 5)


if __name__ == "__main__":
    unittest.main()
