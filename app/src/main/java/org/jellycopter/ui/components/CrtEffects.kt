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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.random.Random

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
 * Curved glass tube with rounded corners (22.dp default), inner cathode ray vignette shadow,
 * subtle glass reflection glare curve, and phosphor scanlines.
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

        // Inner Cathode Ray Vignette Shadow (darker around edges & corners) + Glass Glare
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

                    // Subtle top glass glare curve
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x22FFFFFF),
                                Color(0x0AFFFFFF),
                                Color.Transparent
                            )
                        ),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.08f, 4f),
                        size = Size(size.width * 0.84f, size.height * 0.35f),
                        style = Stroke(width = 2.0f)
                    )

                    // Subtle top-left glass glare accent line
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
 * Looping animated CRT analog static noise / snow in Compose.
 * Uses Canvas with random noise pixels, gray shades, subtle jitter, and rolling vertical hold
 * for unprogrammed channels and channel switching.
 * Includes an on-screen prompt '[PRESS ENTER TO PROGRAM CHANNEL]' when channel is unprogrammed.
 */
@Composable
fun AnalogStaticSnow(
    modifier: Modifier = Modifier,
    isUnprogrammed: Boolean = false,
    channelNumber: Int? = null,
    onProgramChannel: (() -> Unit)? = null,
    promptText: String = "[PRESS ENTER TO PROGRAM CHANNEL]"
) {
    var noiseTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(33L) // ~30 fps analog static noise refresh rate
            noiseTick = (noiseTick + 1) % 10000
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0D0F12))
            .fillMaxSize()
    ) {
        // CRT Analog Noise Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .crtScanlines(lineSpacingPx = 4f, lineAlpha = 0.22f)
        ) {
            val rng = Random(noiseTick)
            val w = size.width
            val h = size.height

            // Random noise specks across the screen
            val numSpecks = 260
            for (i in 0 until numSpecks) {
                val x = rng.nextFloat() * w
                val y = rng.nextFloat() * h
                val speckW = rng.nextFloat() * 5f + 1.5f
                val speckH = rng.nextFloat() * 2.5f + 1f
                val alpha = rng.nextFloat() * 0.75f + 0.25f
                val shade = rng.nextInt(4)
                val speckColor = when (shade) {
                    0 -> Color.White.copy(alpha = alpha)
                    1 -> Color(0xFFD0D5DD).copy(alpha = alpha)
                    2 -> Color(0xFF98A2B3).copy(alpha = alpha)
                    else -> Color(0xFF475467).copy(alpha = alpha)
                }
                drawRect(
                    color = speckColor,
                    topLeft = Offset(x, y),
                    size = Size(speckW, speckH)
                )
            }

            // Horizontal static jitter bands (simulating analog sync jitter)
            val numBands = 5
            for (i in 0 until numBands) {
                val bandY = rng.nextFloat() * h
                val bandH = rng.nextFloat() * 4f + 1.5f
                val bandAlpha = rng.nextFloat() * 0.20f + 0.05f
                drawRect(
                    color = Color.White.copy(alpha = bandAlpha),
                    topLeft = Offset(0f, bandY),
                    size = Size(w, bandH)
                )
            }

            // Rolling vertical hold sync bar
            val rollProgress = (noiseTick % 100) / 100f
            val rollY = rollProgress * h
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x20000000),
                        Color(0x35FFFFFF),
                        Color(0x20000000),
                        Color.Transparent
                    ),
                    startY = rollY - 35f,
                    endY = rollY + 35f
                ),
                topLeft = Offset(0f, (rollY - 35f).coerceAtLeast(0f)),
                size = Size(w, 70f)
            )
        }

        // CRT Cathode Ray vignette over static noise
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x1A000000),
                                Color(0x55000000),
                                Color(0xAA000000)
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width.coerceAtLeast(size.height) * 0.72f
                        )
                    )
                }
        )

        // On-screen OSD prompt when channel is unprogrammed
        if (isUnprogrammed) {
            var isFocused by remember { mutableStateOf(false) }
            var blinkVisible by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(550L)
                    blinkVisible = !blinkVisible
                }
            }

            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE60A0E14))
                        .border(
                            BorderStroke(
                                width = if (isFocused) 2.dp else 1.5.dp,
                                color = if (isFocused) Color(0xFFFFD54F) else Color(0xFF10B981)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable(interactionSource = interactionSource)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                when (keyEvent.key) {
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        RetroAudioEffects.playKnobClick()
                                        onProgramChannel?.invoke()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                RetroAudioEffects.playKnobClick()
                                onProgramChannel?.invoke()
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val chLabel = if (channelNumber != null) "CH 0$channelNumber • " else ""
                    Text(
                        text = "${chLabel}NO BROADCAST SIGNAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFEF4444)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = promptText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = if (blinkVisible) Color(0xFF34D399) else Color(0xFF065F46),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "TUNER UNCONFIGURED • SELECT SOURCE TO ASSIGN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Authentic 1970s/1980s retro SMPTE color bar / broadcast test slate showing station callsign
 * ('[WJEL-TV CH 03]'), channel title, show title, 'PLEASE STAND BY / BROADCAST RETRIEVING'
 * countdown/loading bar, and retro typography.
 */
@Composable
fun BroadcastSlateCard(
    modifier: Modifier = Modifier,
    stationCallsign: String = "[WJEL-TV CH 03]",
    channelTitle: String = "CHANNEL 03 BROADCAST",
    showTitle: String = "SIGNAL ACQUISITION IN PROGRESS",
    statusText: String = "PLEASE STAND BY / BROADCAST RETRIEVING",
    progressFraction: Float? = null,
    scanlinesEnabled: Boolean = true
) {
    var animProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(40L)
            animProgress = (animProgress + 0.012f) % 1.0f
        }
    }

    val activeProgress = progressFraction ?: animProgress

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // SMPTE / EIA Color Bars Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Top Section (68% height): 7 primary SMPTE bars
            val topH = h * 0.68f
            val barW = w / 7f
            val topColors = listOf(
                Color(0xFFC0C0C0), // 75% Gray / White
                Color(0xFFC0C000), // Yellow
                Color(0xFF00C0C0), // Cyan
                Color(0xFF00C000), // Green
                Color(0xFFC000C0), // Magenta
                Color(0xFFC00000), // Red
                Color(0xFF0000C0)  // Blue
            )
            topColors.forEachIndexed { i, col ->
                drawRect(
                    color = col,
                    topLeft = Offset(i * barW, 0f),
                    size = Size(barW + 1f, topH)
                )
            }

            // 2. Middle Section (8% height): Castellation transition bars
            val midH = h * 0.08f
            val midColors = listOf(
                Color(0xFF0000C0), // Blue
                Color(0xFF131313), // Black
                Color(0xFFC000C0), // Magenta
                Color(0xFF131313), // Black
                Color(0xFF00C0C0), // Cyan
                Color(0xFF131313), // Black
                Color(0xFFC0C0C0)  // Gray
            )
            midColors.forEachIndexed { i, col ->
                drawRect(
                    color = col,
                    topLeft = Offset(i * barW, topH),
                    size = Size(barW + 1f, midH)
                )
            }

            // 3. Bottom Section (24% height): In-phase, 100% white, Quadrature, PLUGE
            val botH = h * 0.24f
            val botY = topH + midH
            val botBlockW = w / 6f
            val botColors = listOf(
                Color(0xFF002147), // -I (In-phase navy)
                Color(0xFFFFFFFF), // 100% White
                Color(0xFF32006A), // +Q (Quadrature violet)
                Color(0xFF131313), // Black
                Color(0xFF0A0A0A), // PLUGE sub-black
                Color(0xFF131313)  // Black
            )
            botColors.forEachIndexed { i, col ->
                drawRect(
                    color = col,
                    topLeft = Offset(i * botBlockW, botY),
                    size = Size(botBlockW + 1f, botH)
                )
            }
        }

        // Cathode Ray Vignette Shadow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x15000000),
                                Color(0x45000000),
                                Color(0x85000000)
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width.coerceAtLeast(size.height) * 0.75f
                        )
                    )
                }
        )

        // Center Broadcast Slate Placard Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xF20A0C10))
                    .border(
                        BorderStroke(
                            2.5.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFDF7A),
                                    Color(0xFFD4AF37),
                                    Color(0xFF8B6914),
                                    Color(0xFFD4AF37)
                                )
                            )
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .border(BorderStroke(1.dp, Color(0x80000000)), RoundedCornerShape(8.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Station Callsign Header
                Text(
                    text = stationCallsign,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = Color(0xFFFFD54F)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Channel Title
                Text(
                    text = channelTitle.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFFF1F5F9),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show / Program Title
                Text(
                    text = showTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Brass Accent Pinstripe Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFD4AF37),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status Text
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFF34D399)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Retro Segmented Countdown / Loading Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF05120B))
                        .border(BorderStroke(1.dp, Color(0xFF1B3D28)), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val segmentCount = 18
                    val litSegments = (activeProgress * segmentCount).toInt().coerceIn(0, segmentCount)
                    for (i in 0 until segmentCount) {
                        val isLit = i < litSegments
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (isLit) {
                                        if (i >= segmentCount - 3) Color(0xFFFFB300) else Color(0xFF34D399)
                                    } else {
                                        Color(0xFF0F2618)
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Calibration Metadata Footer
                Text(
                    text = "SMPTE-170M COLOR ALIGNMENT • 1000 Hz 0 dB REF • NTSC 525",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    color = Color(0x99A0AEC0)
                )
            }
        }

        // CRT Scanline Overlay
        if (scanlinesEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .crtScanlines(lineSpacingPx = 4f, lineAlpha = 0.20f)
            )
        }
    }
}

/**
 * Authentic Vintage Rotary VHF Channel Selector Dial:
 * Physical knob with channel numbers (2..13) that rotates with animated spring physics
 * when channel selection changes. Includes indicator arrow, brass center cap, and D-pad interaction.
 */
@Composable
fun RotaryChannelDial(
    selectedChannel: Int,
    onChannelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 58.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Channel 2 is 0 degrees, each channel advances by 30 degrees (12 channels around dial: 2..13)
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
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) JellycopterTheme.colors.borderGlow else Color(0x33C8A060)
                ),
                RoundedCornerShape(8.dp)
            )
            .background(Color(0xFF1E1710))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            val next = if (selectedChannel >= 13) 2 else selectedChannel + 1
                            RetroAudioEffects.playKnobClick()
                            onChannelSelected(next)
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val next = if (selectedChannel >= 13) 2 else selectedChannel + 1
                    RetroAudioEffects.playKnobClick()
                    onChannelSelected(next)
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label Header
        Text(
            text = "VHF CHANNEL",
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = Color(0xFFD4AF37) // Antique Brass
        )

        Spacer(modifier = Modifier.height(2.dp))

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
                .border(BorderStroke(1.5.dp, Color(0xFFD4AF37)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Channel Numbers Dial Ring (Stationary plate with channels 2..13)
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
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
                        radius = if (isCur) 2f else 1f,
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
                    .border(BorderStroke(1.2.dp, Color(0xFFE5C07B)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Ribbed Flutes / Tactile Ridge Bar across the knob
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(dialSize * 0.55f)
                        .clip(RoundedCornerShape(2.dp))
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
                        .border(BorderStroke(0.6.dp, Color(0xFFB8860B)), CircleShape)
                )

                // Brass Center Cap
                Box(
                    modifier = Modifier
                        .size(dialSize * 0.22f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFDF7A), // Polished brass center highlight
                                    Color(0xFFD4AF37), // Antique gold
                                    Color(0xFF8B6914), // Deep brass tone
                                    Color(0xFF4A3510)  // Dark rim
                                )
                            )
                        )
                        .border(BorderStroke(0.8.dp, Color(0xFF6B4E23)), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Selected Channel Callout Pip
        val chCallout = if (selectedChannel < 10) "CH 0$selectedChannel" else "CH $selectedChannel"
        Text(
            text = chCallout,
            fontSize = 10.sp,
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
            .border(BorderStroke(1.2.dp, Color(0xFF8B6914)), RoundedCornerShape(8.dp))
            .background(Color(0xFF140E08))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Meter Window Glass
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
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
                val pivot = Offset(w / 2f, h + 6f)
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
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Round
                )

                // Center pivot cap
                drawCircle(
                    color = Color(0xFF332211),
                    radius = 3.5f,
                    center = pivot
                )
            }

            // Gauge labels
            Text(
                text = "VU",
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF332211),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp)
            )
            Text(
                text = "-20",
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF332211),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)
            )
            Text(
                text = "+3dB",
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = meterLabel,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
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
                val chText = when {
                    channelNumber <= 0 -> "AUX"
                    channelNumber < 10 -> "CH 0$channelNumber"
                    else -> "CH $channelNumber"
                }
                Text(
                    text = chText,
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

    val shape = RoundedCornerShape(5.dp)

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
            .onKeyEvent { keyEvent ->
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            RetroAudioEffects.playKnobClick()
                            onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    RetroAudioEffects.playKnobClick()
                    onClick()
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.5.dp),
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
                    fontSize = 10.5.sp,
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
                    fontSize = 8.5.sp,
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
    slatCount: Int = 4,
    badgeText: String = "SOLID STATE STEREO"
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF140D07))
            .border(BorderStroke(1.dp, Color(0xFF3A2412)), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 0 until slatCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.dp))
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
                Spacer(modifier = Modifier.height(2.5.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                .padding(horizontal = 6.dp, vertical = 1.5.dp)
        ) {
            Text(
                text = badgeText,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = Color(0xFF1A1208)
            )
        }
    }
}

/**
 * Vintage 1978 Woodgrain TV Console Cabinet Frame:
 * Full 16:9 woodgrain cabinet border with rich walnut texture,
 * chamfered cabinet beveling, and metallic brass inlay pinstriping.
 */
@Composable
fun CabinetFrame(
    modifier: Modifier = Modifier,
    bezelThickness: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3A2012), // Warm walnut top rail
                        Color(0xFF28140A), // Deep wood grain
                        Color(0xFF452616), // Highlight plank
                        Color(0xFF231208), // Dark shadow
                        Color(0xFF180A04)  // Bottom rail
                    )
                )
            )
            .drawBehind {
                // Walnut wood grain fine striations
                val grainSpacing = 4f
                val numGrains = (size.height / grainSpacing).toInt()
                for (i in 0 until numGrains step 2) {
                    val y = i * grainSpacing
                    val alpha = if (i % 6 == 0) 0.08f else 0.035f
                    drawLine(
                        color = Color(0xFF6B3A1C).copy(alpha = alpha),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                // 3D Outer Cabinet Bevel: Top rim highlight, bottom rim shadow
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0x88000000),
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 2f
                )
            }
            // 3D Cabinet outer rim border
            .border(
                BorderStroke(2.dp, Color(0xFF150A04)),
                RoundedCornerShape(12.dp)
            )
            .padding(bezelThickness)
    ) {
        // Brass Inlay Pinstriping surrounding the screen and control fascia
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    BorderStroke(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFDF7A), // Inlaid brass highlight
                                Color(0xFFD4AF37), // Antique brass
                                Color(0xFF8B6914), // Burnished shadow
                                Color(0xFFD4AF37)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                .border(
                    BorderStroke(1.dp, Color(0x66000000)),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(4.dp)
        ) {
            content()
        }
    }
}
