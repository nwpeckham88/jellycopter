"""
Mock Jellyfin, Jellyseerr v2.7.3, and Companion Plugin HTTP Server Harness.
Permits end-to-end network integration testing of all REST and sync protocols.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import threading
import urllib.parse
from typing import Dict, Any, List, Optional


class MockServerState:
    """Holds mutable in-memory state across all mock endpoints."""
    def __init__(self):
        # Jellyfin State
        self.users = {"user_1": {"Id": "user_1", "Name": "Nathan"}}
        self.episodes = {}  # ep_id -> dict
        self.next_up_items = {}  # series_id -> item_id
        self.user_data = {}  # ep_id -> {"Played": False, "PlaybackPositionTicks": 0}
        self.progress_reports = []
        self.stopped_reports = []

        # Jellyseerr State
        self.trending_items = []
        self.popular_movies = []
        self.popular_tv = []
        self.upcoming_movies = []
        self.upcoming_tv = []
        self.tv_details = {}  # tmdb_id -> dict
        self.requests_log = []
        self.existing_requests = set()  # set of (media_type, media_id)
        self.forbidden_users = set()

        # Companion Plugin State
        self.plugin_enabled = True
        self.server_decks = {}  # deck_id -> dict
        self.server_achievements = {}  # achievement_id -> dict


class MockApiHandler(BaseHTTPRequestHandler):
    state: MockServerState = None

    def log_message(self, format, *args):
        # Silence default stderr logging during test runs
        return

    def _send_json(self, status: int, data: Any):
        payload = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        # -------------------------------------------------------------
        # 1. Jellyfin Endpoints
        # -------------------------------------------------------------
        if path == "/System/Info/Public":
            self._send_json(200, {
                "ServerName": "Gold-Mock",
                "Version": "10.11.11",
                "Id": "jellyfin_mock_001"
            })
            return

        if path.startswith("/Users/") and "/Items" in path:
            parent_id = query.get("ParentId", [None])[0]
            items = list(self.state.episodes.values())
            if parent_id:
                items = [it for it in items if it.get("SeriesId") == parent_id or it.get("ParentId") == parent_id]
            self._send_json(200, {
                "Items": items,
                "TotalRecordCount": len(items)
            })
            return

        if path == "/Shows/NextUp":
            next_up_list = []
            for sid, ep_id in self.state.next_up_items.items():
                if ep_id in self.state.episodes:
                    next_up_list.append(self.state.episodes[ep_id])
            self._send_json(200, {"Items": next_up_list, "TotalRecordCount": len(next_up_list)})
            return

        # -------------------------------------------------------------
        # 2. Jellyseerr v2.7.3 Endpoints
        # -------------------------------------------------------------
        if path == "/api/v1/discover/trending":
            self._send_json(200, {
                "page": 1,
                "totalPages": 1,
                "totalResults": len(self.state.trending_items),
                "results": self.state.trending_items
            })
            return

        if path == "/api/v1/discover/movies":
            self._send_json(200, {
                "page": 1,
                "totalPages": 1,
                "totalResults": len(self.state.popular_movies),
                "results": self.state.popular_movies
            })
            return

        if path == "/api/v1/discover/tv":
            self._send_json(200, {
                "page": 1,
                "totalPages": 1,
                "totalResults": len(self.state.popular_tv),
                "results": self.state.popular_tv
            })
            return

        if path == "/api/v1/discover/movies/upcoming":
            self._send_json(200, {
                "page": 1,
                "totalPages": 1,
                "totalResults": len(self.state.upcoming_movies),
                "results": self.state.upcoming_movies
            })
            return

        if path == "/api/v1/discover/tv/upcoming":
            self._send_json(200, {
                "page": 1,
                "totalPages": 1,
                "totalResults": len(self.state.upcoming_tv),
                "results": self.state.upcoming_tv
            })
            return

        if path.startswith("/api/v1/tv/"):
            tmdb_str = path.split("/")[-1]
            try:
                tmdb_id = int(tmdb_str)
                details = self.state.tv_details.get(tmdb_id, {
                    "id": tmdb_id,
                    "name": "Sample Show",
                    "seasons": []
                })
                self._send_json(200, details)
                return
            except ValueError:
                self._send_json(400, {"error": "Invalid TMDB ID"})
                return

        # -------------------------------------------------------------
        # 3. Companion Plugin Endpoints (/Jellycopter/*)
        # -------------------------------------------------------------
        if path.startswith("/Jellycopter"):
            if not self.state.plugin_enabled:
                self._send_json(404, {"error": "Plugin not installed"})
                return

            if path == "/Jellycopter/Ping":
                self._send_json(200, {
                    "version": "1.0.0",
                    "supportedSyncProtocols": [1],
                    "serverTimeUtc": "2026-09-16T04:30:00Z"
                })
                return

            if path == "/Jellycopter/Decks":
                decks_list = list(self.state.server_decks.values())
                self._send_json(200, decks_list)
                return

            if path == "/Jellycopter/Achievements":
                ach_list = list(self.state.server_achievements.values())
                self._send_json(200, ach_list)
                return

        self._send_json(404, {"error": "Endpoint not found", "path": path})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        payload = json.loads(body) if body else {}

        # -------------------------------------------------------------
        # 1. Jellyfin Scrobble / Sessions
        # -------------------------------------------------------------
        if path == "/Sessions/Playing/Progress":
            self.state.progress_reports.append(payload)
            ep_id = payload.get("ItemId")
            pos = payload.get("PositionTicks", 0)
            if ep_id:
                self.state.user_data[ep_id] = {
                    "Played": False,
                    "PlaybackPositionTicks": pos
                }
            self._send_json(204, {})
            return

        if path == "/Sessions/Playing/Stopped":
            self.state.stopped_reports.append(payload)
            ep_id = payload.get("ItemId")
            series_id = payload.get("SeriesId")
            # If standard unsuppressed scrobble:
            if payload.get("Played", False) and ep_id:
                self.state.user_data[ep_id] = {
                    "Played": True,
                    "PlaybackPositionTicks": 0
                }
                # Advance sequential NextUp pointer
                if series_id and series_id in self.state.next_up_items:
                    curr_next = self.state.next_up_items[series_id]
                    if curr_next == ep_id:
                        # Advance to next chronological episode
                        next_id = f"{curr_next}_next"
                        self.state.next_up_items[series_id] = next_id
                        if next_id not in self.state.episodes:
                            self.state.episodes[next_id] = {
                                "Id": next_id,
                                "Name": f"{next_id} Title",
                                "SeriesId": series_id
                            }
            self._send_json(204, {})
            return

        # -------------------------------------------------------------
        # 2. Jellyseerr 1-Click Request
        # -------------------------------------------------------------
        if path == "/api/v1/request":
            user_token = self.headers.get("X-Api-Key")
            if user_token in self.state.forbidden_users:
                self._send_json(403, {"error": "User does not have permission to request media"})
                return

            mtype = payload.get("mediaType")
            mid = payload.get("mediaId")
            key = (mtype, mid)

            if key in self.state.existing_requests:
                self._send_json(409, {
                    "message": "Request already exists",
                    "statusCode": 409
                })
                return

            self.state.existing_requests.add(key)
            self.state.requests_log.append(payload)
            self._send_json(201, {
                "id": len(self.state.requests_log),
                "status": 2,  # PENDING APPROVAL / PROCESSING
                "media": {"id": mid, "mediaType": mtype},
                "seasons": payload.get("seasons", "all")
            })
            return

        # -------------------------------------------------------------
        # 3. Companion Plugin Sync
        # -------------------------------------------------------------
        if path.startswith("/Jellycopter"):
            if not self.state.plugin_enabled:
                self._send_json(404, {"error": "Plugin not installed"})
                return

            if path == "/Jellycopter/Decks":
                deck_id = payload.get("deckId")
                client_cycle = payload.get("cycleNumber", 1)
                client_exhausted = set(payload.get("exhaustedItemIds", []))

                if deck_id in self.state.server_decks:
                    server_deck = self.state.server_decks[deck_id]
                    server_cycle = server_deck.get("cycleNumber", 1)
                    if client_cycle > server_cycle:
                        # Client is ahead
                        self.state.server_decks[deck_id] = payload
                    elif client_cycle == server_cycle:
                        # SET UNION MERGE
                        merged = set(server_deck.get("exhaustedItemIds", [])).union(client_exhausted)
                        server_deck["exhaustedItemIds"] = sorted(list(merged))
                        server_deck["lastPlayedItemId"] = payload.get("lastPlayedItemId", server_deck.get("lastPlayedItemId"))
                        server_deck["lastResumePositionTicks"] = payload.get("lastResumePositionTicks", server_deck.get("lastResumePositionTicks"))
                        self.state.server_decks[deck_id] = server_deck
                else:
                    self.state.server_decks[deck_id] = payload

                self._send_json(200, self.state.server_decks[deck_id])
                return

            if path == "/Jellycopter/Achievements":
                ach_id = payload.get("id")
                self.state.server_achievements[ach_id] = payload
                self._send_json(200, payload)
                return

        self._send_json(404, {"error": "Not Found"})


class MockServerHarness:
    """Manager to spin up and tear down the local mock HTTP server."""

    def __init__(self, host: str = "127.0.0.1", port: int = 0):
        self.state = MockServerState()
        MockApiHandler.state = self.state
        self.server = HTTPServer((host, port), MockApiHandler)
        self.host, self.port = self.server.server_address
        self.base_url = f"http://{self.host}:{self.port}"
        self._thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def start(self):
        self._thread.start()

    def stop(self):
        self.server.shutdown()
        self.server.server_close()
        self._thread.join(timeout=2.0)
