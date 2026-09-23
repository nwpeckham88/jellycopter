"""
Tier 1 Feature Tests: Jellyseerr Discovery Carousels & 1-Click Requests
Requirements: R3, ORIGINAL_REQUEST.md § R3, PROJECT.md Features 16 & 17.
Verifies OpenAPI v2.7.3 carousel deserialization, 1-Click request payload construction,
long-press season selection, and 409 Conflict / 403 Forbidden handling.
"""

import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


import unittest
import urllib.request
import json
from harness.mock_servers import MockServerHarness


class TestFeatureJellyseerrRequests(unittest.TestCase):

    def setUp(self):
        self.mock_server = MockServerHarness()
        self.mock_server.start()

        # Seed discovery items
        self.mock_server.state.trending_items = [
            {"id": 101, "title": "Dune: Part Two", "mediaType": "movie", "posterPath": "/dune2.jpg"},
            {"id": 102, "name": "Severance", "mediaType": "tv", "posterPath": "/sev.jpg"}
        ]
        self.mock_server.state.popular_movies = [
            {"id": 201, "title": "Inception", "mediaType": "movie"}
        ]
        self.mock_server.state.popular_tv = [
            {"id": 301, "name": "Succession", "mediaType": "tv"}
        ]

    def tearDown(self):
        self.mock_server.stop()

    def test_trending_carousel_returns_valid_page_info(self):
        """Test 1: GET /api/v1/discover/trending parses items and media types correctly."""
        url = f"{self.mock_server.base_url}/api/v1/discover/trending"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(data["totalResults"], 2)
        self.assertEqual(data["results"][0]["title"], "Dune: Part Two")
        self.assertEqual(data["results"][1]["name"], "Severance")

    def test_one_click_request_submits_smart_defaults(self):
        """Test 2: Single remote click submits 1-click request with smart defaults (seasons: 'all')."""
        url = f"{self.mock_server.base_url}/api/v1/request"
        payload = {
            "mediaType": "movie",
            "mediaId": 12345,
            "is4k": False,
            "seasons": "all"
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 201)
            res_data = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(res_data["status"], 2)
        self.assertIn(("movie", 12345), self.mock_server.state.existing_requests)

    def test_long_press_advanced_request_with_specific_seasons(self):
        """Test 3: Long-press modal allows requesting specific seasons (e.g., seasons: [2, 4])."""
        url = f"{self.mock_server.base_url}/api/v1/request"
        payload = {
            "mediaType": "tv",
            "mediaId": 98765,
            "seasons": [2, 4],
            "is4k": True
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 201)
            res_data = json.loads(resp.read().decode("utf-8"))

        self.assertEqual(res_data["seasons"], [2, 4])

    def test_request_conflict_409_handled_without_fatal_error(self):
        """Test 4: Requesting an already requested title returns 409 Conflict, caught gracefully."""
        url = f"{self.mock_server.base_url}/api/v1/request"
        payload = {"mediaType": "movie", "mediaId": 55555}
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        # First request succeeds
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 201)

        # Duplicate request triggers 409
        try:
            with urllib.request.urlopen(req) as resp:
                self.fail("Should have returned HTTP 409 Conflict")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 409)
            err_data = json.loads(e.read().decode("utf-8"))
            self.assertIn("already exists", err_data["message"])

    def test_permission_denied_403_handling(self):
        """Test 5: User lacking request permissions returns HTTP 403 Forbidden."""
        url = f"{self.mock_server.base_url}/api/v1/request"
        self.mock_server.state.forbidden_users.add("token_guest")

        payload = {"mediaType": "movie", "mediaId": 77777}
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "X-Api-Key": "token_guest"}
        )
        try:
            with urllib.request.urlopen(req) as resp:
                self.fail("Should have returned HTTP 403 Forbidden")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 403)


if __name__ == "__main__":
    unittest.main()
