package org.jellycopter.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class BaseThemeMode {
    WARM_DARK,
    JELLYFIN_PURPLE,
    OLED_PURE_BLACK,
    CYBERPUNK_NEON
}

data class JellycopterColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val onBackground: Color,
    val onSurface: Color,
    val ambientGlow: Color,
    val borderGlow: Color
)

val LocalJellycopterColors = staticCompositionLocalOf {
    JellycopterThemeDefaults.warmDarkColors
}

object JellycopterThemeDefaults {
    val warmDarkColors = JellycopterColors(
        background = Color(0xFF14110E),
        surface = Color(0xFF1E1815),
        surfaceVariant = Color(0xFF2C221D),
        primaryAccent = Color(0xFFF59E0B),
        secondaryAccent = Color(0xFFE07A5F),
        onBackground = Color(0xFFFDF8F2),
        onSurface = Color(0xFFEDE4DC),
        ambientGlow = Color(0x4DF59E0B),
        borderGlow = Color(0xFFFBBF24)
    )

    val oledPureBlackColors = JellycopterColors(
        background = Color(0xFF000000),
        surface = Color(0xFF101010),
        surfaceVariant = Color(0xFF1C1C1C),
        primaryAccent = Color(0xFF3B82F6),
        secondaryAccent = Color(0xFF94A3B8),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFEDEDED),
        ambientGlow = Color(0x333B82F6),
        borderGlow = Color(0xFF60A5FA)
    )

    val jellyfinPurpleColors = JellycopterColors(
        background = Color(0xFF050714),
        surface = Color(0xFF121528),
        surfaceVariant = Color(0xFF1E2240),
        primaryAccent = Color(0xFFAA5CC3),
        secondaryAccent = Color(0xFF00A4DC),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFECEFF4),
        ambientGlow = Color(0x4DAA5CC3),
        borderGlow = Color(0xFFC084FC)
    )

    val cyberpunkNeonColors = JellycopterColors(
        background = Color(0xFF070913),
        surface = Color(0xFF131424),
        surfaceVariant = Color(0xFF1F2038),
        primaryAccent = Color(0xFF00F0FF),
        secondaryAccent = Color(0xFFFF0055),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFE0F7FA),
        ambientGlow = Color(0x4D00F0FF),
        borderGlow = Color(0xFF22D3EE)
    )

    fun getBaseColors(mode: BaseThemeMode): JellycopterColors {
        return when (mode) {
            BaseThemeMode.WARM_DARK -> warmDarkColors
            BaseThemeMode.OLED_PURE_BLACK -> oledPureBlackColors
            BaseThemeMode.JELLYFIN_PURPLE -> jellyfinPurpleColors
            BaseThemeMode.CYBERPUNK_NEON -> cyberpunkNeonColors
        }
    }
}

object JellycopterTheme {
    val colors: JellycopterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJellycopterColors.current
}

@Composable
fun JellycopterTheme(
    baseTheme: BaseThemeMode = BaseThemeMode.WARM_DARK,
    dynamicPalette: ExtractedPalette? = null,
    content: @Composable () -> Unit
) {
    val base = JellycopterThemeDefaults.getBaseColors(baseTheme)

    // Target dynamic accents based on extracted poster palette or base theme default
    val targetPrimary = dynamicPalette?.vibrantColor ?: base.primaryAccent
    val targetSecondary = dynamicPalette?.lightVibrantColor ?: base.secondaryAccent
    val targetAmbient = dynamicPalette?.dominantColor?.copy(alpha = 0.35f) ?: base.ambientGlow
    val targetBorder = dynamicPalette?.vibrantColor ?: base.borderGlow

    // Smooth 350ms color morphing transitions over base theme
    val animPrimary = animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animPrimary"
    ).value

    val animSecondary = animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animSecondary"
    ).value

    val animAmbient = animateColorAsState(
        targetValue = targetAmbient,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animAmbient"
    ).value

    val animBorder = animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animBorder"
    ).value

    val animatedThemeColors = base.copy(
        primaryAccent = animPrimary,
        secondaryAccent = animSecondary,
        ambientGlow = animAmbient,
        borderGlow = animBorder
    )

    val materialColors = darkColorScheme(
        primary = animatedThemeColors.primaryAccent,
        secondary = animatedThemeColors.secondaryAccent,
        background = animatedThemeColors.background,
        surface = animatedThemeColors.surface,
        onBackground = animatedThemeColors.onBackground,
        onSurface = animatedThemeColors.onSurface
    )

    CompositionLocalProvider(LocalJellycopterColors provides animatedThemeColors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
