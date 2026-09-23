package org.jellycopter.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Focus Engine Constants & Calculations for 10-Foot TV Leanback UI.
 *
 * Implements focus scaling (1.08f), glow borders (3dp), ambient elevation (16dp),
 * and carousel snapping pivot alignment (parentFraction = 0.08f).
 */
object FocusEngine {

    const val FOCUS_SCALE = 1.08f
    const val UNFOCUSED_SCALE = 1.0f

    val BORDER_WIDTH_FOCUSED: Dp = 3.dp
    val BORDER_WIDTH_UNFOCUSED: Dp = 0.dp

    val GLOW_ELEVATION_FOCUSED: Dp = 16.dp
    val GLOW_ELEVATION_UNFOCUSED: Dp = 2.dp

    const val PIVOT_PARENT_FRACTION = 0.08f
    const val PIVOT_CHILD_FRACTION = 0.0f

    val FocusSpringSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun calculateScale(isFocused: Boolean, maxScale: Float = FOCUS_SCALE): Float {
        return if (isFocused) maxScale else UNFOCUSED_SCALE
    }

    fun calculateBorderWidthDp(isFocused: Boolean): Float {
        return if (isFocused) BORDER_WIDTH_FOCUSED.value else BORDER_WIDTH_UNFOCUSED.value
    }

    fun calculateGlowElevationDp(isFocused: Boolean): Float {
        return if (isFocused) GLOW_ELEVATION_FOCUSED.value else GLOW_ELEVATION_UNFOCUSED.value
    }

    fun calculateSnapOffset(cardWidthPx: Float, viewportWidthPx: Float, parentFraction: Float = PIVOT_PARENT_FRACTION): Float {
        val targetLeft = viewportWidthPx * parentFraction
        return targetLeft
    }
}
