"""
Tier 1 Feature Tests: Companion Plugin Sync & Graceful Local Room DB Fallback
Requirements: R5, ORIGINAL_REQUEST.md § R5, PROJECT.md Features 23 & 24.
Verifies /Jellycopter/Ping handshake, set-union deck merging, and 100% standalone
Room DB operation when companion plugin is absent (HTTP 404).
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness
from harness.client_state import ClientRoomDatabase


class TestFeaturePluginSyncFallback(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.room_db = ClientRoomDatabase()

    def tearDown(self):
        self.room_db.close()
        self.mock_server.stop()

    def test_ping_handshake_detects_companion_plugin(self):
        """Test 1: GET /Jellycopter/Ping returns 200 OK with version string when plugin is installed."""
        url = f"{self.mock_server.base_url}/Jellycopter/Ping"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
        self.assertEqual(data["version"], "1.0.0")

    def test_set_union_merging_of_same_cycle_exhausted_items(self):
        """Test 2: When client and server are on the same cycle, exhausted items are merged via set-union."""
        deck_id = "deck_simpsons"
        # Seed server deck with episodes 1 and 2 exhausted
        self.mock_server.state.server_decks[deck_id] = {
            "deckId": deck_id,
            "cycleNumber": 1,
            "exhaustedItemIds": ["ep_01", "ep_02"]
        }

        # Client syncs with episodes 2 and 3 exhausted
        sync_payload = {
            "deckId": deck_id,
            "cycleNumber": 1,
            "exhaustedItemIds": ["ep_02", "ep_03"]
        }
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        req = urllib.request.Request(
            url,
            data=json.dumps(sync_payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            merged_deck = json.loads(resp.read().decode("utf-8"))

        # Merged exhausted items must be set-union: {ep_01, ep_02, ep_03}
        self.assertEqual(merged_deck["exhaustedItemIds"], ["ep_01", "ep_02", "ep_03"])

    def test_client_cycle_ahead_overwrites_server_cycle(self):
        """Test 3: If client cycleNumber > server cycleNumber, server adopts client's new cycle state."""
        deck_id = "deck_futurama"
        self.mock_server.state.server_decks[deck_id] = {
            "deckId": deck_id,
            "cycleNumber": 1,
            "exhaustedItemIds": ["ep_01", "ep_02"]
        }

        # Client has finished cycle 1 and started cycle 2
        sync_payload = {
            "deckId": deck_id,
            "cycleNumber": 2,
            "exhaustedItemIds": ["ep_05"]
        }
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        req = urllib.request.Request(
            url,
            data=json.dumps(sync_payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            updated_deck = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(updated_deck["cycleNumber"], 2)
        self.assertEqual(updated_deck["exhaustedItemIds"], ["ep_05"])

    def test_plugin_404_activates_graceful_room_db_fallback(self):
        """Test 4: When plugin is disabled/uninstalled (404), client falls back to local Room DB seamlessly."""
        self.mock_server.state.plugin_enabled = False

        url = f"{self.mock_server.base_url}/Jellycopter/Ping"
        req = urllib.request.Request(url)
        is_plugin_available = True
        try:
            with urllib.request.urlopen(req) as resp:
                pass
        except urllib.error.HTTPError as e:
            if e.code == 404:
                is_plugin_available = False

        self.assertFalse(is_plugin_available)

        # Standalone Room DB operation proceeds with zero failure
        self.room_db.save_deck_progress("local_deck", "card_1", "ep_1", 1, 0, 1800000)
        progress = self.room_db.get_deck_progress("local_deck")
        self.assertEqual(progress["current_card_id"], "card_1")

    def test_local_room_db_persists_without_network(self):
        """Test 5: Client Room DB handles full write/read cycle in offline mode."""
        self.room_db.save_deck_progress(
            deck_id="offline_deck",
            card_id="c_10",
            episode_id="e_10",
            index=10,
            resume_ms=500000,
            duration_ms=1800000
        )
        data = self.room_db.get_deck_progress("offline_deck")
        self.assertIsNotNone(data)
        self.assertEqual(data["current_index"], 10)
        self.assertEqual(data["midway_resume_ms"], 500000)


if __name__ == "__main__":
    unittest.main()
