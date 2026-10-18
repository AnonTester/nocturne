package app.nocturne.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ThemeId(val label: String, val description: String) {
    OLED("OLED Black", "Pure black pixels, soft indigo accent. Zero glare on an AMOLED panel."),
    SLATE("Midnight Slate", "Deep blue-slate surfaces with a cool sky accent."),
    AMBER("Warm Amber", "Warm brown and amber only, no blue light. Easy before sleep."),
    RED("Night Red", "Pure red monochrome for pitch-dark rooms.");

    companion object {
        fun fromName(name: String?): ThemeId = entries.firstOrNull { it.name == name } ?: OLED
    }
}

/**
 * Every colour the UI may use. Components read these tokens only (never literals),
 * so switching theme recolours the whole app.
 */
@Immutable
data class NocturneColors(
    val id: ThemeId,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val positive: Color,
    val warning: Color,
    val danger: Color,
    val scrim: Color,
    val coverStart: Color,
    val coverEnd: Color,
)

object NocturnePalettes {
    val Oled = NocturneColors(
        id = ThemeId.OLED,
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0E),
        surfaceHigh = Color(0xFF15151C),
        border = Color(0xFF24242F),
        textPrimary = Color(0xFFF2F2F7),
        textSecondary = Color(0xFFA9A9BB),
        textMuted = Color(0xFF6C6C7E),
        accent = Color(0xFF818CF8),
        onAccent = Color(0xFF0A0A24),
        accentContainer = Color(0xFF1C1B4A),
        onAccentContainer = Color(0xFFC7CBFF),
        secondary = Color(0xFFFBBF24),
        onSecondary = Color(0xFF1A1200),
        positive = Color(0xFF34D399),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFFB7185),
        scrim = Color(0xEB000000),
        coverStart = Color(0xFF1B1B33),
        coverEnd = Color(0xFF05050A),
    )

    val Slate = NocturneColors(
        id = ThemeId.SLATE,
        background = Color(0xFF0A111F),
        surface = Color(0xFF111B31),
        surfaceHigh = Color(0xFF1A2745),
        border = Color(0xFF2A3A5E),
        textPrimary = Color(0xFFE3EAF6),
        textSecondary = Color(0xFF93A4C3),
        textMuted = Color(0xFF61759B),
        accent = Color(0xFF38BDF8),
        onAccent = Color(0xFF03202E),
        accentContainer = Color(0xFF0C3550),
        onAccentContainer = Color(0xFFBDE8FF),
        secondary = Color(0xFFA78BFA),
        onSecondary = Color(0xFF1A0F3D),
        positive = Color(0xFF4ADE80),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFF87171),
        scrim = Color(0xEB050A14),
        coverStart = Color(0xFF1C3560),
        coverEnd = Color(0xFF070D1A),
    )

    val Amber = NocturneColors(
        id = ThemeId.AMBER,
        background = Color(0xFF0B0803),
        surface = Color(0xFF161006),
        surfaceHigh = Color(0xFF241A0B),
        border = Color(0xFF3F2E12),
        textPrimary = Color(0xFFFCE7B8),
        textSecondary = Color(0xFFCFA765),
        textMuted = Color(0xFF8A6A34),
        accent = Color(0xFFF59E0B),
        onAccent = Color(0xFF1A1000),
        accentContainer = Color(0xFF3D2806),
        onAccentContainer = Color(0xFFFFD98A),
        secondary = Color(0xFFE8B04A),
        onSecondary = Color(0xFF1A1000),
        positive = Color(0xFFE5B84B),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFE2692B),
        scrim = Color(0xEB080501),
        coverStart = Color(0xFF3A2606),
        coverEnd = Color(0xFF0A0602),
    )

    val Red = NocturneColors(
        id = ThemeId.RED,
        background = Color(0xFF080102),
        surface = Color(0xFF130305),
        surfaceHigh = Color(0xFF1F0609),
        border = Color(0xFF3B0C11),
        textPrimary = Color(0xFFFF8F8F),
        textSecondary = Color(0xFFC25555),
        textMuted = Color(0xFF7C3030),
        accent = Color(0xFFE5484D),
        onAccent = Color(0xFF1A0102),
        accentContainer = Color(0xFF3A0A0E),
        onAccentContainer = Color(0xFFFFB3B3),
        secondary = Color(0xFFFF6B6B),
        onSecondary = Color(0xFF1A0102),
        positive = Color(0xFFFF8F8F),
        warning = Color(0xFFFF6B6B),
        danger = Color(0xFFFF3B3B),
        scrim = Color(0xEB060001),
        coverStart = Color(0xFF3A0A0E),
        coverEnd = Color(0xFF080102),
    )

    fun of(id: ThemeId): NocturneColors = when (id) {
        ThemeId.OLED -> Oled
        ThemeId.SLATE -> Slate
        ThemeId.AMBER -> Amber
        ThemeId.RED -> Red
    }
}
