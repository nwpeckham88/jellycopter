"""
Tier 1 Feature Tests: Telemetry & Gamified Milestone Achievements
Requirements: R5, ORIGINAL_REQUEST.md § R5, PROJECT.md Feature 22.
Verifies milestone evaluation (Trip to the Moon, Binge Voyager, Shuffle Centurion, Night Owl, The Compleatist)
and the Sacred Playback Rule (zero mid-playback toast interruptions).
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
from harness.client_state import ClientRoomDatabase, AchievementEngine


class TestFeatureAchievements(unittest.TestCase):

    def setUp(self):
        self.db = ClientRoomDatabase()
        self.engine = AchievementEngine(self.db)

    def tearDown(self):
        self.db.close()

    def test_trip_to_the_moon_unlocks_at_72_hours(self):
        """Test 1: Reaching 72 hours (259,200,000 ms) unlocks 'Trip to the Moon'."""
        unlocked = self.engine.evaluate_playback_session(
            episodes_completed=1,
            total_watch_time_ms=259200000,
            is_shuffle_play=True,
            deck_cycle_completed=False,
            current_hour=14
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("trip_to_the_moon", unlocked_ids)
        self.assertEqual(self.engine.unlocked["trip_to_the_moon"].points, 100)

    def test_binge_voyager_unlocks_at_8_episodes(self):
        """Test 2: Completing 8 episodes in a single session unlocks 'Binge Voyager'."""
        unlocked = self.engine.evaluate_playback_session(
            episodes_completed=8,
            total_watch_time_ms=14400000,
            is_shuffle_play=False,
            deck_cycle_completed=False,
            current_hour=16
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("binge_voyager", unlocked_ids)

    def test_shuffle_centurion_unlocks_at_100_shuffle_plays(self):
        """Test 3: Completing 100 shuffle deck plays unlocks 'Shuffle Centurion'."""
        unlocked = self.engine.evaluate_playback_session(
            episodes_completed=100,
            total_watch_time_ms=180000000,
            is_shuffle_play=True,
            deck_cycle_completed=False,
            current_hour=12
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("shuffle_centurion", unlocked_ids)

    def test_night_owl_unlocks_between_3am_and_5am(self):
        """Test 4: Completing playback at 03:45 AM local time unlocks 'Night Owl'."""
        unlocked = self.engine.evaluate_playback_session(
            episodes_completed=1,
            total_watch_time_ms=1800000,
            is_shuffle_play=False,
            deck_cycle_completed=False,
            current_hour=3  # 03:00 to 05:00
        )
        unlocked_ids = [a.id for a in unlocked]
        self.assertIn("night_owl", unlocked_ids)

    def test_sacred_playback_rule_zero_mid_playback_toasts(self):
        """Test 5: Mid-playback toast count is 0; achievements queued exclusively for post-playback screen."""
        self.engine.evaluate_playback_session(
            episodes_completed=10,
            total_watch_time_ms=260000000,
            is_shuffle_play=True,
            deck_cycle_completed=True,
            current_hour=4
        )
        # Verify 0 toasts shown during active video
        self.assertEqual(self.engine.active_playback_toast_count, 0)
        # Verify celebrations are queued for the end screen
        self.assertGreater(len(self.engine.pending_end_screen_celebrations), 0)


if __name__ == "__main__":
    unittest.main()
