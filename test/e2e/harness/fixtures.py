"""
Test Catalogs and Data Fixtures for Jellycopter E2E Test Suite.
"""

from typing import List
from harness.shuffle_engine import EpisodeItem


def bobs_burgers_catalog() -> List[EpisodeItem]:
    """Generates 14 seasons with 313 total episodes matching reference dataset."""
    episodes: List[EpisodeItem] = []
    # Season episode distributions typical for Bob's Burgers
    season_counts = [13, 9, 23, 22, 21, 19, 22, 21, 22, 22, 22, 22, 22, 23]  # sum = 283 + 30 = 313
    ep_counter = 1

    for s_idx, count in enumerate(season_counts, start=1):
        season_id = f"season_bb_{s_idx:02d}"
        for e_idx in range(1, count + 1):
            episodes.append(EpisodeItem(
                id=f"bb_s{s_idx:02d}e{e_idx:02d}",
                name=f"Bob's Burgers S{s_idx:02d}E{e_idx:02d} Ep Title",
                series_id="show_bobs_burgers",
                season_id=season_id,
                season_number=s_idx,
                index_number=e_idx,
                play_count=0.0,
                played=False
            ))
            ep_counter += 1
    return episodes


def star_trek_tng_multipart_catalog() -> List[EpisodeItem]:
    """Catalog with multi-part story arcs and standalone episodes."""
    return [
        EpisodeItem("tng_1", "Encounter at Farpoint", "tng", "s1", 1, 1),
        EpisodeItem("tng_2", "The Naked Now", "tng", "s1", 1, 2),
        EpisodeItem("tng_3", "The Best of Both Worlds, Part 1", "tng", "s3", 3, 26),
        EpisodeItem("tng_4", "The Best of Both Worlds, Part 2", "tng", "s4", 4, 1),  # Cross-season!
        EpisodeItem("tng_5", "Family", "tng", "s4", 4, 2),
        EpisodeItem("tng_6", "Redemption, Part 1", "tng", "s4", 4, 26),
        EpisodeItem("tng_7", "Redemption, Part 2", "tng", "s5", 5, 1),
        EpisodeItem("tng_8", "Chain of Command, Part 1", "tng", "s6", 6, 10),
        EpisodeItem("tng_9", "Chain of Command, Part 2", "tng", "s6", 6, 11),  # Same season sequential!
        EpisodeItem("tng_10", "Darmok", "tng", "s5", 5, 2),
        EpisodeItem("tng_11", "The Inner Light", "tng", "s5", 5, 25),
        EpisodeItem("tng_12", "All Good Things..., Part 1", "tng", "s7", 7, 25),
        EpisodeItem("tng_13", "All Good Things..., Part 2", "tng", "s7", 7, 26),  # Same season sequential!
    ]


def uneven_seasons_catalog() -> List[EpisodeItem]:
    """Extreme uneven seasons: Season 1 has 6 eps, Season 2 has 24 eps, Season 3 has 24 eps (N=54)."""
    episodes = []
    # Season 1 (6 eps)
    for i in range(1, 7):
        episodes.append(EpisodeItem(f"uneven_s1_{i}", f"Show S1E{i}", "uneven_show", "s1", 1, i))
    # Season 2 (24 eps)
    for i in range(1, 25):
        episodes.append(EpisodeItem(f"uneven_s2_{i}", f"Show S2E{i}", "uneven_show", "s2", 2, i))
    # Season 3 (24 eps)
    for i in range(1, 25):
        episodes.append(EpisodeItem(f"uneven_s3_{i}", f"Show S3E{i}", "uneven_show", "s3", 3, i))
    return episodes


def pigeonhole_dominant_catalog() -> List[EpisodeItem]:
    """Dominant season: Season 1 has 50 episodes, Season 2 has 5 episodes (N=55, max > ceil(N/2))."""
    episodes = []
    for i in range(1, 51):
        episodes.append(EpisodeItem(f"pigeon_s1_{i}", f"Show S1E{i}", "pigeon_show", "s1", 1, i))
    for i in range(1, 6):
        episodes.append(EpisodeItem(f"pigeon_s2_{i}", f"Show S2E{i}", "pigeon_show", "s2", 2, i))
    return episodes


def generic_titles_catalog() -> List[EpisodeItem]:
    """Catalog with generic titles that should NOT be fused into multi-part arcs."""
    return [
        EpisodeItem("gen_1", "Episode 1", "gen_show", "s1", 1, 1),
        EpisodeItem("gen_2", "Episode 2", "gen_show", "s1", 1, 2),
        EpisodeItem("gen_3", "Episode 3", "gen_show", "s1", 1, 3),
        EpisodeItem("gen_4", "Chapter 1", "gen_show", "s2", 2, 1),
        EpisodeItem("gen_5", "Chapter 2", "gen_show", "s2", 2, 2),
    ]


def roman_numeral_catalog() -> List[EpisodeItem]:
    """Catalog with Roman numerals and keywords."""
    return [
        EpisodeItem("rom_1", "Invasion: Part I", "rom_show", "s1", 1, 1),
        EpisodeItem("rom_2", "Invasion: Part II", "rom_show", "s1", 1, 2),
        EpisodeItem("rom_3", "Invasion: Part III", "rom_show", "s1", 1, 3),
        # Trailing roman numerals without part keyword in different seasons (Simpsons specials)
        EpisodeItem("thoh_5", "Treehouse of Horror V", "simpsons", "s6", 6, 6),
        EpisodeItem("thoh_6", "Treehouse of Horror VI", "simpsons", "s7", 7, 6),
    ]


def single_episode_show_catalog() -> List[EpisodeItem]:
    """Miniseries with a single episode."""
    return [
        EpisodeItem("single_1", "Pilot and Finale", "single_show", "s1", 1, 1)
    ]


def single_season_show_catalog() -> List[EpisodeItem]:
    """Single season show (10 episodes)."""
    return [
        EpisodeItem(f"s1_{i}", f"Single Season Ep {i}", "single_season_show", "s1", 1, i)
        for i in range(1, 11)
    ]
