"""
Jellycopter Client State Machine & Room Database Simulator Harness
Covers Room DB schema, watch history decoupling, 10-ft focus engine,
and achievement telemetry evaluation.
"""

import sqlite3
import time
from typing import Dict, List, Optional, Set, Any
from dataclasses import dataclass


@dataclass
class UnlockedAchievement:
    id: str
    title: str
    tier: str
    points: int
    unlocked_at: int


class ClientRoomDatabase:
    """Simulates Android Room DB (jellycopter.db) using SQLite."""

    def __init__(self, in_memory: bool = True):
        self.conn = sqlite3.connect(":memory:" if in_memory else "test_jellycopter.db")
        self.conn.row_factory = sqlite3.Row
        self._create_tables()

    def _create_tables(self):
        cur = self.conn.cursor()
        # Deck entity table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS deck_entities (
                deck_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                series_id TEXT,
                channel_id TEXT,
                current_index INTEGER NOT NULL,
                cycle_number INTEGER NOT NULL,
                strict_queue_mode INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)
        # Deck playback progress table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS deck_playback_progress (
                deck_id TEXT PRIMARY KEY,
                current_card_id TEXT NOT NULL,
                current_episode_id TEXT NOT NULL,
                current_index INTEGER NOT NULL,
                midway_resume_ms INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                last_watched_timestamp INTEGER NOT NULL
            )
        """)
        # Telemetry events ledger
        cur.execute("""
            CREATE TABLE IF NOT EXISTS telemetry_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_type TEXT NOT NULL,
                item_id TEXT,
                duration_ms INTEGER,
                timestamp INTEGER NOT NULL
            )
        """)
        # Achievements table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS achievements (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                unlocked INTEGER NOT NULL,
                unlocked_at INTEGER,
                progress_value REAL NOT NULL
            )
        """)
        self.conn.commit()

    def save_deck_progress(self, deck_id: str, card_id: str, episode_id: str, index: int, resume_ms: int, duration_ms: int):
        cur = self.conn.cursor()
        cur.execute("""
            INSERT OR REPLACE INTO deck_playback_progress
            (deck_id, current_card_id, current_episode_id, current_index, midway_resume_ms, duration_ms, last_watched_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (deck_id, card_id, episode_id, index, resume_ms, duration_ms, int(time.time() * 1000)))
        self.conn.commit()

    def get_deck_progress(self, deck_id: str) -> Optional[Dict[str, Any]]:
        cur = self.conn.cursor()
        cur.execute("SELECT * FROM deck_playback_progress WHERE deck_id = ?", (deck_id,))
        row = cur.fetchone()
        return dict(row) if row else None

    def record_telemetry_event(self, event_type: str, item_id: str, duration_ms: int, ts: Optional[int] = None):
        cur = self.conn.cursor()
        cur.execute("""
            INSERT INTO telemetry_events (event_type, item_id, duration_ms, timestamp)
            VALUES (?, ?, ?, ?)
        """, (event_type, item_id, duration_ms, ts or int(time.time() * 1000)))
        self.conn.commit()

    def close(self):
        self.conn.close()


class AchievementEngine:
    """
    Evaluates client telemetry against milestone definitions.
    Enforces the Sacred Playback Rule: 0 toasts/interruptions during active playback.
    """

    def __init__(self, db: ClientRoomDatabase):
        self.db = db
        self.unlocked: Dict[str, UnlockedAchievement] = {}
        self.pending_end_screen_celebrations: List[UnlockedAchievement] = []
        self.active_playback_toast_count = 0

    def evaluate_playback_session(
        self,
        episodes_completed: int,
        total_watch_time_ms: int,
        is_shuffle_play: bool,
        deck_cycle_completed: bool,
        current_hour: int
    ) -> List[UnlockedAchievement]:
        newly_unlocked = []

        # 1. "Trip to the Moon" (72 hours = 259,200,000 ms)
        if total_watch_time_ms >= 259200000 and "trip_to_the_moon" not in self.unlocked:
            ach = UnlockedAchievement("trip_to_the_moon", "Trip to the Moon", "Platinum", 100, int(time.time()))
            self.unlocked["trip_to_the_moon"] = ach
            newly_unlocked.append(ach)

        # 2. "Binge Voyager" (8+ episodes in session)
        if episodes_completed >= 8 and "binge_voyager" not in self.unlocked:
            ach = UnlockedAchievement("binge_voyager", "Binge Voyager", "Gold", 50, int(time.time()))
            self.unlocked["binge_voyager"] = ach
            newly_unlocked.append(ach)

        # 3. "Shuffle Centurion" (100 shuffle deck episode playthroughs)
        if is_shuffle_play and episodes_completed >= 100 and "shuffle_centurion" not in self.unlocked:
            ach = UnlockedAchievement("shuffle_centurion", "Shuffle Centurion", "Gold", 50, int(time.time()))
            self.unlocked["shuffle_centurion"] = ach
            newly_unlocked.append(ach)

        # 4. "Night Owl" (completed between 03:00 and 05:00)
        if 3 <= current_hour < 5 and "night_owl" not in self.unlocked:
            ach = UnlockedAchievement("night_owl", "Night Owl", "Silver", 25, int(time.time()))
            self.unlocked["night_owl"] = ach
            newly_unlocked.append(ach)

        # 5. "The Compleatist" (100% deck cycle exhaustion)
        if deck_cycle_completed and "the_compleatist" not in self.unlocked:
            ach = UnlockedAchievement("the_compleatist", "The Compleatist", "Gold", 50, int(time.time()))
            self.unlocked["the_compleatist"] = ach
            newly_unlocked.append(ach)

        # Queue for post-playback screen (Sacred Playback Rule: 0 toasts during video)
        self.pending_end_screen_celebrations.extend(newly_unlocked)
        return newly_unlocked

    def show_playback_toast_if_any(self):
        """Simulates an illegal mid-playback popup."""
        # By contract, this MUST NEVER be called during active playback!
        self.active_playback_toast_count += 1


class ComposeTvFocusEngine:
    """
    Simulates Jetpack Compose for TV 10-foot D-pad focus engine:
    - 1.08x focus scaling elevation
    - 0.08f pivot offset (8% inset from screen edge)
    - Focus restoration on back navigation
    - Dynamic adaptive palette extraction from 120x180 thumbnail
    """

    def __init__(self):
        self.focus_scale = 1.08
        self.pivot_fraction = 0.08
        self.focus_stack: List[int] = []  # Stack of focused card indices
        self.active_index = 0
        self.current_ambient_glow_hex = "#121528"

    def navigate_to_card(self, index: int):
        self.active_index = index

    def enter_child_screen(self):
        """Pushes current focus state onto restoration stack."""
        self.focus_stack.append(self.active_index)

    def press_back_key(self) -> int:
        """Restores exact previously focused card index."""
        if self.focus_stack:
            self.active_index = self.focus_stack.pop()
        return self.active_index

    def extract_palette_from_poster(self, thumbnail_width: int, thumbnail_height: int, poster_url: str) -> str:
        """
        Simulates downsampled Coil request (120x180 px) and Palette extraction.
        Returns extracted dominant hex color in < 15ms.
        """
        # Ensure dimensions match downsampling specification
        assert thumbnail_width <= 120 and thumbnail_height <= 180
        # Deterministic hex tint based on poster name
        h = sum(ord(c) for c in poster_url)
        colors = ["#AA5CC3", "#00F0FF", "#FF5E36", "#00A4DC", "#FFAA00"]
        self.current_ambient_glow_hex = colors[h % len(colors)]
        return self.current_ambient_glow_hex
