package app.nocturne.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalNocturneColors = staticCompositionLocalOf { NocturnePalettes.Oled }

/** Shorthand used by all components: `Nocturne.colors.accent`. */
object Nocturne {
    val colors: NocturneColors
        @Composable @ReadOnlyComposable get() = LocalNocturneColors.current
}

private const val THEME_FADE_MS = 350

@Composable
private fun animated(target: Color): Color =
    animateColorAsState(target, tween(THEME_FADE_MS), label = "themeColor").value

/** Colours cross-fade when the theme changes so the switch is unmistakable and smooth. */
@Composable
fun NocturneTheme(themeId: ThemeId, content: @Composable () -> Unit) {
    val t = NocturnePalettes.of(themeId)
    val c = NocturneColors(
        id = t.id,
        background = animated(t.background),
        surface = animated(t.surface),
        surfaceHigh = animated(t.surfaceHigh),
        border = animated(t.border),
        textPrimary = animated(t.textPrimary),
        textSecondary = animated(t.textSecondary),
        textMuted = animated(t.textMuted),
        accent = animated(t.accent),
        onAccent = animated(t.onAccent),
        accentContainer = animated(t.accentContainer),
        onAccentContainer = animated(t.onAccentContainer),
        secondary = animated(t.secondary),
        onSecondary = animated(t.onSecondary),
        positive = animated(t.positive),
        warning = animated(t.warning),
        danger = animated(t.danger),
        scrim = animated(t.scrim),
        coverStart = animated(t.coverStart),
        coverEnd = animated(t.coverEnd),
    )
    val scheme = darkColorScheme(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentContainer,
        onPrimaryContainer = c.onAccentContainer,
        secondary = c.secondary,
        onSecondary = c.onSecondary,
        secondaryContainer = c.surfaceHigh,
        onSecondaryContainer = c.textPrimary,
        tertiary = c.secondary,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceHigh,
        onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceHigh,
        surfaceContainerHighest = c.surfaceHigh,
        outline = c.border,
        outlineVariant = c.border,
        error = c.danger,
        onError = c.onAccent,
        scrim = c.scrim,
    )
    CompositionLocalProvider(LocalNocturneColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = NocturneTypography, content = content)
    }
}

private val Sans = FontFamily.SansSerif
val Mono = FontFamily.Monospace

// Sized for a 1080p phone (~420 dpi): body 15 sp and up, nothing below 12 sp.
val NocturneTypography = Typography(
    displayLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp),
)
