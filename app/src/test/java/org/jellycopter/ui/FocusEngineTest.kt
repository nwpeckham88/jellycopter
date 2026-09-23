package org.jellycopter.ui

import androidx.compose.animation.core.Spring
import org.jellycopter.ui.components.FocusEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusEngineTest {

    @Test
    fun testFocusScaleCalculation() {
        // Unfocused must be 1.0f (neutral scale)
        val unfocusedScale = FocusEngine.calculateScale(isFocused = false)
        assertEquals(1.0f, unfocusedScale, 0.001f)

        // Focused must be 1.08f (10-foot TV emphasis)
        val focusedScale = FocusEngine.calculateScale(isFocused = true)
        assertEquals(1.08f, focusedScale, 0.001f)
    }

    @Test
    fun testCustomScaleTarget() {
        val customFocused = FocusEngine.calculateScale(isFocused = true, maxScale = 1.12f)
        assertEquals(1.12f, customFocused, 0.001f)

        val customUnfocused = FocusEngine.calculateScale(isFocused = false, maxScale = 1.12f)
        assertEquals(1.0f, customUnfocused, 0.001f)
    }

    @Test
    fun testGlowElevationCalculation() {
        val unfocusedElevation = FocusEngine.calculateGlowElevationDp(isFocused = false)
        assertEquals(2.0f, unfocusedElevation, 0.001f)

        val focusedElevation = FocusEngine.calculateGlowElevationDp(isFocused = true)
        assertEquals(16.0f, focusedElevation, 0.001f)
    }

    @Test
    fun testBorderWidthCalculation() {
        val unfocusedBorder = FocusEngine.calculateBorderWidthDp(isFocused = false)
        assertEquals(0.0f, unfocusedBorder, 0.001f)

        val focusedBorder = FocusEngine.calculateBorderWidthDp(isFocused = true)
        assertEquals(3.0f, focusedBorder, 0.001f)
    }

    @Test
    fun testCarouselSnappingPivotCalculations() {
        // TV 10-foot UI anchors focused card near left margin (8% inset)
        val viewportWidth = 1920f
        val cardWidth = 300f

        val snapOffset = FocusEngine.calculateSnapOffset(
            cardWidthPx = cardWidth,
            viewportWidthPx = viewportWidth,
            parentFraction = 0.08f
        )
        // 1920 * 0.08 = 153.6px
        assertEquals(153.6f, snapOffset, 0.1f)
    }

    @Test
    fun testSpringSpecParameters() {
        val spec = FocusEngine.FocusSpringSpec
        assertEquals(Spring.DampingRatioLowBouncy, spec.dampingRatio, 0.001f)
        assertEquals(Spring.StiffnessMedium, spec.stiffness, 0.001f)
    }
}
