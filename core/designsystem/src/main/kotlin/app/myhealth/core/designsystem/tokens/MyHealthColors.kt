package app.myhealth.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The five MyHealth themes. Each has exactly ONE accent hue (a neon in dark mode, a deep saturated tone in light
 * mode for contrast) plus near-neutral surfaces whose tint is either pure grey or a whisper of the same hue.
 * Status is never colour-only: icon, label, fill weight and marker position carry it.
 */
enum class MyHealthThemeId(val hue: Float, val accentSat: Float, val neutralSat: Float, val neutralHue: Float = hue) {
    /** Bioluminescent mint glow on deep night. */
    Aurora(168f, 0.92f, 0.10f),
    /** Frosted violet glass. */
    Crystal(252f, 0.90f, 0.12f),
    /** Calm sage on warm paper. */
    Botanical(150f, 0.38f, 0.14f, neutralHue = 40f),
    /** Electric precision blue. */
    Precision(214f, 0.95f, 0.10f),
    /** Neon heartbeat pink. */
    Pulse(330f, 0.88f, 0.08f),
}

/** Semantic colour roles. Screens use roles, never raw values. */
@Immutable
data class MyHealthColors(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val accentSoft: Color,
    /** Same hue, brighter: gradients, highlights and glows. */
    val accentBright: Color,
    val glow: Color,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
    val isDark: Boolean,
    val theme: MyHealthThemeId,
)

/** ARGB longs for JVM tests (contrast, hue). */
data class ColorRoles(
    val accent: Long, val onAccent: Long, val accentContainer: Long, val onAccentContainer: Long, val accentSoft: Long, val accentBright: Long,
    val background: Long, val backgroundAlt: Long, val surface: Long, val surfaceRaised: Long, val surfaceSunken: Long,
    val outline: Long, val outlineStrong: Long, val textPrimary: Long, val textSecondary: Long, val textTertiary: Long,
) {
    val chromatic get() = listOf(accent, accentContainer, onAccentContainer, accentSoft, accentBright)
    val neutrals get() = listOf(background, backgroundAlt, surface, surfaceRaised, surfaceSunken, outline, outlineStrong, textPrimary, textSecondary, textTertiary)
}

object Hsl {
    /** HSL (h 0–360, s/l 0–1) → opaque ARGB. */
    fun argb(h: Float, s: Float, l: Float): Long {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = (h % 360) / 60f
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        val m = l - c / 2
        fun ch(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}

object Contrast {
    private fun ch(v: Long): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
    fun luminance(c: Long) = 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    fun ratio(a: Long, b: Long): Double { val x = luminance(a); val y = luminance(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
}

object MyHealthColorRoles {
    /** Walks lightness from [start] (darker or lighter) until [against] reaches [min] contrast: every hue passes AA. */
    private fun solve(h: Float, s: Float, start: Float, against: Long, min: Double, darker: Boolean): Float {
        var l = start
        while (Contrast.ratio(Hsl.argb(h, s, l), against) < min && l in 0.02f..0.98f) l += if (darker) -0.01f else 0.01f
        return l
    }

    fun roles(t: MyHealthThemeId, dark: Boolean): ColorRoles {
        fun a(s: Float, l: Float) = Hsl.argb(t.hue, s, l)
        fun n(l: Float, s: Float = t.neutralSat) = Hsl.argb(t.neutralHue, s, l)
        val lightAccentL = solve(t.hue, t.accentSat * 0.85f, 0.40f, 0xFFFFFFFF, 4.8, darker = true)
        val darkOn = a(t.accentSat * 0.6f, 0.07f)
        val brightL = solve(t.hue, t.accentSat, 0.74f, darkOn, 4.8, darker = false).coerceAtMost(0.80f)
        val darkAccentL = solve(t.hue, t.accentSat, 0.62f, darkOn, 4.8, darker = false)
        return if (!dark) ColorRoles(
            accent = a(t.accentSat * 0.85f, lightAccentL), onAccent = 0xFFFFFFFF, accentContainer = a(t.accentSat * 0.70f, 0.90f), onAccentContainer = a(t.accentSat, 0.14f),
            accentSoft = a(t.accentSat * 0.6f, 0.955f), accentBright = a(t.accentSat, 0.50f),
            background = n(0.955f), backgroundAlt = n(0.925f, t.neutralSat * 1.4f), surface = n(0.985f), surfaceRaised = 0xFFFFFFFF, surfaceSunken = n(0.925f),
            outline = n(0.84f), outlineStrong = n(0.42f, t.neutralSat * 0.6f), textPrimary = n(0.09f, t.neutralSat * 0.5f), textSecondary = n(0.28f, t.neutralSat * 0.5f), textTertiary = n(0.38f, t.neutralSat * 0.5f),
        ) else ColorRoles(
            accent = a(t.accentSat, darkAccentL), onAccent = darkOn, accentContainer = a(t.accentSat * 0.55f, 0.20f), onAccentContainer = a(t.accentSat * 0.9f, 0.88f),
            accentSoft = a(t.accentSat * 0.45f, 0.12f), accentBright = a(t.accentSat, maxOf(brightL, darkAccentL)),
            background = n(0.045f), backgroundAlt = n(0.075f, t.neutralSat * 1.6f), surface = n(0.085f), surfaceRaised = n(0.12f), surfaceSunken = n(0.035f),
            outline = n(0.20f), outlineStrong = n(0.54f, t.neutralSat * 0.6f), textPrimary = n(0.95f, t.neutralSat * 0.5f), textSecondary = n(0.78f, t.neutralSat * 0.5f), textTertiary = n(0.64f, t.neutralSat * 0.5f),
        )
    }
}

private fun Long.c() = Color(this.toInt())

fun ColorRoles.toColors(isDark: Boolean, theme: MyHealthThemeId): MyHealthColors {
    val acc = accent.c(); val bright = accentBright.c()
    return MyHealthColors(
        accent = acc, onAccent = onAccent.c(), accentContainer = accentContainer.c(), onAccentContainer = onAccentContainer.c(), accentSoft = accentSoft.c(),
        accentBright = bright, glow = bright.copy(alpha = if (isDark) 0.45f else 0.22f),
        background = background.c(), backgroundAlt = backgroundAlt.c(), surface = surface.c(), surfaceRaised = surfaceRaised.c(), surfaceSunken = surfaceSunken.c(),
        glassTop = if (isDark) surfaceRaised.c().copy(alpha = 0.78f) else Color.White.copy(alpha = 0.86f),
        glassBottom = if (isDark) surface.c().copy(alpha = 0.62f) else surface.c().copy(alpha = 0.70f),
        glassBorder = if (isDark) bright.copy(alpha = 0.16f) else outline.c().copy(alpha = 0.9f),
        outline = outline.c(), outlineStrong = outlineStrong.c(), textPrimary = textPrimary.c(), textSecondary = textSecondary.c(), textTertiary = textTertiary.c(),
        scrim = Color(0x99000000.toInt()), isDark = isDark, theme = theme,
    )
}
