package app.myhealth.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/** Visual family (see the android-design-studio skill): frosted panels over living colour, or warm paper + hero card. */
enum class ThemeFamily { Glass, Solid }

/**
 * MyHealth themes. Each is exactly ONE accent hue plus neutrals; tints, glows, hero cards and chart steps are
 * lightness/chroma steps of that hue. Status is never colour-only: icon, label, fill weight and material carry it.
 */
enum class MyHealthThemeId(val hue: Float, val accentSat: Float, val neutralSat: Float, val family: ThemeFamily, val neutralHue: Float = hue) {
    /** Clean white with a calm sky-blue accent: the default theme. */
    Pearl(205f, 0.72f, 0.06f, ThemeFamily.Solid),
    /** Teal light through frosted glass on a night sky. */
    Aurora(168f, 0.80f, 0.10f, ThemeFamily.Glass),
    /** Clear blue glass, precise and cool. */
    Crystal(214f, 0.85f, 0.10f, ThemeFamily.Glass),
    /** Magenta pulse glowing behind glass. */
    Pulse(330f, 0.80f, 0.08f, ThemeFamily.Glass),
    /** Deep forest green on warm cream paper. */
    Evergreen(150f, 0.45f, 0.14f, ThemeFamily.Solid, neutralHue = 45f),
    /** Warm apricot on soft cream. */
    Apricot(26f, 0.75f, 0.15f, ThemeFamily.Solid),
    /** Lime and ink: bold and digital. */
    Citrus(80f, 0.80f, 0.15f, ThemeFamily.Solid),
    /** Calm violet on lavender white. */
    Iris(255f, 0.70f, 0.14f, ThemeFamily.Solid);

    companion object {
        val Default = Pearl
        /** Stored names from earlier versions keep working. */
        fun parse(name: String?): MyHealthThemeId = when (name) {
            "Botanical" -> Evergreen
            "Precision" -> Crystal
            else -> entries.firstOrNull { it.name == name } ?: Default
        }
    }
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
    /** Hero material: the one focal card per screen and every out-of-range value. */
    val hero: Color,
    val onHero: Color,
    /** Gauge fills, deltas and highlights drawn on [hero]. */
    val heroAccent: Color,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    /**
     * The ONE exception to the single-hue rule (owner's request): red marks a value that is NOT IN RANGE, so it can
     * be spotted at a glance. Used only for out-of-range status labels and the out-of-range tile edge, always with an
     * icon and a word (High/Low), never as decoration.
     */
    val danger: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    /** Soft, hue-tinted drop shadow (Solid family). */
    val shadow: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
    val isDark: Boolean,
    val theme: MyHealthThemeId,
    /** Liquid-glass surfaces (default for every theme); off = flat, light-weight surfaces for slower phones. */
    val isGlass: Boolean = true,
) {
    val family get() = theme.family
}

/** ARGB longs for JVM tests (contrast, hue). */
data class ColorRoles(
    val accent: Long, val onAccent: Long, val accentContainer: Long, val onAccentContainer: Long, val accentSoft: Long, val accentBright: Long,
    val hero: Long, val onHero: Long, val heroAccent: Long,
    val background: Long, val backgroundAlt: Long, val surface: Long, val surfaceRaised: Long, val surfaceSunken: Long,
    val outline: Long, val outlineStrong: Long, val textPrimary: Long, val textSecondary: Long, val textTertiary: Long,
) {
    val chromatic get() = listOf(accent, accentContainer, onAccentContainer, accentSoft, accentBright, hero, heroAccent)
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

/** Out-of-range red (see [MyHealthColors.danger]); same for every theme so "red = not in range" never changes. */
object Danger {
    fun danger(dark: Boolean): Long = if (dark) 0xFFFF6B6B else 0xFFC62828
    fun onDanger(dark: Boolean): Long = if (dark) 0xFF2A0606 else 0xFFFFFFFF
    fun soft(dark: Boolean): Long = if (dark) 0xFF3A1416 else 0xFFFDECEC
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
        val solid = t.family == ThemeFamily.Solid
        val lightAccentL = solve(t.hue, t.accentSat * 0.85f, 0.40f, 0xFFFFFFFF, 4.8, darker = true)
        val darkOn = a(t.accentSat * 0.6f, 0.07f)
        val brightL = solve(t.hue, t.accentSat, 0.74f, darkOn, 4.8, darker = false).coerceAtMost(0.80f)
        val darkAccentL = solve(t.hue, t.accentSat, 0.62f, darkOn, 4.8, darker = false)
        val accentLight = a(t.accentSat * 0.85f, lightAccentL)
        val accentDark = a(t.accentSat, darkAccentL)
        // Hero: deep shade of the hue (Citrus: ink) in light mode; the accent itself (inverted material) in dark mode.
        val heroLight = if (t == MyHealthThemeId.Citrus) a(0.30f, 0.09f) else a(t.accentSat * 0.62f, 0.21f)
        val heroDark = if (solid) accentDark else a(t.accentSat * 0.6f, 0.23f)
        val hero = if (dark) heroDark else heroLight
        val onHero = if (dark && solid) darkOn else n(0.975f, t.neutralSat * 0.5f)
        val heroAccentL = solve(t.hue, t.accentSat, 0.62f, hero, 3.2, darker = dark && solid)
        val heroAccent = a(t.accentSat, heroAccentL)
        return if (!dark) ColorRoles(
            accent = accentLight, onAccent = 0xFFFFFFFF, accentContainer = a(t.accentSat * 0.70f, 0.90f), onAccentContainer = a(t.accentSat, 0.14f),
            accentSoft = a(t.accentSat * 0.6f, if (solid) 0.92f else 0.955f), accentBright = a(t.accentSat, 0.50f),
            hero = hero, onHero = onHero, heroAccent = heroAccent,
            background = n(if (t == MyHealthThemeId.Pearl) 0.975f else 0.955f), backgroundAlt = n(if (t == MyHealthThemeId.Pearl) 0.955f else 0.925f, t.neutralSat * 1.4f),
            surface = if (solid) 0xFFFFFFFF else n(0.985f), surfaceRaised = 0xFFFFFFFF, surfaceSunken = n(0.925f),
            outline = n(0.84f), outlineStrong = n(0.42f, t.neutralSat * 0.6f), textPrimary = n(0.09f, t.neutralSat * 0.5f), textSecondary = n(0.28f, t.neutralSat * 0.5f), textTertiary = n(0.38f, t.neutralSat * 0.5f),
        ) else ColorRoles(
            accent = accentDark, onAccent = darkOn, accentContainer = a(t.accentSat * 0.55f, 0.20f), onAccentContainer = a(t.accentSat * 0.9f, 0.88f),
            accentSoft = a(t.accentSat * 0.45f, 0.12f), accentBright = a(t.accentSat, maxOf(brightL, darkAccentL)),
            hero = hero, onHero = onHero, heroAccent = heroAccent,
            background = n(0.045f), backgroundAlt = n(0.075f, t.neutralSat * 1.6f), surface = n(if (solid) 0.10f else 0.085f), surfaceRaised = n(if (solid) 0.13f else 0.12f), surfaceSunken = n(0.035f),
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
        hero = hero.c(), onHero = onHero.c(), heroAccent = heroAccent.c(),
        danger = Color(Danger.danger(isDark).toInt()), onDanger = Color(Danger.onDanger(isDark).toInt()), dangerSoft = Color(Danger.soft(isDark).toInt()),
        shadow = if (isDark) Color(0x66000000) else Color(Hsl.argb(theme.hue, 0.35f, 0.18f).toInt()).copy(alpha = 0.16f),
        background = background.c(), backgroundAlt = backgroundAlt.c(), surface = surface.c(), surfaceRaised = surfaceRaised.c(), surfaceSunken = surfaceSunken.c(),
        glassTop = if (isDark) surfaceRaised.c().copy(alpha = 0.78f) else Color.White.copy(alpha = 0.86f),
        glassBottom = if (isDark) surface.c().copy(alpha = 0.62f) else surface.c().copy(alpha = 0.70f),
        glassBorder = if (isDark) bright.copy(alpha = 0.16f) else outline.c().copy(alpha = 0.9f),
        outline = outline.c(), outlineStrong = outlineStrong.c(), textPrimary = textPrimary.c(), textSecondary = textSecondary.c(), textTertiary = textTertiary.c(),
        scrim = Color(0x99000000.toInt()), isDark = isDark, theme = theme,
    )
}
