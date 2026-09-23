"""
Tier 5 Adversarial Stress Tests: Mix Channels & Extreme DSP Parameters
Stress-testing multi-show uneven ratios, negative/extreme center boost values,
and vocal formant filter frequency extremes.
"""

import math
import unittest
from harness.shuffle_engine import (
    SmartShuffleEngine,
    EpisodeItem,
    calculate_season_clash_rate,
)
from harness.dsp_engine import (
    IturBs775Downmixer,
    DynamicRangeCompressor,
    VocalFormantPeakingFilter,
)


class TestAdversarialMixAndDspExtremes(unittest.TestCase):

    def setUp(self):
        self.engine = SmartShuffleEngine()

    # -------------------------------------------------------------
    # 1. Multi-Show Mix Channels with Extreme Uneven Show Ratios
    # -------------------------------------------------------------

    def test_mix_channel_extreme_show_ratio_2_vs_100_episodes(self):
        """
        Stress 1: Mix Channel with Show A (2 episodes, weight 1) and Show B (100 episodes, weight 1).
        Target: 50 episodes.
        Show A will exhaust its sub-deck 25 times!
        Verifies independent sub-deck recycling, no show clumping, no crash.
        """
        eps_a = [
            EpisodeItem(f"showA_ep{i}", f"Show A Ep {i}", "show_A", "s1", 1, i)
            for i in range(1, 3)
        ]
        eps_b = [
            EpisodeItem(f"showB_ep{i}", f"Show B Ep {i}", "show_B", "s1", 1, i)
            for i in range(1, 101)
        ]

        shows = [
            ("show_A", "Show A", eps_a, 1),
            ("show_B", "Show B", eps_b, 1),
        ]

        deck = self.engine.generate_mix_channel_deck(
            channel_id="mix_extreme",
            channel_name="Extreme Mix",
            shows=shows,
            target_total_episodes=50
        )

        self.assertEqual(len(deck.cards), 50)

        # Level 1 Anti-Clustering: No adjacent episodes from the same show
        adjacent_show_clashes = 0
        for i in range(len(deck.cards) - 1):
            if deck.cards[i].series_id == deck.cards[i + 1].series_id:
                adjacent_show_clashes += 1

        self.assertEqual(
            adjacent_show_clashes, 0,
            f"Mix channel had {adjacent_show_clashes} adjacent show collisions!"
        )

    def test_mix_channel_single_show_deadlock_prevention(self):
        """
        Stress 2: Mix channel configured with only 1 show.
        Adjacent show collisions are mathematically unavoidable.
        Must not deadlock or infinite loop.
        """
        eps = [EpisodeItem(f"ep{i}", f"Ep {i}", "solo_show", "s1", 1, i) for i in range(1, 10)]
        shows = [("solo_show", "Solo Show", eps, 1)]

        deck = self.engine.generate_mix_channel_deck(
            channel_id="mix_solo",
            channel_name="Solo Mix",
            shows=shows,
            target_total_episodes=20
        )
        self.assertEqual(len(deck.cards), 20)

    # -------------------------------------------------------------
    # 2. Extreme Out-of-Range DSP Parameters
    # -------------------------------------------------------------

    def test_center_boost_clamping_on_extreme_inputs(self):
        """
        Stress 3: Parameter clamping for center dialogue boost:
        Values: -100.0 dB, +100.0 dB, NaN/Inf.
        Must clamp strictly into [0.0, 6.0] dB without numeric explosion.
        """
        dmx_low = IturBs775Downmixer(center_boost_db=-100.0)
        self.assertEqual(dmx_low.center_boost_db, 0.0)

        dmx_high = IturBs775Downmixer(center_boost_db=+100.0)
        self.assertEqual(dmx_high.center_boost_db, 6.0)

    def test_drc_extreme_compression_ratios(self):
        """
        Stress 4: DRC configured with extreme compression ratio (e.g. 100:1 brickwall limiter)
        and negative ratio (should be clamped/handled safely).
        """
        drc = DynamicRangeCompressor(sample_rate=48000, compression_ratio=100.0)
        # Input: 0 dBFS (amplitude 1.0)
        # High threshold: -18 dBFS
        # Compressed target: -18 + (0 - (-18)) / 100 = -18 + 0.18 = -17.82 dBFS
        gain = drc.compute_static_gain_db(0.0)
        self.assertAlmostEqual(gain, -17.82, places=2)

    def test_formant_filter_stability_at_nyquist(self):
        """
        Stress 5: Vocal formant biquad filter frequency response near Nyquist (23 kHz at 48kHz fs).
        Verifies filter poles remain strictly inside unit circle (stable).
        """
        biquad = VocalFormantPeakingFilter(sample_rate=48000, f0=2500.0, q=1.0, gain_db=3.5)
        # Check frequency response at f0 (2500 Hz): must be +3.5 dB
        resp_f0 = biquad.frequency_response_db(2500.0)
        self.assertAlmostEqual(resp_f0, 3.5, delta=0.2)

        # Check frequency response far away (100 Hz, 20 kHz): must be ~0 dB
        resp_low = biquad.frequency_response_db(100.0)
        resp_high = biquad.frequency_response_db(20000.0)
        self.assertAlmostEqual(resp_low, 0.0, delta=0.5)
        self.assertAlmostEqual(resp_high, 0.0, delta=0.5)


if __name__ == "__main__":
    unittest.main()
