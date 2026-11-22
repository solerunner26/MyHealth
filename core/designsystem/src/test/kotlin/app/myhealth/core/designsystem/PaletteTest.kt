package app.myhealth.core.designsystem

import app.myhealth.core.designsystem.tokens.MyHealthColorRoles
import app.myhealth.core.designsystem.tokens.MyHealthThemeId
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Every theme, light and dark: ONE accent hue, low-chroma neutrals, WCAG AA contrast for text and controls. */
class PaletteTest {
    private fun rgb(c: Long) = Triple(((c shr 16) and 0xFF).toInt() / 255.0, ((c shr 8) and 0xFF).toInt() / 255.0, (c and 0xFF).toInt() / 255.0)

    private fun hueSat(c: Long): Pair<Double?, Double> {
        val (r, g, b) = rgb(c)
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val l = (mx + mn) / 2
        val s = if (d < 1e-9) 0.0 else d / (1 - abs(2 * l - 1))
        if (d < 1e-9) return null to 0.0
        val h = when (mx) { r -> ((g - b) / d) % 6; g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60
        return (if (h < 0) h + 360 else h) to s
    }

    private fun ch(s: Double) = if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    private fun lum(c: Long): Double { val (r, g, b) = rgb(c); return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b) }
    private fun contrast(a: Long, b: Long): Double { val la = lum(a); val lb = lum(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }
    private fun hueDist(a: Double, b: Double) = min(abs(a - b), 360 - abs(a - b))

    @Test fun oneAccentHuePerThemeAndCalmNeutrals() {
        for (t in MyHealthThemeId.entries) for (dark in listOf(false, true)) {
            val r = MyHealthColorRoles.roles(t, dark)
            r.chromatic.forEach { c ->
                val (h, _) = hueSat(c)
                val (rr, gg, bb) = rgb(c); val chroma = max(rr, max(gg, bb)) - min(rr, min(gg, bb))
                // near-white/near-black tints (chroma < 6 %) have no perceivable hue after 8-bit rounding
                assertTrue("$t dark=$dark accent role ${c.toString(16)} hue $h", h == null || chroma < 0.06 || hueDist(h, t.hue.toDouble()) < 4)
            }
            r.neutrals.forEach { c -> val (_, s) = hueSat(c); assertTrue("$t dark=$dark neutral ${c.toString(16)} saturation $s", s <= 0.25) }
        }
    }

    @Test fun contrastMeetsWcagAA() {
        for (t in MyHealthThemeId.entries) for (dark in listOf(false, true)) {
            val r = MyHealthColorRoles.roles(t, dark)
            val tag = "$t dark=$dark"
            for (bg in listOf(r.surface, r.surfaceRaised, r.background, r.surfaceSunken, r.backgroundAlt)) {
                assertTrue("$tag primary", contrast(r.textPrimary, bg) >= 7.0)
                assertTrue("$tag secondary ${contrast(r.textSecondary, bg)}", contrast(r.textSecondary, bg) >= 4.5)
                assertTrue("$tag tertiary ${contrast(r.textTertiary, bg)}", contrast(r.textTertiary, bg) >= 4.5)
            }
            assertTrue("$tag onAccent ${contrast(r.onAccent, r.accent)}", contrast(r.onAccent, r.accent) >= 4.5)
            assertTrue("$tag onAccent over bright end of gradient ${contrast(r.onAccent, r.accentBright)}", !dark || contrast(r.onAccent, r.accentBright) >= 4.5)
            assertTrue("$tag onContainer", contrast(r.onAccentContainer, r.accentContainer) >= 4.5)
            assertTrue("$tag accent vs surface ${contrast(r.accent, r.surface)}", contrast(r.accent, r.surface) >= 3.0)
            assertTrue("$tag onHero ${contrast(r.onHero, r.hero)}", contrast(r.onHero, r.hero) >= 4.5)
            assertTrue("$tag heroAccent ${contrast(r.heroAccent, r.hero)}", contrast(r.heroAccent, r.hero) >= 3.0)
            assertTrue("$tag hero stands out from the page ${contrast(r.hero, r.background)}", contrast(r.hero, r.background) >= 1.5)
            assertTrue("$tag outlineStrong ${contrast(r.outlineStrong, r.surface)}", contrast(r.outlineStrong, r.surface) >= 3.0)
        }
    }
}
