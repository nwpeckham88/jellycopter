package org.jellycopter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jellycopter.ui.theme.JellycopterTheme

/**
 * 10-Foot Focusable Card for Android TV & Touch Devices.
 *
 * Implements Focus Elevation Scaling (1.08f), animated glow borders (3dp),
 * and ambient elevation (16dp) on remote focus or hover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    scaleTarget: Float = FocusEngine.FOCUS_SCALE,
    glowElevation: Dp = FocusEngine.GLOW_ELEVATION_FOCUSED,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scaleTarget else FocusEngine.UNFOCUSED_SCALE,
        animationSpec = FocusEngine.FocusSpringSpec,
        label = "FocusScale"
    )

    val currentElevation = if (isFocused) glowElevation else FocusEngine.GLOW_ELEVATION_UNFOCUSED
    val borderStroke = if (isFocused) {
        BorderStroke(FocusEngine.BORDER_WIDTH_FOCUSED, JellycopterTheme.colors.borderGlow)
    } else {
        BorderStroke(1.dp, Color(0x18FFFFFF))
    }

    val shadowAmbient = if (isFocused) Color(0x55000000) else Color(0x26000000)
    val shadowSpot = if (isFocused) Color(0x99000000) else Color(0x33000000)

    Box(
        modifier = modifier
            .scale(animatedScale)
            .shadow(currentElevation, shape, clip = false, ambientColor = shadowAmbient, spotColor = shadowSpot)
            .clip(shape)
            .border(borderStroke, shape)
            .onFocusChanged { focusState ->
                val newFocus = focusState.isFocused
                if (isFocused != newFocus) {
                    isFocused = newFocus
                    onFocusChanged?.invoke(newFocus)
                }
            }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            onClick()
                        }
                        true
                    }
                    Key.Menu -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            onLongClick?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content(isFocused)
        if (isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.03f))
            )
        }
    }
}
