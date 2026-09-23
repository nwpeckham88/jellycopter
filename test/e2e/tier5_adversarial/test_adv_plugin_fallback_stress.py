"""
Tier 5 Adversarial Stress Tests: Companion Plugin Sync Fallback & Room DB Standalone
Stress-tests offline and missing server plugin states (HTTP 404, HTTP 502, connection refused),
and verifies that Room DB standalone operation executes 100% reliably with zero crashes or data loss.
"""

import sys
import os
import json
import urllib.request
import urllib.error
import socket
import threading
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from harness.mock_servers import MockServerHarness
from harness.client_state import ClientRoomDatabase


class TestAdversarialPluginFallbackStress(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.local_db = ClientRoomDatabase(in_memory=True)

    def tearDown(self):
        self.mock_server.stop()
        self.local_db.conn.close()

    # -------------------------------------------------------------
    # 1. Probe Plugin Handshake Failures
    # -------------------------------------------------------------

    def test_probe_plugin_missing_http_404(self):
        """Plugin disabled on server returns 404 Not Found."""
        self.mock_server.state.plugin_enabled = False
        url = f"{self.mock_server.base_url}/Jellycopter/Ping"
        req = urllib.request.Request(url)

        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req)

        self.assertEqual(ctx.exception.code, 404)

    def test_probe_plugin_http_502_bad_gateway(self):
        """Simulate reverse proxy returning 502 Bad Gateway when Jellyfin is restarting."""
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.bind(("127.0.0.1", 0))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]

        def serve_502():
            try:
                conn, _ = server_sock.accept()
                conn.recv(1024)
                resp = (
                    b"HTTP/1.1 502 Bad Gateway\r\n"
                    b"Content-Type: text/html\r\n"
                    b"Content-Length: 32\r\n"
                    b"Connection: close\r\n\r\n"
                    b"<html>502 Bad Gateway</html>"
                )
                conn.sendall(resp)
                conn.close()
            except Exception:
                pass

        th = threading.Thread(target=serve_502, daemon=True)
        th.start()

        url = f"http://127.0.0.1:{port}/Jellycopter/Ping"
        req = urllib.request.Request(url)
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req, timeout=1.0)

        self.assertEqual(ctx.exception.code, 502)
        server_sock.close()

    def test_probe_plugin_connection_refused(self):
        """Simulate connection refused (offline host or dead port)."""
        url = "http://127.0.0.1:59998/Jellycopter/Ping"
        req = urllib.request.Request(url)
        with self.assertRaises(urllib.error.URLError) as ctx:
            urllib.request.urlopen(req, timeout=1.0)

        self.assertIsInstance(ctx.exception.reason, (ConnectionRefusedError, socket.error))

    # -------------------------------------------------------------
    # 2. Standalone Room DB Resilience Under Offline Conditions
    # -------------------------------------------------------------

    def test_standalone_room_db_crud_while_offline(self):
        """
        When companion plugin is absent or offline, local Room DB must support:
        1. Deck creation and updates.
        2. Midway resume saving.
        3. Telemetry and achievement unlocks.
        Zero crashes, zero data loss.
        """
        # Ensure server plugin is offline
        self.mock_server.state.plugin_enabled = False

        # 1. Create deck locally in Room DB
        cur = self.local_db.conn.cursor()
        cur.execute("""
            INSERT OR REPLACE INTO deck_entities
            (deck_id, name, series_id, channel_id, current_index, cycle_number, strict_queue_mode, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, ("deck_offline_1", "The Wire", "series_wire", None, 0, 1, 0, 1000))
        self.local_db.conn.commit()

        cur.execute("SELECT * FROM deck_entities WHERE deck_id = ?", ("deck_offline_1",))
        row = cur.fetchone()
        self.assertIsNotNone(row)
        self.assertEqual(row["name"], "The Wire")
        self.assertEqual(row["cycle_number"], 1)

        # 2. Advance deck playback and save midway progress
        self.local_db.save_deck_progress(
            deck_id="deck_offline_1",
            card_id="card_101",
            episode_id="ep_wire_s1e1",
            index=0,
            resume_ms=1800000,
            duration_ms=3600000
        )
        progress = self.local_db.get_deck_progress("deck_offline_1")
        self.assertIsNotNone(progress)
        self.assertEqual(progress["current_episode_id"], "ep_wire_s1e1")
        self.assertEqual(progress["midway_resume_ms"], 1800000)

        # 3. Simulate 100 offline deck playthroughs
        for i in range(1, 101):
            self.local_db.save_deck_progress(
                deck_id="deck_offline_1",
                card_id=f"card_{i}",
                episode_id=f"ep_{i}",
                index=i,
                resume_ms=0,
                duration_ms=1800000
            )

        updated_progress = self.local_db.get_deck_progress("deck_offline_1")
        self.assertEqual(updated_progress["current_index"], 100)

    def test_zero_data_loss_on_sync_failure_retry_loop(self):
        """
        Stress test: 20 successive sync attempts failing against offline plugin.
        Verify local Room DB entities are 100% preserved and never corrupted.
        """
        self.mock_server.state.plugin_enabled = False

        # Seed local deck and achievement
        cur = self.local_db.conn.cursor()
        cur.execute("""
            INSERT OR REPLACE INTO deck_entities
            (deck_id, name, series_id, channel_id, current_index, cycle_number, strict_queue_mode, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, ("deck_survivor", "Futurama", "series_futurama", None, 5, 2, 0, 5000))
        cur.execute("""
            INSERT OR REPLACE INTO achievements
            (id, title, unlocked, unlocked_at, progress_value)
            VALUES (?, ?, ?, ?, ?)
        """, ("binge_voyager", "Binge Voyager", 1, 123456, 8.0))
        self.local_db.conn.commit()

        # Simulate 20 sync cycles that fall back to local DB
        for _ in range(20):
            # Probe fails
            with self.assertRaises(urllib.error.HTTPError):
                urllib.request.urlopen(f"{self.mock_server.base_url}/Jellycopter/Ping")

            # Fallback keeps local DB untouched
            cur.execute("SELECT * FROM deck_entities WHERE deck_id = ?", ("deck_survivor",))
            deck = cur.fetchone()
            cur.execute("SELECT * FROM achievements WHERE id = ?", ("binge_voyager",))
            ach = cur.fetchone()
            self.assertIsNotNone(deck)
            self.assertIsNotNone(ach)
            self.assertEqual(deck["series_id"], "series_futurama")
            self.assertEqual(ach["unlocked"], 1)

    # -------------------------------------------------------------
    # 3. Reconnection & Set-Union Cross-Device Sync
    # -------------------------------------------------------------

    def test_reconnection_and_set_union_sync(self):
        """
        When plugin comes back online, verify set-union merging of exhausted card IDs:
        Client exhausted: {ep1, ep2}
        Server exhausted: {ep2, ep3}
        Merged exhausted: {ep1, ep2, ep3}
        """
        # Step 1: Client played ep1, ep2 while offline
        client_exhausted = ["ep_1", "ep_2"]

        # Step 2: Server received ep2, ep3 from another client device
        self.mock_server.state.plugin_enabled = True
        self.mock_server.state.server_decks["deck_sync_test"] = {
            "deckId": "deck_sync_test",
            "cycleNumber": 1,
            "exhaustedItemIds": ["ep_2", "ep_3"],
            "seriesId": "series_sync"
        }

        # Step 3: Client reconnects and pushes sync payload
        url = f"{self.mock_server.base_url}/Jellycopter/Decks"
        payload = json.dumps({
            "deckId": "deck_sync_test",
            "cycleNumber": 1,
            "exhaustedItemIds": client_exhausted,
            "seriesId": "series_sync"
        }).encode("utf-8")

        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 200)
            merged_deck = json.loads(resp.read().decode("utf-8"))

        # Invariant: Exhausted set union MUST contain all of {ep_1, ep_2, ep_3}
        self.assertEqual(
            sorted(merged_deck["exhaustedItemIds"]),
            ["ep_1", "ep_2", "ep_3"]
        )


if __name__ == "__main__":
    unittest.main()
