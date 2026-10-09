package org.jellycopter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellycopter.ui.theme.JellycopterTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies horizontal CRT scanlines across any composable container.
 */
fun Modifier.crtScanlines(
    lineSpacingPx: Float = 4f,
    lineAlpha: Float = 0.18f
): Modifier = this.drawWithContent {
    drawContent()
    val numLines = (size.height / lineSpacingPx).toInt()
    for (i in 0 until numLines step 2) {
        val y = i * lineSpacingPx
        drawLine(
            color = Color.Black.copy(alpha = lineAlpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2f
        )
    }
}

/**
 * 4:3 CRT Picture Tube Bezel:
 * Curved glass tube with rounded corners, inner cathode ray vignette shadow,
 * subtle glass reflection glare, and phosphor scanlines.
 */
@Composable
fun CrtScreenTube(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    scanlinesEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            // Outer CRT chassis frame border: dual-tone deep bezel
            .border(
                BorderStroke(
                    width = 4.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF3E2C1C), // Wood / brass rim top
                            Color(0xFF1E140C), // Dark shadow bottom
                            Color(0xFF4A3522)
                        )
                    )
                ),
                shape = shape
            )
            .border(
                BorderStroke(2.dp, Color(0x66000000)),
                shape = shape
            )
            .background(Color(0xFF0A0C0E))
    ) {
        // Content inside the CRT tube
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scanlinesEnabled) Modifier.crtScanlines() else Modifier)
        ) {
            content()
        }

        // Inner Cathode Ray Vignette Shadow (darker around edges & corners)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Vignette corner shading
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x10000000),
                                Color(0x40000000),
                                Color(0x95000000)
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width.coerceAtLeast(size.height) * 0.72f
                        )
                    )

                    // Subtle top-left glass glare curve
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x22FFFFFF),
                                Color(0x08FFFFFF),
                                Color.Transparent
                            )
                        ),
                        start = Offset(20f, 12f),
                        end = Offset(size.width * 0.45f, 12f),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
        )
    }
}

/**
 * Authentic Vintage Rotary VHF Channel Selector Dial:
 * Physical knob with channel numbers (2..13) that rotates with animated spring physics
 * when channel selection changes.
 */
@Composable
fun RotaryChannelDial(
    selectedChannel: Int,
    onChannelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 90.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Channel 2 is 0 degrees, each channel advances by 30 degrees (12 channels around dial)
    val targetAngle = ((selectedChannel - 2) * 30f) % 360f

    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "RotaryDialAngle"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                BorderStroke(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) JellycopterTheme.colors.borderGlow else Color(0x33C8A060)
                ),
                RoundedCornerShape(10.dp)
            )
            .background(Color(0xFF1E1710))
            .padding(8.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            val next = if (selectedChannel >= 7) 2 else selectedChannel + 1
                            onChannelSelected(next)
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            val prev = if (selectedChannel <= 2) 7 else selectedChannel - 1
                            onChannelSelected(prev)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label Header
        Text(
            text = "VHF CHANNEL",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFFD4AF37) // Antique Brass
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Rotary Knob Face
        Box(
            modifier = Modifier
                .size(dialSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3A2B1D),
                            Color(0xFF1C130B),
                            Color(0xFF0F0A06)
                        )
                    )
                )
                .border(BorderStroke(2.dp, Color(0xFFD4AF37)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Channel Numbers Dial Ring (Stationary plate)
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Channels 2 through 13
                val channels = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
                channels.forEachIndexed { index, ch ->
                    val angleRad = (index * 30f - 90f) * (PI / 180f).toFloat()
                    val tickRadius = radius * 0.88f
                    val x = center.x + tickRadius * cos(angleRad)
                    val y = center.y + tickRadius * sin(angleRad)

                    val isCur = ch == selectedChannel
                    drawCircle(
                        color = if (isCur) Color(0xFFFFB300) else Color(0x88C8A060),
                        radius = if (isCur) 2.5f else 1.2f,
                        center = Offset(x, y)
                    )
                }
            }

            // Rotating Inner Grip Knob
            Box(
                modifier = Modifier
                    .size(dialSize * 0.65f)
                    .rotate(animatedAngle)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF4A3724),
                                Color(0xFF22170D),
                                Color(0xFF110A05)
                            )
                        )
                    )
                    .border(BorderStroke(1.5.dp, Color(0xFFE5C07B)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Ribbed Flutes / Tactile Ridge Bar across the knob
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(dialSize * 0.55f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFD54F),
                                    Color(0xFFB8860B),
                                    Color(0xFF6B4226)
                                )
                            )
                        )
                )

                // Channel indicator arrow / pip on top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300))
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Selected Channel Callout Pip
        Text(
            text = "CH 0$selectedChannel",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isFocused) Color(0xFFFFD54F) else Color(0xFF34D399)
        )
    }
}

/**
 * Backlit Analog VU Meter:
 * Warm glowing amber gauge with printed decibel arc and twitching needle.
 */
@Composable
fun AnalogVuMeter(
    modifier: Modifier = Modifier,
    meterLabel: String = "AUDIO VU"
) {
    // Dynamic needle oscillation simulating audio telemetry
    var needleFraction by remember { mutableFloatStateOf(0.35f) }

    LaunchedEffect(Unit) {
        val targets = listOf(0.28f, 0.45f, 0.38f, 0.62f, 0.30f, 0.50f, 0.40f)
        var idx = 0
        while (true) {
            delay((200..450).random().toLong())
            needleFraction = targets[idx % targets.size] + ((0..10).random() - 5) * 0.02f
            idx++
        }
    }

    val animatedNeedle by animateFloatAsState(
        targetValue = needleFraction.coerceIn(0.1f, 0.95f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "VuNeedle"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.5.dp, Color(0xFF8B6914)), RoundedCornerShape(8.dp))
            .background(Color(0xFF140E08))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Meter Window Glass
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                // Warm incandescent backlit meter glow
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF2D0), // Warm backlight
                            Color(0xFFFFE099),
                            Color(0xFFD4A054)
                        )
                    )
                )
                .border(BorderStroke(1.dp, Color(0xFF5A3E1B)), RoundedCornerShape(4.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw arc gauge ticks: -20, -10, -7, -5, -3, 0, +1, +2, +3
                val pivot = Offset(w / 2f, h + 10f)
                val meterLength = h * 1.15f

                // Scale line
                drawArc(
                    color = Color(0xFF332211),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.1f, h * 0.2f),
                    size = Size(w * 0.8f, h * 1.4f),
                    style = Stroke(width = 1.2f)
                )

                // Red overload region on right
                drawArc(
                    color = Color(0xFFD32F2F),
                    startAngle = 295f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(w * 0.1f, h * 0.2f),
                    size = Size(w * 0.8f, h * 1.4f),
                    style = Stroke(width = 2.2f)
                )

                // Needle: Angle spans from 215 degrees (-20dB) to 325 degrees (+3dB)
                val currentAngle = 215f + (animatedNeedle * 110f)
                val angleRad = currentAngle * (PI / 180f).toFloat()

                val tip = Offset(
                    pivot.x + meterLength * cos(angleRad),
                    pivot.y + meterLength * sin(angleRad)
                )

                drawLine(
                    color = Color(0xFF1A1A1A),
                    start = pivot,
                    end = tip,
                    strokeWidth = 2.0f,
                    cap = StrokeCap.Round
                )

                // Center pivot cap
                drawCircle(
                    color = Color(0xFF332211),
                    radius = 4.5f,
                    center = pivot
                )
            }

            // Gauge labels
            Text(
                text = "VU",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF332211),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
            )
            Text(
                text = "-20",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF332211),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp)
            )
            Text(
                text = "+3dB",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = meterLabel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Color(0xFFC8A060)
        )
    }
}

/**
 * Vacuum Fluorescent Display (VFD) Channel Readout:
 * Glowing electric green or amber digital status box.
 */
@Composable
fun VfdChannelDisplay(
    channelNumber: Int,
    channelName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF030D08))
            .border(BorderStroke(1.5.dp, Color(0xFF1B3D28)), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CH 0$channelNumber",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF34D399) // Phosphor green
                )
                Text(
                    text = "• STEREO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF10B981)
                )
            }
            Text(
                text = channelName.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF6EE7B7),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tactile Mechanical 3D Push Button:
 * Raised beveled border when idle, depressed inset bevel on focus/click.
 */
@Composable
fun TactilePushButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    isFocused -> Color(0xFF4A3420)
                    isSelected -> Color(0xFF382617)
                    else -> Color(0xFF23180E)
                }
            )
            // 3D Bevel highlight & shadow
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = when {
                        isFocused -> Color(0xFFFFD54F)
                        isSelected -> Color(0xFFD4AF37)
                        else -> Color(0xFF5A3E24)
                    }
                ),
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // LED indicator dot
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFocused -> Color(0xFFFFB300)
                                isSelected -> Color(0xFF34D399)
                                else -> Color(0xFF553C26)
                            }
                        )
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = when {
                        isFocused -> Color(0xFFFFFBEB)
                        isSelected -> Color(0xFFFFD54F)
                        else -> Color(0xFFD4AF37)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (badgeText != null) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) Color(0xFF34D399) else Color(0x99C8A060)
                )
            }
        }
    }
}

/**
 * Slotted Vintage TV Speaker Grille:
 * Horizontal fluted slats with fabric mesh backing and brass nameplate.
 */
@Composable
fun SpeakerGrille(
    modifier: Modifier = Modifier,
    slatCount: Int = 8,
    badgeText: String = "SOLID STATE STEREO"
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF140D07))
            .border(BorderStroke(1.dp, Color(0xFF3A2412)), RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 0 until slatCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1E140C),
                                Color(0xFF3E2818),
                                Color(0xFF1E140C)
                            )
                        )
                    )
            )
            if (i < slatCount - 1) {
                Spacer(modifier = Modifier.height(3.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Brass Badge Nameplate
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE5C07B),
                            Color(0xFFB8860B),
                            Color(0xFF8B6914)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = badgeText,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF1A1208)
            )
        }
    }
}
