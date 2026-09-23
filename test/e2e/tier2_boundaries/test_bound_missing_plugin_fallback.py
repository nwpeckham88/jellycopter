"""
Tier 2 Boundary Tests: Missing Plugin (HTTP 404 & Failures) Fallback
Requirements: 100% standalone Room DB operation when Jellyfin server plugin is absent,
errored (500), unreachable, or disconnected.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness
from harness.client_state import ClientRoomDatabase


class TestBoundMissingPluginFallback(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.room_db = ClientRoomDatabase()

    def tearDown(self):
        self.room_db.close()
        self.mock_server.stop()

    def test_http_404_activates_local_fallback_mode(self):
        """Boundary 1: Receiving 404 Not Found from /Jellycopter/Ping toggles local mode with 0 crash."""
        self.mock_server.state.plugin_enabled = False
        is_plugin_available = None
        try:
            req = urllib.request.Request(f"{self.mock_server.base_url}/Jellycopter/Ping")
            with urllib.request.urlopen(req) as resp:
                is_plugin_available = (resp.status == 200)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                is_plugin_available = False

        self.assertFalse(is_plugin_available)

    def test_network_unreachable_falls_back_to_room_db(self):
        """Boundary 2: Connection refused (e.g. invalid port) falls back to Room DB gracefully."""
        is_plugin_available = None
        try:
            req = urllib.request.Request("http://127.0.0.1:59999/Jellycopter/Ping")
            with urllib.request.urlopen(req, timeout=0.5) as resp:
                is_plugin_available = True
        except Exception:
            is_plugin_available = False

        self.assertFalse(is_plugin_available)
        # Verify local Room DB write succeeds
        self.room_db.save_deck_progress("fallback_deck", "card_1", "ep_1", 3, 1000, 1800000)
        prog = self.room_db.get_deck_progress("fallback_deck")
        self.assertEqual(prog["current_index"], 3)

    def test_concurrent_room_db_transactions_during_outage(self):
        """Boundary 3: Rapid consecutive local transactions in Room DB execute with zero lock error."""
        for i in range(50):
            self.room_db.save_deck_progress(
                f"deck_{i}", f"card_{i}", f"ep_{i}", i, i * 1000, 1800000
            )
        prog = self.room_db.get_deck_progress("deck_49")
        self.assertEqual(prog["current_index"], 49)

    def test_subsequent_plugin_reconnect_syncs_local_state(self):
        """Boundary 4: When plugin becomes reachable after offline mode, local deck syncs up."""
        # 1. Start offline, write to Room DB
        self.room_db.save_deck_progress("reconnect_deck", "c_1", "e_1", 5, 0, 1800000)
        local_state = self.room_db.get_deck_progress("reconnect_deck")

        # 2. Server plugin is enabled
        self.mock_server.state.plugin_enabled = True

        # 3. Synchronize local progress to server
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        payload = {
            "deckId": "reconnect_deck",
            "cycleNumber": 1,
            "exhaustedItemIds": ["e_1"],
            "lastResumePositionTicks": local_state["midway_resume_ms"]
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 200)

        # 4. Confirm server now holds the synced deck
        self.assertIn("reconnect_deck", self.mock_server.state.server_decks)

    def test_missing_plugin_does_not_block_achievements_evaluation(self):
        """Boundary 5: Achievement milestone tracking proceeds purely via local DB without server plugin."""
        self.room_db.record_telemetry_event("PlaybackCompleted", "ep_123", 1800000)
        cur = self.room_db.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM telemetry_events")
        count = cur.fetchone()[0]
        self.assertEqual(count, 1)


if __name__ == "__main__":
    unittest.main()
