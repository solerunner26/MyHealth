package app.myhealth.core.designsystem

import app.myhealth.core.designsystem.tokens.ColorRoles
import app.myhealth.core.designsystem.tokens.MyHealthColorRoles
import app.myhealth.core.designsystem.tokens.MyHealthPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Enforces constraint 3 (one accent hue + neutrals) and WCAG AA contrast for text roles. */
class PaletteTest {
    private fun rgb(c: Long) = Triple(((c shr 16) and 0xFF).toInt(), ((c shr 8) and 0xFF).toInt(), (c and 0xFF).toInt())

    private fun hue(c: Long): Double? {
        val (r, g, b) = rgb(c).let { Triple(it.first / 255.0, it.second / 255.0, it.third / 255.0) }
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        if (d < 1e-9) return null
        val h = when (mx) { r -> ((g - b) / d) % 6; g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60
        return if (h < 0) h + 360 else h
    }

    private fun lum(c: Long): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        val (r, g, b) = rgb(c)
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }

    private fun contrast(a: Long, b: Long): Double { val la = lum(a); val lb = lum(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }

    @Test fun neutralsAreTrueGrey() {
        MyHealthPalette.neutrals.forEach { val (r, g, b) = rgb(it); assertTrue("neutral ${it.toString(16)}", r == g && g == b) }
    }

    @Test fun accentScaleHasExactlyOneHue() {
        MyHealthPalette.accentScale.forEach { val h = hue(it)!!; assertTrue("hue $h for ${it.toString(16)}", abs(h - 174) < 3) }
    }

    @Test fun everyRoleIsAccentHueOrGrey() {
        listOf(MyHealthColorRoles.Light, MyHealthColorRoles.Dark).flatMap(ColorRoles::all).forEach {
            val h = hue(it)
            assertTrue("role ${it.toString(16)} hue $h", h == null || abs(h - 174) < 3)
        }
    }

    @Test fun textContrastMeetsWcagAA() {
        for (r in listOf(MyHealthColorRoles.Light, MyHealthColorRoles.Dark)) {
            for (bg in listOf(r.surface, r.background, r.surfaceSunken)) {
                assertTrue(contrast(r.textPrimary, bg) >= 7.0)
                assertTrue(contrast(r.textSecondary, bg) >= 4.5)
                assertTrue("tertiary ${contrast(r.textTertiary, bg)}", contrast(r.textTertiary, bg) >= 4.5)
            }
            assertTrue("onAccent ${contrast(r.onAccent, r.accent)}", contrast(r.onAccent, r.accent) >= 4.5)
            assertTrue(contrast(r.onAccentContainer, r.accentContainer) >= 4.5)
            assertTrue("accent vs surface ${contrast(r.accent, r.surface)}", contrast(r.accent, r.surface) >= 3.0)
            assertTrue("outlineStrong ${contrast(r.outlineStrong, r.surface)}", contrast(r.outlineStrong, r.surface) >= 3.0)
        }
        assertEquals(17, MyHealthPalette.neutrals.size)
    }
}
