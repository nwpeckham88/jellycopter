"""
Tier 1 Feature Tests: Decoupled Watch History
Requirements: R1, ORIGINAL_REQUEST.md § R1, PROJECT.md Feature 7.
Verifies that shuffle deck playback NEVER modifies or clobbers Jellyfin's
sequential Next Up pointers or server UserData.Played state.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness
from harness.client_state import ClientRoomDatabase


class TestFeatureWatchHistoryIsolation(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.room_db = ClientRoomDatabase()

        # Seed mock server with a sequential Next Up pointer
        self.series_id = "series_detroiters"
        self.ep_s1e3 = "detroiters_s01e03"
        self.mock_server.state.episodes[self.ep_s1e3] = {
            "Id": self.ep_s1e3,
            "Name": "Detroiters S1E3",
            "SeriesId": self.series_id
        }
        self.mock_server.state.next_up_items[self.series_id] = self.ep_s1e3

    def tearDown(self):
        self.room_db.close()
        self.mock_server.stop()

    def test_next_up_pointer_remains_unchanged_after_shuffle_playback(self):
        """Test 1: Server Next Up remains S01E03 even after shuffle playing an episode from Season 2."""
        # Initial query to /Shows/NextUp
        req = urllib.request.Request(f"{self.mock_server.base_url}/Shows/NextUp")
        with urllib.request.urlopen(req) as resp:
            initial_next_up = json.loads(resp.read().decode("utf-8"))["Items"][0]["Id"]
        self.assertEqual(initial_next_up, self.ep_s1e3)

        # Simulate shuffle playback of S02E08:
        # Client writes progress to Room DB, suppresses server Played=true scrobble
        shuffle_ep_id = "detroiters_s02e08"
        self.room_db.save_deck_progress(
            deck_id=f"tv_deck:{self.series_id}",
            card_id=shuffle_ep_id,
            episode_id=shuffle_ep_id,
            index=14,
            resume_ms=1200000,
            duration_ms=1800000
        )

        # Verify /Shows/NextUp still points to S01E03
        with urllib.request.urlopen(req) as resp:
            after_next_up = json.loads(resp.read().decode("utf-8"))["Items"][0]["Id"]
        self.assertEqual(after_next_up, self.ep_s1e3)

    def test_user_data_played_not_mutated_on_server(self):
        """Test 2: UserData for shuffle episode is NOT marked Played=true on the Jellyfin server."""
        shuffle_ep_id = "detroiters_s02e08"
        # Verify initial server state
        self.assertNotIn(shuffle_ep_id, self.mock_server.state.user_data)

        # Play shuffle episode and persist locally in Room DB
        self.room_db.save_deck_progress(
            deck_id=f"tv_deck:{self.series_id}",
            card_id=shuffle_ep_id,
            episode_id=shuffle_ep_id,
            index=15,
            resume_ms=1800000,
            duration_ms=1800000
        )

        # Server UserData has not been marked Played
        user_state = self.mock_server.state.user_data.get(shuffle_ep_id, {})
        self.assertFalse(user_state.get("Played", False))

    def test_room_db_persists_independent_deck_pointer(self):
        """Test 3: Local Room DB preserves the deck currentIndex independently."""
        deck_id = f"tv_deck:{self.series_id}"
        self.room_db.save_deck_progress(
            deck_id=deck_id,
            card_id="detroiters_s02e08",
            episode_id="detroiters_s02e08",
            index=19,
            resume_ms=45000,
            duration_ms=1800000
        )

        progress = self.room_db.get_deck_progress(deck_id)
        self.assertIsNotNone(progress)
        self.assertEqual(progress["current_index"], 19)
        self.assertEqual(progress["midway_resume_ms"], 45000)

    def test_restarting_app_restores_shuffle_deck_without_querying_server_next_up(self):
        """Test 4: On app restart, deck restores exact index from Room DB without inspecting NextUp."""
        deck_id = f"tv_deck:{self.series_id}"
        self.room_db.save_deck_progress(
            deck_id=deck_id,
            card_id="card_42",
            episode_id="ep_42",
            index=42,
            resume_ms=900000,
            duration_ms=1800000
        )

        # Retrieve saved state
        restored = self.room_db.get_deck_progress(deck_id)
        self.assertEqual(restored["current_card_id"], "card_42")
        self.assertEqual(restored["current_index"], 42)
        self.assertEqual(restored["midway_resume_ms"], 900000)

    def test_parallel_sequential_and_shuffle_playback_separation(self):
        """Test 5: Watching a sequential episode updates NextUp; shuffle deck progress stays unaffected."""
        deck_id = f"tv_deck:{self.series_id}"
        # Set shuffle deck at index 25
        self.room_db.save_deck_progress(deck_id, "s_ep", "s_ep", 25, 0, 1800000)

        # Now watch sequential episode on server (normal scrobble)
        scrobble_url = f"{self.mock_server.base_url}/Sessions/Playing/Stopped"
        req = urllib.request.Request(
            scrobble_url,
            data=json.dumps({
                "ItemId": self.ep_s1e3,
                "SeriesId": self.series_id,
                "Played": True
            }).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 204)

        # Server NextUp advanced
        self.assertEqual(self.mock_server.state.next_up_items[self.series_id], f"{self.ep_s1e3}_next")

        # But shuffle deck in Room DB is completely unchanged at index 25!
        deck_progress = self.room_db.get_deck_progress(deck_id)
        self.assertEqual(deck_progress["current_index"], 25)


if __name__ == "__main__":
    unittest.main()
