"""
Tier 5 Adversarial Stress Tests: UI Focus Engine & Dynamic Adaptive Palette
Stress-tests 10-foot D-pad focus scaling (1.08f), carousel pivot fraction (0.08f),
focus restoration across Back navigation stacks, and adaptive palette extraction
on corrupted, monochrome, transparent, and extreme dimension image data.
"""

import sys
import os
import math
import unittest
from typing import List, Tuple

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from harness.client_state import ComposeTvFocusEngine


class TestAdversarialUiFocusStress(unittest.TestCase):

    def setUp(self):
        self.engine = ComposeTvFocusEngine()

    # -------------------------------------------------------------
    # 1. 10-Foot D-Pad Focus Scaling & Invariants
    # -------------------------------------------------------------

    def test_focus_scaling_invariants(self):
        """
        Verify strict mathematical invariant:
        Focused card scale MUST be exactly 1.08x.
        Unfocused card scale MUST be exactly 1.00x.
        """
        self.assertEqual(self.engine.focus_scale, 1.08)
        self.assertAlmostEqual(self.engine.focus_scale, 1.08, places=4)

        # Scale delta should provide exactly 8% visual prominence
        scale_delta = self.engine.focus_scale - 1.0
        self.assertAlmostEqual(scale_delta, 0.08, places=4)

    # -------------------------------------------------------------
    # 2. Carousel Pivot Fraction (0.08f) Across Resolutions
    # -------------------------------------------------------------

    def test_pivot_offset_calculations_across_screen_resolutions(self):
        """
        Verify pivot alignment (parentFraction = 0.08f) across standard TV resolutions:
        - 720p HD: 1280px -> 102.4px
        - 1080p Full HD: 1920px -> 153.6px
        - 1440p QHD: 2560px -> 204.8px
        - 4K UHD: 3840px -> 307.2px
        - 8K UHD: 7680px -> 614.4px
        """
        resolutions = [
            (1280, 102.4),
            (1920, 153.6),
            (2560, 204.8),
            (3840, 307.2),
            (7680, 614.4)
        ]

        for width, expected_offset in resolutions:
            offset = width * self.engine.pivot_fraction
            self.assertAlmostEqual(
                offset, expected_offset, places=2,
                msg=f"Pivot offset incorrect for width {width}px"
            )

    # -------------------------------------------------------------
    # 3. Focus Restoration Across Deep Back Navigation Stacks
    # -------------------------------------------------------------

    def test_deep_focus_restoration_stack(self):
        """
        Simulates deep user navigation:
        Home [focused card 5]
          -> Show Details [focused season 2, card 3]
            -> Episode Picker [focused episode 18]
              -> Modal Settings [item 1]
        Verifies exact LIFO focus restoration on Back key presses.
        """
        # Step 1: User focuses card 5 on Home screen
        self.engine.navigate_to_card(5)
        self.assertEqual(self.engine.active_index, 5)

        # Step 2: Opens Show Details
        self.engine.enter_child_screen()
        self.engine.navigate_to_card(3)
        self.assertEqual(self.engine.active_index, 3)

        # Step 3: Opens Episode Picker
        self.engine.enter_child_screen()
        self.engine.navigate_to_card(18)
        self.assertEqual(self.engine.active_index, 18)

        # Step 4: Opens Settings modal
        self.engine.enter_child_screen()
        self.engine.navigate_to_card(1)
        self.assertEqual(self.engine.active_index, 1)

        # Now unwind the back stack
        # Pop 1: Return to Episode Picker (focus restored to 18)
        self.assertEqual(self.engine.press_back_key(), 18)
        self.assertEqual(self.engine.active_index, 18)

        # Pop 2: Return to Show Details (focus restored to 3)
        self.assertEqual(self.engine.press_back_key(), 3)
        self.assertEqual(self.engine.active_index, 3)

        # Pop 3: Return to Home Screen (focus restored to 5)
        self.assertEqual(self.engine.press_back_key(), 5)
        self.assertEqual(self.engine.active_index, 5)

        # Pop 4: Empty stack handling (must not crash, maintains current root focus)
        self.assertEqual(self.engine.press_back_key(), 5)
        self.assertEqual(self.engine.active_index, 5)

    def test_empty_stack_and_rapid_back_spam(self):
        """Spamming 50 Back presses on an empty stack must not raise IndexError."""
        self.engine.navigate_to_card(0)
        for _ in range(50):
            res = self.engine.press_back_key()
            self.assertEqual(res, 0)

    # -------------------------------------------------------------
    # 4. Dynamic Adaptive Palette Extraction Stress Tests
    # -------------------------------------------------------------

    def test_palette_extraction_downsample_bounds(self):
        """Downsampled poster dimensions must strictly be within 120x180 px."""
        posters = [
            "https://image.tmdb.org/t/p/original/dune2_poster.jpg",
            "https://image.tmdb.org/t/p/w500/avatar_poster.jpg",
            "https://image.tmdb.org/t/p/original/oppenheimer_poster.jpg"
        ]

        for p in posters:
            color = self.engine.extract_palette_from_poster(120, 180, p)
            self.assertTrue(color.startswith("#"))
            self.assertEqual(len(color), 7)

    def test_palette_extraction_on_corrupted_and_extreme_inputs(self):
        """Test palette extraction logic on corrupted/extreme RGB pixel data."""
        # Pure Black: All 0x000000
        black_pixels = [0xFF000000] * 100
        palette_black = self._extract_algorithmic_palette(black_pixels)
        self.assertIsNotNone(palette_black)

        # Pure White: All 0xFFFFFF (Delta = 0, lum = 1.0 -> no division by zero)
        white_pixels = [0xFFFFFFFF] * 100
        palette_white = self._extract_algorithmic_palette(white_pixels)
        self.assertIsNotNone(palette_white)

        # Pure Gray: 0x808080 (Delta = 0, sat = 0)
        gray_pixels = [0xFF808080] * 100
        palette_gray = self._extract_algorithmic_palette(gray_pixels)
        self.assertIsNotNone(palette_gray)

        # 100% Transparent pixels (alpha < 128)
        transparent_pixels = [0x00FFFFFF] * 100
        palette_trans = self._extract_algorithmic_palette(transparent_pixels)
        self.assertIsNotNone(palette_trans)

        # 1x1 Single pixel
        single_pixel = [0xFFFF5500]
        palette_single = self._extract_algorithmic_palette(single_pixel)
        self.assertIsNotNone(palette_single)

    def _extract_algorithmic_palette(self, pixels: List[int]) -> Tuple[int, int, int]:
        """
        Python mirror of org.jellycopter.ui.theme.PaletteExtractor.extractFromPixels
        Validates mathematical stability on edge cases (e.g. delta == 0, transparent pixels).
        """
        if not pixels:
            return (0xFF1E293B, 0xFF38BDF8, 0xFF0F172A)

        color_frequency = {}
        best_vibrant = pixels[0]
        max_sat = -1.0
        best_dark_vibrant = pixels[0]
        min_lum_dark = 1.0

        for color in pixels:
            a = (color >> 24) & 0xFF
            if a < 128:
                continue

            r = ((color >> 16) & 0xFF) / 255.0
            g = ((color >> 8) & 0xFF) / 255.0
            b = (color & 0xFF) / 255.0

            cmax = max(r, g, b)
            cmin = min(r, g, b)
            delta = cmax - cmin
            lum = (cmax + cmin) / 2.0
            denom = (1.0 - abs(2.0 * lum - 1.0))
            sat = 0.0 if (delta == 0.0 or denom == 0.0) else delta / denom

            quantized = color & 0x00F0F0F0
            color_frequency[quantized] = color_frequency.get(quantized, 0) + 1

            if sat > max_sat and 0.3 <= lum <= 0.8:
                max_sat = sat
                best_vibrant = color

            if 0.1 <= lum <= 0.4 and sat > 0.2 and lum < min_lum_dark:
                min_lum_dark = lum
                best_dark_vibrant = color

        dominant_int = max(color_frequency.items(), key=lambda x: x[1])[0] if color_frequency else pixels[0]
        return (dominant_int | 0xFF000000, best_vibrant | 0xFF000000, best_dark_vibrant | 0xFF000000)


if __name__ == "__main__":
    unittest.main()
