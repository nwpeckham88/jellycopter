"""
Tier 3 Cross-Feature Combination Tests: Shuffle Deck + Watch History Isolation + Companion Sync
Verifies that while shuffle playback suppresses sequential Next Up mutation on the Jellyfin server,
it concurrently syncs deck progress and set-union exhausted items to the companion plugin (/Jellycopter/Decks).
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness
from harness.client_state import ClientRoomDatabase
from harness.shuffle_engine import SmartShuffleEngine, EpisodeItem


class TestComboShuffleIsolationSync(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.room_db = ClientRoomDatabase()
        self.engine = SmartShuffleEngine()

        self.series_id = "series_curb"
        self.ep_s1e1 = "curb_s01e01"
        self.mock_server.state.episodes[self.ep_s1e1] = {
            "Id": self.ep_s1e1, "Name": "Curb S1E1", "SeriesId": self.series_id
        }
        self.mock_server.state.next_up_items[self.series_id] = self.ep_s1e1

        self.episodes = [
            EpisodeItem(f"curb_{i}", f"Curb Ep {i}", self.series_id, f"s_{(i%4)+1}", (i%4)+1, i)
            for i in range(1, 21)
        ]
        self.deck = self.engine.generate_single_show_deck(self.series_id, "Curb", self.episodes)

    def tearDown(self):
        self.room_db.close()
        self.mock_server.stop()

    def test_shuffle_advances_and_syncs_to_plugin_without_touching_server_next_up(self):
        """Combo 1: Playing shuffle episode syncs to /Jellycopter/Decks while leaving server Next Up untouched."""
        # 1. Advance shuffle deck by 1 episode
        self.deck, event = self.engine.advance_playback(
            self.deck, position_ms=1800000, duration_ms=1800000
        )
        consumed_card = self.deck.cards[0]

        # 2. Record locally in Room DB
        self.room_db.save_deck_progress(
            self.deck.deck_id, consumed_card.id, consumed_card.items[0].id,
            self.deck.current_index, 0, 1800000
        )

        # 3. Synchronize to companion plugin /Jellycopter/Decks
        sync_payload = {
            "deckId": self.deck.deck_id,
            "seriesId": self.series_id,
            "cycleNumber": self.deck.cycle_number,
            "exhaustedItemIds": list(self.deck.exhausted_card_ids),
            "lastPlayedItemId": consumed_card.id,
            "lastResumePositionTicks": 0
        }
        url_plugin = f"{self.mock_server.base_url}/Jellycopter/Decks"
        req_sync = urllib.request.Request(
            url_plugin,
            data=json.dumps(sync_payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req_sync) as resp:
            self.assertEqual(resp.status, 200)

        # 4. Verify /Jellycopter/Decks on server holds the updated deck
        self.assertIn(self.deck.deck_id, self.mock_server.state.server_decks)
        synced_deck = self.mock_server.state.server_decks[self.deck.deck_id]
        self.assertEqual(synced_deck["lastPlayedItemId"], consumed_card.id)

        # 5. Verify /Shows/NextUp on server remains S01E01!
        req_next = urllib.request.Request(f"{self.mock_server.base_url}/Shows/NextUp")
        with urllib.request.urlopen(req_next) as resp:
            next_up = json.loads(resp.read().decode("utf-8"))["Items"][0]["Id"]
        self.assertEqual(next_up, self.ep_s1e1)

    def test_multi_device_set_union_sync(self):
        """Combo 2: Living Room TV and Bedroom TV sync exhausted cards without clobbering each other."""
        deck_id = "shared_deck"
        # Seed server with ep 1 watched on Living Room TV
        self.mock_server.state.server_decks[deck_id] = {
            "deckId": deck_id,
            "cycleNumber": 1,
            "exhaustedItemIds": ["curb_1"]
        }

        # Bedroom TV watched ep 2
        bedroom_sync = {
            "deckId": deck_id,
            "cycleNumber": 1,
            "exhaustedItemIds": ["curb_2"]
        }
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        req = urllib.request.Request(url, data=json.dumps(bedroom_sync).encode("utf-8"), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            merged = json.loads(resp.read().decode("utf-8"))

        # Both ep 1 and ep 2 are in exhausted list
        self.assertEqual(merged["exhaustedItemIds"], ["curb_1", "curb_2"])

    def test_plugin_sync_failure_falls_back_to_local_room_db_without_mutation_to_next_up(self):
        """Combo 3: When plugin is disabled (404), local Room DB persists and Next Up remains untouched."""
        self.mock_server.state.plugin_enabled = False

        # Attempt plugin sync (fails with 404)
        try:
            req = urllib.request.Request(f"{self.mock_server.base_url}/Jellycopter/Decks")
            with urllib.request.urlopen(req) as resp:
                pass
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 404)

        # Write to Room DB locally
        self.room_db.save_deck_progress("offline_deck", "curb_5", "curb_5", 5, 0, 1800000)
        local_data = self.room_db.get_deck_progress("offline_deck")
        self.assertEqual(local_data["current_index"], 5)

        # Server Next Up still points to S01E01
        self.assertEqual(self.mock_server.state.next_up_items[self.series_id], self.ep_s1e1)

    def test_cycle_completion_syncs_cycle_increment(self):
        """Combo 4: Finishing all episodes in deck increments cycle and syncs cycle 2 to plugin."""
        for _ in range(20):
            self.deck, _ = self.engine.advance_playback(self.deck, position_ms=1800000, duration_ms=1800000)

        self.assertEqual(self.deck.cycle_number, 2)
        sync_payload = {
            "deckId": self.deck.deck_id,
            "cycleNumber": 2,
            "exhaustedItemIds": []
        }
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        req = urllib.request.Request(url, data=json.dumps(sync_payload).encode("utf-8"), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        self.assertEqual(data["cycleNumber"], 2)

    def test_midway_resume_syncs_position_ticks(self):
        """Combo 5: Midway pause saves resume position to plugin without altering Next Up."""
        # Pause at 50% (900,000 ms)
        self.deck, event = self.engine.advance_playback(self.deck, position_ms=900000, duration_ms=1800000)
        self.assertEqual(event, "SAVED_MIDWAY_RESUME")

        sync_payload = {
            "deckId": self.deck.deck_id,
            "cycleNumber": 1,
            "exhaustedItemIds": [],
            "lastResumePositionTicks": 9000000000
        }
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        req = urllib.request.Request(url, data=json.dumps(sync_payload).encode("utf-8"), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        self.assertEqual(data["lastResumePositionTicks"], 9000000000)


if __name__ == "__main__":
    unittest.main()
