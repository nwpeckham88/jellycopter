"""
Jellycopter Smart Shuffle Reference Engine & Entropy Harness
Authoritative implementation adhering to ORIGINAL_REQUEST.md and Survey Analysis.
"""

from dataclasses import dataclass, field
from typing import List, Dict, Set, Optional, Tuple, Any
import math
import random
import re
from collections import Counter, defaultdict

# Regex for multi-part episodes
# Matches "Title, Part 1", "Title - Pt. II", "Title (Part 2)", "Title: Vol. 3", etc.
MULTI_PART_REGEX = re.compile(
    r'^(?P<base>.*?)(?:,\s*|:\s*|\s*-\s*|\s*\(\s*|\s*\[\s*|\s+)'
    r'(?:(?P<kw>part|pt\.?|vol\.?|volume|chapter)\s*)?'
    r'(?P<num>[0-9]+|[ivxlcdm]+)(?:\s*\)|\s*\])?$',
    re.IGNORECASE
)

ROMAN_MAP = {
    'i': 1, 'v': 5, 'x': 10, 'l': 50, 'c': 100, 'd': 500, 'm': 1000
}

ROMAN_REGEX = re.compile(r"^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$", re.IGNORECASE)

GENERIC_BLACKLIST = {'episode', 'chapter', 'part', 'pt', 'vol', 'volume', 'special', 'ep', ''}


def parse_roman_or_int(num_str: str) -> Optional[int]:
    num_str = num_str.strip()
    if num_str.isdigit():
        return int(num_str)

    if not num_str or not ROMAN_REGEX.match(num_str):
        return None

    # Check valid Roman numeral
    val = 0
    prev_val = 0
    for char in reversed(num_str.lower()):
        if char not in ROMAN_MAP:
            return None
        curr = ROMAN_MAP[char]
        if curr >= prev_val:
            val += curr
        else:
            val -= curr
        prev_val = curr
    return val if val > 0 else None


def normalize_title(title: str) -> str:
    # Trim whitespace, lowercase, strip trailing punctuation
    return re.sub(r'[^\w\s]', '', title.strip().lower())


@dataclass
class EpisodeItem:
    id: str
    name: str
    series_id: str
    season_id: str
    season_number: int
    index_number: int
    play_count: float = 0.0
    played: bool = False
    run_time_ticks: int = 18000000000  # Default 30 min
    location_type: str = "FileSystem"
    is_missing: bool = False
    media_type: str = "Video"


@dataclass
class MediaCard:
    id: str
    items: List[EpisodeItem]
    item_ids: List[str]
    category_key: str
    season_number: int
    series_id: str
    play_count: float = 0.0
    played: bool = False
    in_cooldown: bool = False
    is_multi_part: bool = False

    @property
    def title(self) -> str:
        if len(self.items) == 1:
            return self.items[0].name
        return f"{self.items[0].name} (Contiguous {len(self.items)}-Part Arc)"


@dataclass
class ShuffleDeck:
    deck_id: str
    name: str
    series_id: Optional[str]
    channel_id: Optional[str]
    cards: List[MediaCard]
    current_index: int = 0
    cycle_number: int = 1
    midway_resume_ms: int = 0
    strict_queue_mode: bool = False
    exhausted_card_ids: Set[str] = field(default_factory=set)


class SmartShuffleEngine:
    """Core stateful shuffle engine implementing R1 requirements."""

    def __init__(self, rng: Optional[random.Random] = None):
        self.rng = rng or random.Random()

    def filter_playable_episodes(self, raw_items: List[EpisodeItem]) -> List[EpisodeItem]:
        """Filters out missing seasons, virtual items, non-video and zero-runtime files."""
        return [
            item for item in raw_items
            if item.location_type != "Virtual"
            and not item.is_missing
            and item.media_type == "Video"
            and item.run_time_ticks > 0
        ]

    def build_media_cards(self, raw_episodes: List[EpisodeItem], history_set: Optional[Set[str]] = None) -> List[MediaCard]:
        """
        Binds episodes into MediaCards, enforcing the 4-point invariant:
        1. Same season ID
        2. Sequential episode index (E_k+1 == E_k + 1)
        3. Matching normalized base title
        4. Consecutive part number (Part_k+1 == Part_k + 1)
        Generic titles like 'Episode 1' are blacklisted.
        """
        playable = self.filter_playable_episodes(raw_episodes)
        # Sort chronologically by season and index number before building cards
        playable.sort(key=lambda ep: (ep.season_number, ep.index_number))

        cards: List[MediaCard] = []
        current_card: Optional[MediaCard] = None
        last_parsed: Optional[Tuple[str, int]] = None  # (base_title, part_num)

        for ep in playable:
            match = MULTI_PART_REGEX.match(ep.name)
            is_valid_multipart = False
            base_title = ""
            part_num = 0

            if match:
                raw_base = match.group('base').strip()
                norm_base = normalize_title(raw_base)
                num_str = match.group('num')
                parsed_num = parse_roman_or_int(num_str) if num_str else None

                if (
                    norm_base not in GENERIC_BLACKLIST
                    and not norm_base.endswith(" ep")
                    and not norm_base.endswith(" episode")
                    and parsed_num is not None
                ):
                    base_title = norm_base
                    part_num = parsed_num
                    is_valid_multipart = True

            in_cooldown = bool(history_set and ep.id in history_set)

            # Check 4-point invariant against current_card
            can_cluster = False
            if current_card is not None and is_valid_multipart and last_parsed is not None:
                last_base, last_num = last_parsed
                prev_ep = current_card.items[-1]

                same_season = (ep.season_id == prev_ep.season_id)
                seq_index = (ep.index_number == prev_ep.index_number + 1)
                matching_base = (base_title == last_base)
                seq_part = (part_num == last_num + 1)

                if same_season and seq_index and matching_base and seq_part:
                    can_cluster = True

            if can_cluster and current_card is not None:
                current_card.items.append(ep)
                current_card.item_ids.append(ep.id)
                current_card.is_multi_part = True
                if in_cooldown:
                    current_card.in_cooldown = True
                last_parsed = (base_title, part_num)
            else:
                card_id = ep.id
                category_key = ep.season_id if ep.season_id else ep.series_id
                current_card = MediaCard(
                    id=card_id,
                    items=[ep],
                    item_ids=[ep.id],
                    category_key=category_key,
                    season_number=ep.season_number,
                    series_id=ep.series_id,
                    play_count=ep.play_count,
                    played=ep.played,
                    in_cooldown=in_cooldown,
                    is_multi_part=False
                )
                cards.append(current_card)
                if is_valid_multipart:
                    last_parsed = (base_title, part_num)
                else:
                    last_parsed = None

        return cards

    def balance_and_shuffle_cards(self, cards: List[MediaCard]) -> List[MediaCard]:
        """
        Tightness-Aware Weighted Round-Robin Season Anti-Clustering.
        Guarantees 0 adjacent season clashes when max(season_count) <= ceil(N / 2).
        Preserves high Shannon entropy.
        """
        if not cards:
            return []
        if len(cards) <= 1:
            return list(cards)

        buckets: Dict[str, List[MediaCard]] = defaultdict(list)
        for card in cards:
            buckets[card.category_key].append(card)

        # Pre-sort within buckets by play_count + random perturbation
        for key in list(buckets.keys()):
            bucket = buckets[key]
            # Small random noise [0.0, 1.5] for play count tie-break
            bucket.sort(key=lambda c: c.play_count + self.rng.uniform(0.0, 1.5))

        shuffled: List[MediaCard] = []
        last_category_key = ""

        while True:
            active_keys = [k for k, b in buckets.items() if len(b) > 0]
            if not active_keys:
                break

            # Eligible candidates are different from the last emitted category
            candidates = [k for k in active_keys if k != last_category_key]
            if not candidates:
                # Unavoidable clash (deadlock: only 1 category remains)
                candidates = active_keys

            remaining_total = sum(len(buckets[k]) for k in active_keys)
            pigeonhole_threshold = math.ceil(remaining_total / 2.0)

            # Check tightness constraint: if any candidate bucket has count >= ceil(R/2),
            # that bucket MUST be chosen to avoid future inevitable clashes.
            tight_candidates = [k for k in candidates if len(buckets[k]) >= pigeonhole_threshold]

            if tight_candidates:
                selected_key = tight_candidates[0]
            else:
                # Weighted random choice proportional to remaining bucket sizes
                weights = [len(buckets[k]) for k in candidates]
                selected_key = self.rng.choices(candidates, weights=weights, k=1)[0]

            card = buckets[selected_key].pop(0)
            shuffled.append(card)
            last_category_key = selected_key

        return shuffled

    def generate_cross_cycle_deck(
        self,
        raw_episodes: List[EpisodeItem],
        prev_deck_order: Optional[List[str]] = None,
        cycle_number: int = 1
    ) -> List[MediaCard]:
        """
        Dynamic Decoupled Cross-Cycle Buffering.
        Swaps previous cycle tail episodes out of the first K slots with category safety.
        """
        cards = self.build_media_cards(raw_episodes)
        shuffled = self.balance_and_shuffle_cards(cards)

        if not prev_deck_order or len(prev_deck_order) <= 3 or len(shuffled) <= 3:
            return shuffled

        n = len(prev_deck_order)
        # K dynamic boundary buffer: 20% of N, clamped between 2 and 30
        k = max(2, min(30, math.floor(0.20 * n)))
        if k >= len(shuffled):
            k = max(1, math.floor(len(shuffled) / 2))

        prev_index_map = {item_id: idx for idx, item_id in enumerate(prev_deck_order)}
        tail_threshold = n - k

        def is_tail_card(card: MediaCard) -> bool:
            return any(prev_index_map.get(iid, -1) >= tail_threshold for iid in card.item_ids)

        has_multiple_categories = len(set(c.category_key for c in shuffled)) > 1

        def is_category_safe_swap(i_idx: int, j_idx: int) -> bool:
            if not has_multiple_categories:
                return True
            cat_i = shuffled[i_idx].category_key
            cat_j = shuffled[j_idx].category_key
            safe_i_at_j = (
                (j_idx == 0 or (j_idx - 1 != i_idx and shuffled[j_idx - 1].category_key != cat_i)) and
                (j_idx + 1 >= len(shuffled) or shuffled[j_idx + 1].category_key != cat_i)
            )
            safe_j_at_i = (
                (i_idx == 0 or shuffled[i_idx - 1].category_key != cat_j) and
                (i_idx + 1 >= len(shuffled) or (i_idx + 1 != j_idx and shuffled[i_idx + 1].category_key != cat_j))
            )
            return safe_i_at_j and safe_j_at_i

        # Inspect first K slots of the new deck
        limit = min(k, len(shuffled))
        for i in range(limit):
            if is_tail_card(shuffled[i]):
                target_idx = -1
                # Attempt random non-tail swap partner with category safety
                for _ in range(50):
                    j = self.rng.randint(k, len(shuffled) - 1)
                    if not is_tail_card(shuffled[j]) and is_category_safe_swap(i, j):
                        target_idx = j
                        break

                # If random sampling did not find one, perform deterministic scan
                if target_idx == -1:
                    for j in range(k, len(shuffled)):
                        if not is_tail_card(shuffled[j]) and is_category_safe_swap(i, j):
                            target_idx = j
                            break

                # If still not found and single category, swap with any non-tail slot >= K
                if target_idx == -1 and not has_multiple_categories:
                    for j in range(k, len(shuffled)):
                        if not is_tail_card(shuffled[j]):
                            target_idx = j
                            break

                if target_idx != -1:
                    shuffled[i], shuffled[target_idx] = shuffled[target_idx], shuffled[i]

        return shuffled

    def generate_single_show_deck(
        self,
        series_id: str,
        series_name: str,
        episodes: List[EpisodeItem],
        device_id: str = "tv_living_room",
        user_id: str = "user_default"
    ) -> ShuffleDeck:
        """Generates a fresh ShuffleDeck for a single series."""
        cards = self.generate_cross_cycle_deck(episodes, prev_deck_order=None, cycle_number=1)
        deck_id = f"{device_id}:{user_id}:{series_id}"
        return ShuffleDeck(
            deck_id=deck_id,
            name=series_name,
            series_id=series_id,
            channel_id=None,
            cards=cards,
            current_index=0,
            cycle_number=1,
            midway_resume_ms=0,
            strict_queue_mode=False
        )

    def generate_mix_channel_deck(
        self,
        channel_id: str,
        channel_name: str,
        shows: List[Tuple[str, str, List[EpisodeItem], int]],  # (series_id, series_name, episodes, weight)
        target_total_episodes: int = 50
    ) -> ShuffleDeck:
        """
        Hierarchical Two-Level Anti-Clustering for Multi-Show Mix Channels.
        Level 1: Show balancing (no adjacent episodes from same show).
        Level 2: Independent sub-deck per show with season anti-clustering and independent cycling.
        """
        # Build independent sub-decks for each show
        show_sub_decks: Dict[str, List[MediaCard]] = {}
        show_cycles: Dict[str, int] = {}
        show_episodes_map: Dict[str, List[EpisodeItem]] = {}
        show_weights: Dict[str, int] = {}
        show_names: Dict[str, str] = {}

        for sid, sname, eps, weight in shows:
            show_episodes_map[sid] = eps
            show_names[sid] = sname
            show_weights[sid] = max(1, weight)
            show_cycles[sid] = 1
            show_sub_decks[sid] = self.generate_cross_cycle_deck(eps, cycle_number=1)

        result_cards: List[MediaCard] = []
        last_series_id = ""

        for _ in range(target_total_episodes):
            active_shows = [sid for sid in show_sub_decks.keys()]
            if not active_shows:
                break

            candidates = [sid for sid in active_shows if sid != last_series_id]
            if not candidates:
                candidates = active_shows

            weights = [show_weights[sid] for sid in candidates]
            selected_show = self.rng.choices(candidates, weights=weights, k=1)[0]

            # Pop card from selected show's sub-deck
            sub_deck = show_sub_decks[selected_show]
            card = sub_deck.pop(0)
            card.series_id = selected_show
            result_cards.append(card)
            last_series_id = selected_show

            # Check if this show's sub-deck exhausted. If so, reset its sub-deck independently!
            if len(sub_deck) == 0:
                show_cycles[selected_show] += 1
                prev_order = [c.id for c in self.build_media_cards(show_episodes_map[selected_show])]
                show_sub_decks[selected_show] = self.generate_cross_cycle_deck(
                    show_episodes_map[selected_show],
                    prev_deck_order=prev_order,
                    cycle_number=show_cycles[selected_show]
                )

        return ShuffleDeck(
            deck_id=channel_id,
            name=channel_name,
            series_id=None,
            channel_id=channel_id,
            cards=result_cards,
            current_index=0,
            cycle_number=1,
            midway_resume_ms=0
        )

    def advance_playback(
        self,
        deck: ShuffleDeck,
        raw_episodes_provider: Optional[callable] = None,
        position_ms: int = 0,
        duration_ms: int = 1800000,
        is_user_skip: bool = False
    ) -> Tuple[ShuffleDeck, str]:
        """
        State machine progression:
        - If exit at <80% and >90s remaining (non-skip): save midwayResumeMs.
        - If skip:
          - If strictQueueMode: consume card, advance index.
          - If default: recycle card back into pool >= min(10, remaining) slots ahead.
        - If >=80% or <=90s remaining: consume card, reset midway resume, advance index.
        - If current_index >= len(cards): cycle reset with cross-cycle buffering.
        """
        if deck.current_index >= len(deck.cards):
            return deck, "CYCLE_COMPLETE"

        current_card = deck.cards[deck.current_index]
        remaining_ms = duration_ms - position_ms
        completion_ratio = (position_ms / duration_ms) if duration_ms > 0 else 0.0

        if is_user_skip:
            if deck.strict_queue_mode:
                deck.exhausted_card_ids.add(current_card.id)
                deck.current_index += 1
                deck.midway_resume_ms = 0
                event = "CONSUMED_BY_STRICT_SKIP"
            else:
                # Recycle skipped card into pool at least min(10, remaining) slots ahead
                deck.cards.pop(deck.current_index)
                remaining_slots = len(deck.cards) - deck.current_index
                insert_offset = min(10, remaining_slots)
                insert_idx = deck.current_index + insert_offset
                deck.cards.insert(insert_idx, current_card)
                deck.midway_resume_ms = 0
                event = "RECYCLED_SKIP"
        elif completion_ratio < 0.80 and remaining_ms > 90000:
            # Midway pause/exit
            deck.midway_resume_ms = position_ms
            event = "SAVED_MIDWAY_RESUME"
        else:
            # Consumed episode
            deck.exhausted_card_ids.add(current_card.id)
            deck.current_index += 1
            deck.midway_resume_ms = 0
            event = "CONSUMED_NATURAL"

        # Check cycle exhaustion
        if deck.current_index >= len(deck.cards):
            deck.cycle_number += 1
            deck.current_index = 0
            deck.exhausted_card_ids.clear()
            if raw_episodes_provider:
                raw_eps = raw_episodes_provider(deck.series_id)
                prev_order = [c.item_ids[0] for c in deck.cards if c.item_ids]
                deck.cards = self.generate_cross_cycle_deck(raw_eps, prev_order, deck.cycle_number)
            event = "CYCLE_RESET"

        return deck, event


# =============================================================
# Entropy Calculation Utilities (Matching test_shuffle_entropy.py)
# =============================================================

def shannon_entropy(counter: Counter, total: int) -> float:
    entropy = 0.0
    for count in counter.values():
        if count > 0:
            p = count / total
            entropy -= p * math.log2(p)
    return entropy


def calculate_shuffle_entropy_metrics(runs: List[List[str]]) -> Dict[str, float]:
    """
    Evaluates:
    1. First item entropy
    2. Average positional entropy
    3. Transition entropy (adjacent pairs)
    4. Adjacency season clash rate
    """
    if not runs:
        return {}
    trials = len(runs)
    n = len(runs[0])

    # 1. First Item Entropy
    first_items = [run[0] for run in runs if run]
    first_counts = Counter(first_items)
    h_first = shannon_entropy(first_counts, len(first_items))
    max_h_first = math.log2(min(trials, n))

    # 2. Average Positional Entropy
    item_positions = defaultdict(list)
    for run in runs:
        for idx, item_id in enumerate(run):
            item_positions[item_id].append(idx)

    pos_entropies = []
    for item_id, positions in item_positions.items():
        pos_counts = Counter(positions)
        pos_entropies.append(shannon_entropy(pos_counts, len(positions)))
    avg_h_pos = sum(pos_entropies) / len(pos_entropies) if pos_entropies else 0.0
    max_h_pos = math.log2(min(trials, n))

    # 3. Transition Entropy (Markov adjacent pairs)
    pair_counts = Counter()
    total_pairs = 0
    for run in runs:
        for j in range(len(run) - 1):
            pair_counts[(run[j], run[j + 1])] += 1
            total_pairs += 1
    h_trans = shannon_entropy(pair_counts, total_pairs)

    return {
        "trials": trials,
        "item_count": n,
        "first_item_entropy": h_first,
        "max_first_entropy": max_h_first,
        "avg_pos_entropy": avg_h_pos,
        "max_pos_entropy": max_h_pos,
        "transition_entropy": h_trans,
        "first_entropy_ratio": (h_first / max_h_first) if max_h_first > 0 else 1.0,
        "pos_entropy_ratio": (avg_h_pos / max_h_pos) if max_h_pos > 0 else 1.0
    }


def calculate_season_clash_rate(cards: List[MediaCard]) -> float:
    if len(cards) <= 1:
        return 0.0
    clashes = 0
    for i in range(len(cards) - 1):
        if cards[i].category_key == cards[i + 1].category_key:
            clashes += 1
    return clashes / (len(cards) - 1)
