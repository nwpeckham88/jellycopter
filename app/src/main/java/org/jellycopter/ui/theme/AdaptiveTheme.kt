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
    WOODGRAIN_CONSOLE_1978,
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
    JellycopterThemeDefaults.woodgrainConsoleColors
}

object JellycopterThemeDefaults {
    val woodgrainConsoleColors = JellycopterColors(
        background = Color(0xFF160E08),
        surface = Color(0xFF0C0E11),
        surfaceVariant = Color(0xFF26190E),
        primaryAccent = Color(0xFFFFB300),
        secondaryAccent = Color(0xFF34D399),
        onBackground = Color(0xFFFFFBEB),
        onSurface = Color(0xFFFEF3C7),
        ambientGlow = Color(0x22FFB300),
        borderGlow = Color(0xFFFFD54F)
    )

    val warmDarkColors = JellycopterColors(
        background = Color(0xFF0C0E14),
        surface = Color(0xFF141824),
        surfaceVariant = Color(0xFF1D2232),
        primaryAccent = Color(0xFFF5A623),
        secondaryAccent = Color(0xFF38BDF8),
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFE2E8F0),
        ambientGlow = Color(0x22F5A623),
        borderGlow = Color(0xFFFFD56B)
    )

    val oledPureBlackColors = JellycopterColors(
        background = Color(0xFF000000),
        surface = Color(0xFF0C0D12),
        surfaceVariant = Color(0xFF161822),
        primaryAccent = Color(0xFF3B82F6),
        secondaryAccent = Color(0xFF94A3B8),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFE2E8F0),
        ambientGlow = Color(0x183B82F6),
        borderGlow = Color(0xFF93C5FD)
    )

    val jellyfinPurpleColors = JellycopterColors(
        background = Color(0xFF050714),
        surface = Color(0xFF0E1122),
        surfaceVariant = Color(0xFF181C35),
        primaryAccent = Color(0xFFAA5CC3),
        secondaryAccent = Color(0xFF00A4DC),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFECEFF4),
        ambientGlow = Color(0x1FAA5CC3),
        borderGlow = Color(0xFFD8B4FE)
    )

    val cyberpunkNeonColors = JellycopterColors(
        background = Color(0xFF070913),
        surface = Color(0xFF0E111F),
        surfaceVariant = Color(0xFF171B30),
        primaryAccent = Color(0xFF00F0FF),
        secondaryAccent = Color(0xFFFF0055),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFE0F7FA),
        ambientGlow = Color(0x1A00F0FF),
        borderGlow = Color(0xFF67E8F9)
    )

    fun getBaseColors(mode: BaseThemeMode): JellycopterColors {
        return when (mode) {
            BaseThemeMode.WOODGRAIN_CONSOLE_1978 -> woodgrainConsoleColors
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
