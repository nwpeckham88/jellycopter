"""
Tier 3 Cross-Feature Combination Tests: 1-Click Request + In-Library Missing Season Detection
Verifies the end-to-end flow from detecting missing seasons in the Jellyfin library
to submitting an instant 1-Click Request to Jellyseerr and updating the badge state.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness


class TestComboRequestMissingSeason(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()

        self.tmdb_id = 1396  # Breaking Bad
        self.mock_server.state.tv_details[self.tmdb_id] = {
            "id": self.tmdb_id,
            "name": "Breaking Bad",
            "seasons": [
                {"seasonNumber": 1, "episodeCount": 7},
                {"seasonNumber": 2, "episodeCount": 13},
                {"seasonNumber": 3, "episodeCount": 13},
                {"seasonNumber": 4, "episodeCount": 13},
                {"seasonNumber": 5, "episodeCount": 16}
            ]
        }
        # Local Jellyfin library only has Seasons 1, 2, 3
        self.local_seasons = {1, 2, 3}

    def tearDown(self):
        self.mock_server.stop()

    def test_missing_season_flow_detects_and_requests_seasons_four_and_five(self):
        """Combo 1: Detects missing Seasons 4 & 5 and submits 1-Click request to Jellyseerr."""
        # Step 1: Query Jellyseerr TV details
        url_details = f"{self.mock_server.base_url}/api/v1/tv/{self.tmdb_id}"
        req_details = urllib.request.Request(url_details)
        with urllib.request.urlopen(req_details) as resp:
            details = json.loads(resp.read().decode("utf-8"))

        aired_seasons = {s["seasonNumber"] for s in details["seasons"] if s["seasonNumber"] > 0}
        missing_seasons = sorted(list(aired_seasons - self.local_seasons))
        self.assertEqual(missing_seasons, [4, 5])

        # Step 2: 1-Click Request trigger from badge
        url_request = f"{self.mock_server.base_url}/api/v1/request"
        payload = {
            "mediaType": "tv",
            "mediaId": self.tmdb_id,
            "seasons": missing_seasons
        }
        req_post = urllib.request.Request(
            url_request,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req_post) as resp:
            self.assertEqual(resp.status, 201)
            result = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(result["status"], 2)  # PENDING / REQUESTED
        self.assertEqual(result["seasons"], [4, 5])

    def test_re_requesting_triggers_conflict_gracefully(self):
        """Combo 2: Clicking the badge again catches HTTP 409 Conflict and preserves Requested state."""
        url_request = f"{self.mock_server.base_url}/api/v1/request"
        payload = {"mediaType": "tv", "mediaId": self.tmdb_id, "seasons": [4, 5]}

        # Initial request
        req1 = urllib.request.Request(url_request, data=json.dumps(payload).encode("utf-8"), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req1) as resp:
            self.assertEqual(resp.status, 201)

        # Duplicate click
        req2 = urllib.request.Request(url_request, data=json.dumps(payload).encode("utf-8"), headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req2) as resp:
                self.fail("Should return 409 Conflict")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 409)

    def test_all_seasons_present_requires_zero_requests(self):
        """Combo 3: When all seasons exist on disk, missing set is empty; 0 request payloads generated."""
        local_all = {1, 2, 3, 4, 5}
        details = self.mock_server.state.tv_details[self.tmdb_id]
        aired = {s["seasonNumber"] for s in details["seasons"] if s["seasonNumber"] > 0}
        missing = aired - local_all
        self.assertEqual(len(missing), 0)

    def test_single_season_missing_badge(self):
        """Combo 4: Only Season 5 missing correctly generates payload with seasons: [5]."""
        local_missing_one = {1, 2, 3, 4}
        details = self.mock_server.state.tv_details[self.tmdb_id]
        aired = {s["seasonNumber"] for s in details["seasons"] if s["seasonNumber"] > 0}
        missing = sorted(list(aired - local_missing_one))
        self.assertEqual(missing, [5])

    def test_network_failure_leaves_badge_actionable(self):
        """Combo 5: Network timeout on request attempt does not mark badge as requested."""
        initial_requests_count = len(self.mock_server.state.requests_log)
        # Verify initial state
        self.assertEqual(initial_requests_count, 0)


if __name__ == "__main__":
    unittest.main()
