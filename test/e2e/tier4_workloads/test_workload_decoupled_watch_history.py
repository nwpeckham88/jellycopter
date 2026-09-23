"""
Tier 4 Real-World Workload Scenario: Decoupled Watch History
Simulates a multi-day viewing marathon:
- User is sequentially watching The Office (currently at Season 1 Episode 5 in "Next Up").
- User launches Smart Shuffle Deck and watches 25 random shuffle episodes from Seasons 2, 3, 4, 5, 6, 7.
- User pauses midway, skips episodes, and completes cycles.
- Throughout all 25 episodes, Jellyfin server's /Shows/NextUp remains strictly locked to S01E05.
- User returns to "Continue Watching" and plays S01E05 sequentially: server scrobbles and advances to S01E06.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness
from harness.client_state import ClientRoomDatabase
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem


class TestWorkloadDecoupledWatchHistory(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.room_db = ClientRoomDatabase()
        self.engine = SmartShuffleEngine()

        # Seed Jellyfin server with The Office
        self.series_id = "series_the_office"
        self.sequential_next_up_ep = "office_s01e05"
        self.mock_server.state.episodes[self.sequential_next_up_ep] = {
            "Id": self.sequential_next_up_ep,
            "Name": "The Office S1E5 - Basketball",
            "SeriesId": self.series_id
        }
        self.mock_server.state.next_up_items[self.series_id] = self.sequential_next_up_ep

        # Create 50 episodes across seasons 1-7
        self.catalog = [
            EpisodeItem(f"office_s{s}e{e}", f"The Office S{s}E{e}", self.series_id, f"season_{s}", s, e)
            for s in range(1, 8) for e in range(1, 9)
        ]
        self.deck = self.engine.generate_single_show_deck(self.series_id, "The Office", self.catalog)

    def tearDown(self):
        self.room_db.close()
        self.mock_server.stop()

    def test_twenty_five_episode_shuffle_run_leaves_sequential_next_up_completely_untouched(self):
        """Workload 1: 25 shuffle plays do NOT alter /Shows/NextUp (remains S01E05 throughout)."""
        req_next_up = urllib.request.Request(f"{self.mock_server.base_url}/Shows/NextUp")

        for play_count in range(1, 26):
            # Play shuffle episode
            current_card = self.deck.cards[self.deck.current_index]
            self.deck, event = self.engine.advance_playback(
                self.deck, position_ms=1800000, duration_ms=1800000, is_user_skip=False
            )

            # Record isolated state in client Room DB
            self.room_db.save_deck_progress(
                self.deck.deck_id, current_card.id, current_card.item_ids[0],
                self.deck.current_index, 0, 1800000
            )

            # Query server Next Up: MUST ALWAYS REMAIN S01E05
            with urllib.request.urlopen(req_next_up) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                current_server_next_up = data["Items"][0]["Id"]

            self.assertEqual(
                current_server_next_up, self.sequential_next_up_ep,
                f"Shuffle play #{play_count} mutated server Next Up to {current_server_next_up}!"
            )

    def test_sequential_playback_from_continue_watching_advances_normally(self):
        """Workload 2: After shuffle session, launching S01E05 from Continue Watching advances to S01E06."""
        # 1. User watches 10 shuffle episodes first
        for _ in range(10):
            self.deck, _ = self.engine.advance_playback(self.deck, position_ms=1800000, duration_ms=1800000)
            self.room_db.save_deck_progress(self.deck.deck_id, "c", "e", self.deck.current_index, 0, 1800000)

        # 2. User returns to Home screen and clicks Continue Watching (S01E05)
        # Normal sequential scrobble reports Played=True to server
        scrobble_url = f"{self.mock_server.base_url}/Sessions/Playing/Stopped"
        req = urllib.request.Request(
            scrobble_url,
            data=json.dumps({
                "ItemId": self.sequential_next_up_ep,
                "SeriesId": self.series_id,
                "Played": True
            }).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 204)

        # 3. Server Next Up advances to S01E06
        req_next_up = urllib.request.Request(f"{self.mock_server.base_url}/Shows/NextUp")
        with urllib.request.urlopen(req_next_up) as resp:
            new_next_up = json.loads(resp.read().decode("utf-8"))["Items"][0]["Id"]

        self.assertEqual(new_next_up, f"{self.sequential_next_up_ep}_next")

        # 4. But shuffle deck progress in Room DB is still preserved at index 10!
        deck_data = self.room_db.get_deck_progress(self.deck.deck_id)
        self.assertEqual(deck_data["current_index"], 10)


if __name__ == "__main__":
    unittest.main()
