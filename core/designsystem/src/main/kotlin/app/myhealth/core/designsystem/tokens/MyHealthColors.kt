package app.myhealth.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.myhealth.core.designsystem.tokens.MyHealthPalette as P

/** Semantic colour roles. Screens use roles, never raw palette values. */
@Immutable
data class MyHealthColors(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val accentSoft: Color,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
    val isDark: Boolean,
)

/** Raw ARGB form used by tests (contrast and single-hue checks). */
data class ColorRoles(
    val accent: Long, val onAccent: Long, val accentContainer: Long, val onAccentContainer: Long,
    val accentSoft: Long, val background: Long, val surface: Long, val surfaceRaised: Long,
    val surfaceSunken: Long, val outline: Long, val outlineStrong: Long, val textPrimary: Long,
    val textSecondary: Long, val textTertiary: Long,
) {
    fun all(): List<Long> = listOf(accent, onAccent, accentContainer, onAccentContainer, accentSoft, background, surface,
        surfaceRaised, surfaceSunken, outline, outlineStrong, textPrimary, textSecondary, textTertiary)
}

object MyHealthColorRoles {
    val Light = ColorRoles(
        accent = P.Accent30, onAccent = P.White, accentContainer = P.Accent88, onAccentContainer = P.Accent10,
        accentSoft = P.Accent97, background = P.Grey98, surface = P.White, surfaceRaised = P.White,
        surfaceSunken = P.Grey95, outline = P.Grey84, outlineStrong = P.Grey42, textPrimary = P.Black08,
        textSecondary = P.Grey30, textTertiary = P.Grey42,
    )
    val Dark = ColorRoles(
        accent = P.Accent62, onAccent = P.Accent10, accentContainer = P.Accent25, onAccentContainer = P.Accent92,
        accentSoft = P.Accent15, background = P.Grey06, surface = P.Grey09, surfaceRaised = P.Grey12,
        surfaceSunken = P.Grey06, outline = P.Grey20, outlineStrong = P.Grey55, textPrimary = P.Grey95b,
        textSecondary = P.Grey76, textTertiary = P.Grey62,
    )
}


fun ColorRoles.toColors(isDark: Boolean) = MyHealthColors(
    accent = Color(accent.toInt()), onAccent = Color(onAccent.toInt()),
    accentContainer = Color(accentContainer.toInt()), onAccentContainer = Color(onAccentContainer.toInt()),
    accentSoft = Color(accentSoft.toInt()), background = Color(background.toInt()), surface = Color(surface.toInt()),
    surfaceRaised = Color(surfaceRaised.toInt()), surfaceSunken = Color(surfaceSunken.toInt()),
    outline = Color(outline.toInt()), outlineStrong = Color(outlineStrong.toInt()),
    textPrimary = Color(textPrimary.toInt()), textSecondary = Color(textSecondary.toInt()),
    textTertiary = Color(textTertiary.toInt()), scrim = Color(0x99000000.toInt()), isDark = isDark,
)
