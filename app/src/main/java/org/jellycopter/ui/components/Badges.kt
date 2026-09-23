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

/**
 * Apple-style frosted metadata chip (e.g. "1080p", "4K", "DOLBY VISION", "22 min left").
 */
@Composable
fun MetadataChip(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFF1F5F9),
    backgroundColor: Color = Color(0x26FFFFFF)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    }
}

/**
 * Exhaustion Progress Badge: displays cycle progress (e.g. "42/250 played" or "68%").
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x40000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = JellycopterTheme.colors.primaryAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Midway Resume Indicator: displays a small horizontal progress bar
 * if playback is midway (between 0% and 80%).
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
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = JellycopterTheme.colors.primaryAccent,
                trackColor = Color(0x66000000)
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
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onRequestClicked,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) { isFocused ->
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) JellycopterTheme.colors.primaryAccent else Color(0xEEBE123C))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Missing S$seasonNumber • Request",
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
