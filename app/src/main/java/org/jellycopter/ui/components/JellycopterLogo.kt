package org.jellycopter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jellycopter.ui.theme.JellycopterTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom aerodynamic Jellycopter emblem:
 * Features tri-blade supersonic rotors radiating from a glowing media core.
 */
@Composable
fun JellycopterLogo(
    modifier: Modifier = Modifier,
    tint: Color = JellycopterTheme.colors.primaryAccent,
    coreTint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) / 2f

        // Outer subtle orbital ring
        drawCircle(
            color = tint.copy(alpha = 0.25f),
            radius = radius * 0.90f,
            style = Stroke(width = radius * 0.10f)
        )

        // 3 Aerodynamic curved rotor blades
        val bladeCount = 3
        for (i in 0 until bladeCount) {
            val angleDeg = i * 120.0 - 30.0
            val angleRad = Math.toRadians(angleDeg)
            val perpRad = angleRad + Math.PI / 2.0

            val startR = radius * 0.25f
            val endR = radius * 0.88f
            val width = radius * 0.22f

            val startX = center.x + (startR * cos(angleRad)).toFloat()
            val startY = center.y + (startR * sin(angleRad)).toFloat()

            val endX = center.x + (endR * cos(angleRad)).toFloat()
            val endY = center.y + (endR * sin(angleRad)).toFloat()

            val p1 = Offset(
                startX + (width * 0.4f * cos(perpRad)).toFloat(),
                startY + (width * 0.4f * sin(perpRad)).toFloat()
            )
            val p2 = Offset(
                endX + (width * 0.7f * cos(perpRad)).toFloat(),
                endY + (width * 0.7f * sin(perpRad)).toFloat()
            )
            val p3 = Offset(
                endX - (width * 0.3f * cos(perpRad)).toFloat(),
                endY - (width * 0.3f * sin(perpRad)).toFloat()
            )
            val p4 = Offset(
                startX - (width * 0.4f * cos(perpRad)).toFloat(),
                startY - (width * 0.4f * sin(perpRad)).toFloat()
            )

            val path = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                lineTo(p4.x, p4.y)
                close()
            }
            drawPath(path, color = tint.copy(alpha = 0.85f), style = Fill)
        }

        // Center hub circle
        drawCircle(
            color = tint,
            radius = radius * 0.30f
        )
        drawCircle(
            color = coreTint,
            radius = radius * 0.18f
        )
    }
}
