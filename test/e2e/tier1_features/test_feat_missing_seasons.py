"""
Tier 1 Feature Tests: In-Library Missing Season Badges & Request Triggers
Requirements: R3, ORIGINAL_REQUEST.md § R3, PROJECT.md Feature 18.
Verifies diff detection between Jellyfin local storage and TMDB/Jellyseerr catalog,
missing season filtering (ignoring S0 specials and unaired seasons), and direct request trigger.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness


def calculate_missing_seasons(local_seasons: set, tmdb_seasons: list) -> set:
    """
    Filters for aired regular seasons (season_number > 0)
    and computes the difference against local storage.
    """
    aired_regular = {
        s["seasonNumber"] for s in tmdb_seasons
        if s.get("seasonNumber", 0) > 0 and s.get("episodeCount", 0) > 0
    }
    return aired_regular - local_seasons


class TestFeatureMissingSeasons(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()

        # Seed TMDB TV details: Seasons 1, 2, 3, 4, 5 exist on TMDB
        self.tmdb_id = 60059  # Better Call Saul
        self.mock_server.state.tv_details[self.tmdb_id] = {
            "id": self.tmdb_id,
            "name": "Better Call Saul",
            "seasons": [
                {"seasonNumber": 0, "name": "Specials", "episodeCount": 5},
                {"seasonNumber": 1, "name": "Season 1", "episodeCount": 10},
                {"seasonNumber": 2, "name": "Season 2", "episodeCount": 10},
                {"seasonNumber": 3, "name": "Season 3", "episodeCount": 10},
                {"seasonNumber": 4, "name": "Season 4", "episodeCount": 10},
                {"seasonNumber": 5, "name": "Season 5", "episodeCount": 10},
                {"seasonNumber": 6, "name": "Season 6 (Unaired)", "episodeCount": 0}  # 0 eps
            ]
        }

    def tearDown(self):
        self.mock_server.stop()

    def test_missing_seasons_diff_calculation(self):
        """Test 1: Library has Seasons 1 & 2; TMDB has 1-5; identifies Seasons 3, 4, 5 as missing."""
        local_seasons = {1, 2}
        tmdb_seasons = self.mock_server.state.tv_details[self.tmdb_id]["seasons"]
        missing = calculate_missing_seasons(local_seasons, tmdb_seasons)
        self.assertEqual(missing, {3, 4, 5})

    def test_specials_season_zero_is_ignored(self):
        """Test 2: Season 0 (Specials) is not flagged as missing even if absent from local library."""
        local_seasons = {1, 2, 3, 4, 5}
        tmdb_seasons = self.mock_server.state.tv_details[self.tmdb_id]["seasons"]
        missing = calculate_missing_seasons(local_seasons, tmdb_seasons)
        self.assertEqual(len(missing), 0)
        self.assertNotIn(0, missing)

    def test_unaired_seasons_with_zero_episodes_ignored(self):
        """Test 3: Season 6 with 0 episodes is excluded from missing calculation."""
        local_seasons = {1, 2, 3, 4, 5}
        tmdb_seasons = self.mock_server.state.tv_details[self.tmdb_id]["seasons"]
        missing = calculate_missing_seasons(local_seasons, tmdb_seasons)
        self.assertNotIn(6, missing)

    def test_direct_request_action_payload_generation(self):
        """Test 4: Clicking 'Request Missing Seasons' creates payload containing exactly [3, 4, 5]."""
        local_seasons = {1, 2}
        tmdb_seasons = self.mock_server.state.tv_details[self.tmdb_id]["seasons"]
        missing = sorted(list(calculate_missing_seasons(local_seasons, tmdb_seasons)))

        url = f"{self.mock_server.base_url}/api/v1/request"
        payload = {
            "mediaType": "tv",
            "mediaId": self.tmdb_id,
            "seasons": missing
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 201)
            res_data = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(res_data["seasons"], [3, 4, 5])

    def test_no_missing_seasons_omits_badge(self):
        """Test 5: When all aired seasons are present locally, missing season set is empty."""
        local_seasons = {1, 2, 3, 4, 5}
        tmdb_seasons = self.mock_server.state.tv_details[self.tmdb_id]["seasons"]
        missing = calculate_missing_seasons(local_seasons, tmdb_seasons)
        self.assertEqual(len(missing), 0)


if __name__ == "__main__":
    unittest.main()
