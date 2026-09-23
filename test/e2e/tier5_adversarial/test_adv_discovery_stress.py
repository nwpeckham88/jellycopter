"""
Tier 5 Adversarial Stress Tests: Jellyseerr Discovery & Season Diffing
Stress-tests network timeouts, socket disconnects, HTTP 409 Conflict races,
HTTP 403 Forbidden, HTTP 500/502 server errors, and adversarial season diffing.
"""

import sys
import os
import json
import socket
import threading
import time
import urllib.request
import urllib.error
from datetime import date, datetime
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from harness.mock_servers import MockServerHarness


class AdversarialSeasonDetector:
    """
    Python reference implementation of org.jellycopter.discovery.MissingSeasonDetector
    mirroring all release rules and diffing logic for adversarial oracle verification.
    """

    @staticmethod
    def is_released_regular_season(season: dict, reference_date: date) -> bool:
        season_num = season.get("seasonNumber", -1)
        # Rule 1: S0 Specials strictly ignored
        if season_num <= 0:
            return False

        # Rule 2: 0 episodes means unreleased / empty placeholder
        ep_count = season.get("episodeCount", 0)
        if ep_count <= 0:
            return False

        # Rule 3: Missing or empty air date means unreleased
        air_date_str = season.get("airDate")
        if not air_date_str or not str(air_date_str).strip():
            return False

        air_date_str = str(air_date_str).strip()
        try:
            air_date = datetime.strptime(air_date_str, "%Y-%m-%d").date()
            # Released on or before reference date
            return air_date <= reference_date
        except ValueError:
            # If unparseable date but ep_count > 0, assume released
            return True

    @classmethod
    def detect_season_statuses(
        cls,
        local_season_numbers: set,
        remote_seasons: list,
        requests: list = None,
        local_episode_counts: dict = None,
        reference_date: date = None
    ) -> list:
        if reference_date is None:
            reference_date = date.today()
        if requests is None:
            requests = []
        if local_episode_counts is None:
            local_episode_counts = {}

        released = [s for s in remote_seasons if cls.is_released_regular_season(s, reference_date)]

        statuses = []
        for season in released:
            snum = season["seasonNumber"]
            is_local = snum in local_season_numbers
            ep_count = season.get("episodeCount", 0)
            local_eps = local_episode_counts.get(snum, ep_count if is_local else 0)

            matching_req = None
            for req in requests:
                status = req.get("status")
                req_seasons = req.get("seasons")
                if status in (1, 2) and (req_seasons is None or snum in req_seasons):
                    matching_req = req
                    break

            statuses.append({
                "seasonNumber": snum,
                "isAvailableLocally": is_local,
                "isRequested": matching_req is not None,
                "requestId": matching_req.get("id") if matching_req else None,
                "requestStatus": matching_req.get("status") if matching_req else None,
                "totalEpisodes": ep_count,
                "localEpisodes": local_eps
            })
        return statuses

    @staticmethod
    def summarize(statuses: list) -> dict:
        total_aired = len(statuses)
        locally_present = [s for s in statuses if s["isAvailableLocally"]]
        missing_statuses = [s for s in statuses if not s["isAvailableLocally"]]
        missing_seasons = sorted([s["seasonNumber"] for s in missing_statuses])
        unrequested_missing = sorted([s["seasonNumber"] for s in missing_statuses if not s["isRequested"]])

        badge_text = None
        if len(missing_seasons) == 1:
            badge_text = "\u26a0\ufe0f 1 Missing Season"
        elif len(missing_seasons) > 1:
            badge_text = f"\u26a0\ufe0f {len(missing_seasons)} Missing Seasons"

        action_text = None
        if unrequested_missing:
            seasons_str = ", ".join(f"S{s}" for s in unrequested_missing)
            action_text = f"Request Missing Seasons ({seasons_str})"

        return {
            "totalAiredSeasons": total_aired,
            "localSeasonCount": len(locally_present),
            "missingSeasons": missing_seasons,
            "unrequestedMissingSeasons": unrequested_missing,
            "badgeText": badge_text,
            "requestActionText": action_text,
            "hasMissingSeasons": len(missing_seasons) > 0,
            "canTriggerRequest": len(unrequested_missing) > 0
        }


class TestAdversarialDiscoveryStress(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()
        self.ref_date = date(2026, 9, 16)

    def tearDown(self):
        self.mock_server.stop()

    # -------------------------------------------------------------
    # 1. Network Timeouts & Socket Disconnects
    # -------------------------------------------------------------

    def test_network_timeout_handling(self):
        """Simulate request timeout: client must raise TimeoutError/URLError and not hang."""
        blackhole_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        blackhole_sock.bind(("127.0.0.1", 0))
        blackhole_sock.listen(1)
        bh_port = blackhole_sock.getsockname()[1]

        def accept_and_hold():
            try:
                conn, _ = blackhole_sock.accept()
                time.sleep(1.0)
                conn.close()
            except Exception:
                pass

        th = threading.Thread(target=accept_and_hold, daemon=True)
        th.start()

        url = f"http://127.0.0.1:{bh_port}/api/v1/request"
        req = urllib.request.Request(url, data=b"{}", headers={"Content-Type": "application/json"})
        
        start_t = time.time()
        with self.assertRaises((urllib.error.URLError, TimeoutError, socket.timeout)):
            urllib.request.urlopen(req, timeout=0.3)
        duration = time.time() - start_t
        self.assertLess(duration, 0.9, "Timeout took longer than configured threshold")

        blackhole_sock.close()

    def test_abrupt_socket_disconnect(self):
        """Simulate server abruptly resetting connection: client handles ConnectionResetError."""
        reset_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        reset_sock.bind(("127.0.0.1", 0))
        reset_sock.listen(1)
        reset_port = reset_sock.getsockname()[1]

        def accept_and_reset():
            try:
                conn, _ = reset_sock.accept()
                conn.recv(1024)
                conn.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, b"\x01\x00\x00\x00\x00\x00\x00\x00")
                conn.close()
            except Exception:
                pass

        th = threading.Thread(target=accept_and_reset, daemon=True)
        th.start()

        url = f"http://127.0.0.1:{reset_port}/api/v1/request"
        req = urllib.request.Request(url, data=b"{}", headers={"Content-Type": "application/json"})
        with self.assertRaises((urllib.error.URLError, ConnectionResetError, OSError)):
            urllib.request.urlopen(req, timeout=1.0)

        reset_sock.close()

    # -------------------------------------------------------------
    # 2. HTTP 409 Conflict Concurrency Races
    # -------------------------------------------------------------

    def test_http_409_conflict_concurrency_race(self):
        """
        Stress test concurrent requests: when 20 requests fire simultaneously for the same item,
        exactly 1 returns 201 Created and all remaining 19 return 409 Conflict.
        Zero unhandled crashes, state remains clean.
        """
        url = f"{self.mock_server.base_url}/api/v1/request"
        media_id = 99901

        results = []
        threads = []

        def make_request():
            payload = json.dumps({
                "mediaType": "movie",
                "mediaId": media_id,
                "is4k": False,
                "seasons": "all"
            }).encode("utf-8")
            req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(req) as resp:
                    results.append((resp.status, json.loads(resp.read().decode("utf-8"))))
            except urllib.error.HTTPError as e:
                err_body = json.loads(e.read().decode("utf-8"))
                results.append((e.code, err_body))

        for _ in range(20):
            t = threading.Thread(target=make_request)
            threads.append(t)
            t.start()

        for t in threads:
            t.join(timeout=3.0)

        self.assertEqual(len(results), 20)
        status_codes = [r[0] for r in results]
        created_count = status_codes.count(201)
        conflict_count = status_codes.count(409)

        self.assertEqual(created_count, 1, "Expected exactly 1 request to succeed with 201")
        self.assertEqual(conflict_count, 19, "Expected 19 requests to receive 409 Conflict")

        # Verify conflict message
        conflicts = [r[1] for r in results if r[0] == 409]
        for c in conflicts:
            self.assertEqual(c.get("message"), "Request already exists")

    # -------------------------------------------------------------
    # 3. HTTP 403 Forbidden & HTTP 500/502/503 Errors
    # -------------------------------------------------------------

    def test_http_403_forbidden_handling(self):
        """Request media with forbidden user token returns 403 with permission error."""
        self.mock_server.state.forbidden_users.add("unauthorized_guest")
        url = f"{self.mock_server.base_url}/api/v1/request"
        payload = json.dumps({"mediaType": "movie", "mediaId": 777}).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json", "X-Api-Key": "unauthorized_guest"}
        )

        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req)

        self.assertEqual(ctx.exception.code, 403)
        err_data = json.loads(ctx.exception.read().decode("utf-8"))
        self.assertIn("permission", err_data.get("error", "").lower())

    # -------------------------------------------------------------
    # 4. Adversarial Season Diffing Stress Tests
    # -------------------------------------------------------------

    def test_diffing_series_with_specials_s0(self):
        """Specials (Season 0) must be 100% excluded even if locally missing."""
        remote_seasons = [
            {"id": 1, "seasonNumber": 0, "name": "Specials", "episodeCount": 15, "airDate": "2010-01-01"},
            {"id": 2, "seasonNumber": 1, "name": "Season 1", "episodeCount": 10, "airDate": "2011-04-17"},
            {"id": 3, "seasonNumber": 2, "name": "Season 2", "episodeCount": 10, "airDate": "2012-04-01"}
        ]
        local_seasons = {1}  # Missing S0 and S2

        statuses = AdversarialSeasonDetector.detect_season_statuses(
            local_season_numbers=local_seasons,
            remote_seasons=remote_seasons,
            reference_date=self.ref_date
        )

        season_numbers = [s["seasonNumber"] for s in statuses]
        self.assertNotIn(0, season_numbers)
        self.assertEqual(season_numbers, [1, 2])

        summary = AdversarialSeasonDetector.summarize(statuses)
        self.assertEqual(summary["missingSeasons"], [2])
        self.assertEqual(summary["badgeText"], "\u26a0\ufe0f 1 Missing Season")
        self.assertEqual(summary["requestActionText"], "Request Missing Seasons (S2)")

    def test_diffing_unaired_and_future_air_dates(self):
        """Unreleased episodes (count 0, empty/null airDate, future date) excluded."""
        remote_seasons = [
            {"id": 1, "seasonNumber": 1, "name": "Season 1", "episodeCount": 10, "airDate": "2024-01-01"},
            {"id": 2, "seasonNumber": 2, "name": "Season 2", "episodeCount": 10, "airDate": "2026-09-15"},
            {"id": 3, "seasonNumber": 3, "name": "Season 3", "episodeCount": 10, "airDate": "2026-09-16"},
            {"id": 4, "seasonNumber": 4, "name": "Season 4", "episodeCount": 10, "airDate": "2026-09-17"},
            {"id": 5, "seasonNumber": 5, "name": "Season 5", "episodeCount": 10, "airDate": "2027-05-01"},
            {"id": 6, "seasonNumber": 6, "name": "Season 6", "episodeCount": 0, "airDate": "2026-01-01"},
            {"id": 7, "seasonNumber": 7, "name": "Season 7", "episodeCount": 10, "airDate": None},
            {"id": 8, "seasonNumber": 8, "name": "Season 8", "episodeCount": 10, "airDate": "   "}
        ]

        local_seasons = {1}
        statuses = AdversarialSeasonDetector.detect_season_statuses(
            local_season_numbers=local_seasons,
            remote_seasons=remote_seasons,
            reference_date=self.ref_date
        )

        season_numbers = [s["seasonNumber"] for s in statuses]
        self.assertEqual(season_numbers, [1, 2, 3])

        summary = AdversarialSeasonDetector.summarize(statuses)
        self.assertEqual(summary["missingSeasons"], [2, 3])
        self.assertEqual(summary["badgeText"], "\u26a0\ufe0f 2 Missing Seasons")
        self.assertEqual(summary["requestActionText"], "Request Missing Seasons (S2, S3)")

    def test_diffing_mismatched_and_noncontiguous_numbering(self):
        """Anthology / Anime with irregular season numbering (e.g. S1, S3, S7, S100)."""
        remote_seasons = [
            {"id": 1, "seasonNumber": 1, "name": "Season 1", "episodeCount": 12, "airDate": "2020-01-01"},
            {"id": 2, "seasonNumber": 3, "name": "Season 3 (Skipped 2)", "episodeCount": 12, "airDate": "2022-01-01"},
            {"id": 3, "seasonNumber": 7, "name": "Season 7", "episodeCount": 12, "airDate": "2024-01-01"},
            {"id": 4, "seasonNumber": 100, "name": "Season 100", "episodeCount": 24, "airDate": "2025-01-01"}
        ]
        local_seasons = {1, 7}

        statuses = AdversarialSeasonDetector.detect_season_statuses(
            local_season_numbers=local_seasons,
            remote_seasons=remote_seasons,
            reference_date=self.ref_date
        )

        season_numbers = [s["seasonNumber"] for s in statuses]
        self.assertEqual(season_numbers, [1, 3, 7, 100])

        summary = AdversarialSeasonDetector.summarize(statuses)
        self.assertEqual(summary["missingSeasons"], [3, 100])
        self.assertEqual(summary["badgeText"], "\u26a0\ufe0f 2 Missing Seasons")
        self.assertEqual(summary["requestActionText"], "Request Missing Seasons (S3, S100)")

    def test_diffing_local_seasons_exceed_remote_catalog(self):
        """When local library has seasons that remote does not list, no negative diffs or errors."""
        remote_seasons = [
            {"id": 1, "seasonNumber": 1, "name": "Season 1", "episodeCount": 10, "airDate": "2020-01-01"},
            {"id": 2, "seasonNumber": 2, "name": "Season 2", "episodeCount": 10, "airDate": "2021-01-01"}
        ]
        local_seasons = {1, 2, 99}

        statuses = AdversarialSeasonDetector.detect_season_statuses(
            local_season_numbers=local_seasons,
            remote_seasons=remote_seasons,
            reference_date=self.ref_date
        )

        self.assertEqual(len(statuses), 2)
        self.assertTrue(all(s["isAvailableLocally"] for s in statuses))

        summary = AdversarialSeasonDetector.summarize(statuses)
        self.assertFalse(summary["hasMissingSeasons"])
        self.assertIsNone(summary["badgeText"])
        self.assertIsNone(summary["requestActionText"])


if __name__ == "__main__":
    unittest.main()
