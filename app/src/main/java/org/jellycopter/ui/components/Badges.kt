package org.jellycopter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellycopter.ui.theme.JellycopterTheme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border

/**
 * Precision studio-grade metadata chip (e.g. "1080p", "4K", "DOLBY VISION", "22 min left").
 */
@Composable
fun MetadataChip(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFF1F5F9),
    backgroundColor: Color = Color(0x24FFFFFF)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, Color(0x2AFFFFFF)), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/**
 * Exhaustion Progress Badge: displays cycle progress (e.g. "42/250 played (17%)").
 */
@Composable
fun ExhaustionBadge(
    exhaustedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val pct = if (totalCount > 0) (exhaustedCount * 100) / totalCount else 0
    val text = "$exhaustedCount/$totalCount played ($pct%)"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0x66000000))
            .border(BorderStroke(1.dp, Color(0x22FFFFFF)), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = JellycopterTheme.colors.primaryAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}

/**
 * Midway Resume Indicator: displays a small horizontal progress bar
 * if playback is midway (between 2% and 80%).
 */
@Composable
fun MidwayResumeIndicator(
    resumePositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0) return
    val progress = (resumePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    // Only show if between 2% and 80% (exhausted if >= 80%)
    if (progress in 0.02f..0.80f) {
        Column(modifier = modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = JellycopterTheme.colors.primaryAccent,
                trackColor = Color(0x55000000)
            )
        }
    }
}

/**
 * Missing Season Badge: displays indicator on library items with direct request trigger.
 */
@Composable
fun MissingSeasonBadge(
    seasonNumber: Int,
    onRequestClicked: () -> Unit,
    modifier: Modifier = Modifier,
    isRequested: Boolean = false
) {
    if (isRequested) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x2010B981))
                .border(BorderStroke(1.dp, Color(0x6010B981)), RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 4.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S$seasonNumber REQUESTED",
                color = Color(0xFF10B981),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    } else {
        FocusCard(
            onClick = onRequestClicked,
            shape = RoundedCornerShape(6.dp),
            modifier = modifier
        ) { isFocused ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isFocused) Color.White else Color(0x2CF43F5E))
                    .border(
                        BorderStroke(1.dp, if (isFocused) Color.White else Color(0x66F43F5E)),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Missing S$seasonNumber - Request",
                    color = if (isFocused) Color.Black else Color(0xFFFDA4AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}
