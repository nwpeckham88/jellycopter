"""
Tier 4 Real-World Workload Scenario: Jellyseerr Discovery & 1-Click Request Simulation
Simulates full end-to-end user navigation:
1. Discover Tab: Loading Trending and Popular carousels.
2. Browsing to a series and issuing an instant 1-Click Request with smart defaults.
3. Updating the card UI state from 'Unrequested' to 'Requested' (HTTP 201).
4. Navigating to Library, diffing missing seasons, and issuing partial season request.
5. Handling rapid repeat clicks with HTTP 409 Conflict catching.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness


class TestWorkloadJellyseerrRealApiSim(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()

        self.mock_server.state.trending_items = [
            {"id": 95557, "name": "Severance", "mediaType": "tv", "posterPath": "/sev.jpg"},
            {"id": 693134, "title": "Dune: Part Two", "mediaType": "movie", "posterPath": "/dune.jpg"}
        ]
        self.mock_server.state.popular_tv = [
            {"id": 76331, "name": "Succession", "mediaType": "tv"}
        ]
        self.mock_server.state.tv_details[76331] = {
            "id": 76331,
            "name": "Succession",
            "seasons": [
                {"seasonNumber": 1, "episodeCount": 10},
                {"seasonNumber": 2, "episodeCount": 10},
                {"seasonNumber": 3, "episodeCount": 9},
                {"seasonNumber": 4, "episodeCount": 10}
            ]
        }

    def tearDown(self):
        self.mock_server.stop()

    def test_complete_discovery_and_request_workflow(self):
        """Workload: Execute full user journey from carousel browse to 1-click request and library diff."""
        # 1. Fetch Trending Carousel
        trending_req = urllib.request.Request(f"{self.mock_server.base_url}/api/v1/discover/trending")
        with urllib.request.urlopen(trending_req) as resp:
            trending = json.loads(resp.read().decode("utf-8"))
        self.assertEqual(len(trending["results"]), 2)
        severance_item = trending["results"][0]
        self.assertEqual(severance_item["name"], "Severance")

        # 2. Issue 1-Click Request for Severance (TMDB 95557)
        request_req = urllib.request.Request(
            f"{self.mock_server.base_url}/api/v1/request",
            data=json.dumps({
                "mediaType": "tv",
                "mediaId": severance_item["id"],
                "seasons": "all"
            }).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(request_req) as resp:
            self.assertEqual(resp.status, 201)
            request_result = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(request_result["status"], 2)  # Requested / Pending
        self.assertIn(("tv", 95557), self.mock_server.state.existing_requests)

        # 3. Repeat click returns HTTP 409, handled gracefully without UI crash
        try:
            with urllib.request.urlopen(request_req) as resp:
                self.fail("Should have caught 409 Conflict")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 409)

        # 4. Library view diff for Succession (TMDB 76331): local has 1, 2, 3; missing 4
        local_succession_seasons = {1, 2, 3}
        tv_details = self.mock_server.state.tv_details[76331]
        aired = {s["seasonNumber"] for s in tv_details["seasons"] if s["seasonNumber"] > 0}
        missing = sorted(list(aired - local_succession_seasons))
        self.assertEqual(missing, [4])

        # 5. Direct request for missing Season 4
        missing_req = urllib.request.Request(
            f"{self.mock_server.base_url}/api/v1/request",
            data=json.dumps({
                "mediaType": "tv",
                "mediaId": 76331,
                "seasons": missing
            }).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(missing_req) as resp:
            self.assertEqual(resp.status, 201)
            missing_result = json.loads(resp.read().decode("utf-8"))
        self.assertEqual(missing_result["seasons"], [4])


if __name__ == "__main__":
    unittest.main()
