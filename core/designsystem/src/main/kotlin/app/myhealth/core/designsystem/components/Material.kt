package app.myhealth.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.LocalMyHealthColors
import app.myhealth.core.designsystem.theme.MyHealthTheme

/**
 * The surface materials of the two theme families (android-design-studio skill):
 * - [SurfaceKind.Card]: Glass = translucent gradient + hairline light edge; Solid = opaque paper + soft tinted shadow.
 * - [SurfaceKind.Hero]: the screen's focal card. Solid = deep shade of the hue; Glass = accent-tinted glass with glow.
 * - [SurfaceKind.Alert]: values NOT IN RANGE: the card turns red-tinted with a red edge that breathes slowly.
 * - [SurfaceKind.Soft]: tinted container for "slightly off" values and callouts.
 */
enum class SurfaceKind { Card, Hero, Alert, Soft }

@Composable
fun Modifier.material(kind: SurfaceKind, shape: Shape = MyHealthTheme.radii.lgShape): Modifier {
    val c = MyHealthTheme.colors
    if (!c.isGlass) return flat(kind, shape)
    // Liquid glass (owner's reference boards): milky translucent body, a soft glow of the hue underneath, a bright rim
    // that fades downwards, and a specular highlight across the top.
    val white = Color.White
    val rim = Brush.verticalGradient(listOf(white.copy(alpha = if (c.isDark) 0.34f else 0.95f), white.copy(alpha = if (c.isDark) 0.06f else 0.35f)))
    return when (kind) {
        SurfaceKind.Card -> this
            .shadow(12.dp, shape, ambientColor = c.glow.copy(alpha = 0.18f), spotColor = c.accent.copy(alpha = if (c.isDark) 0.30f else 0.18f))
            .background(Brush.verticalGradient(listOf(white.copy(alpha = if (c.isDark) 0.13f else 0.72f), lerp(white, c.accentSoft, 0.35f).copy(alpha = if (c.isDark) 0.05f else 0.48f))), shape)
            .border(1.dp, rim, shape).gloss(shape, if (c.isDark) 0.10f else 0.55f)
        SurfaceKind.Soft -> this
            .shadow(10.dp, shape, ambientColor = c.glow, spotColor = c.accent.copy(alpha = 0.25f))
            .background(Brush.verticalGradient(listOf(c.accentSoft.copy(alpha = 0.92f), c.accentSoft.copy(alpha = 0.55f))), shape)
            .border(1.5.dp, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.8f), c.accent.copy(alpha = 0.35f))), shape).gloss(shape, 0.35f)
        SurfaceKind.Alert -> this
            .shadow(12.dp, shape, ambientColor = c.danger.copy(alpha = 0.25f), spotColor = c.danger.copy(alpha = 0.35f))
            .background(Brush.verticalGradient(listOf(c.dangerSoft.copy(alpha = 0.95f), c.dangerSoft.copy(alpha = 0.65f))), shape)
            .border(1.5.dp, c.danger, shape).gloss(shape, 0.30f)
            .alertRim(shape)
        SurfaceKind.Hero -> this
            .shadow(20.dp, shape, ambientColor = c.glow, spotColor = c.accent.copy(alpha = 0.45f))
            .background(Brush.linearGradient(listOf(c.hero, lerp(c.hero, c.accent, if (c.isDark) 0.35f else 0.22f))), shape)
            .border(1.dp, Brush.verticalGradient(listOf(c.heroAccent.copy(alpha = 0.7f), c.heroAccent.copy(alpha = 0.15f))), shape).gloss(shape, 0.18f)
    }
}

/** Flat, light-weight surfaces when "Glass effects" is off (slower phones). */
@Composable
private fun Modifier.flat(kind: SurfaceKind, shape: Shape): Modifier {
    val c = MyHealthTheme.colors
    return when (kind) {
        SurfaceKind.Card -> this.background(c.surfaceRaised, shape).border(MyHealthTheme.stroke.hairline, c.outline, shape)
        SurfaceKind.Soft -> this.background(c.accentSoft, shape).border(MyHealthTheme.stroke.regular, c.accent, shape)
        SurfaceKind.Alert -> this.background(c.dangerSoft, shape).border(MyHealthTheme.stroke.regular, c.danger, shape)
        SurfaceKind.Hero -> this.background(c.hero, shape)
    }
}

/** Specular highlight: a white sheen over the top ~45 % of the shape, the "liquid" look of glass. */
fun Modifier.gloss(shape: Shape, strength: Float): Modifier = this.drawWithContent {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply { this.addOutline(outline) }
    clipPath(path) {
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = strength), Color.White.copy(alpha = 0f)), endY = size.height * 0.45f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.45f))
    }
    drawContent()
}

/** A glow ring that breathes (2.4 s) around out-of-range values; static when animations are off. */
@Composable
private fun Modifier.alertRim(shape: Shape): Modifier {
    val c = MyHealthTheme.colors
    val reduce = MyHealthTheme.reduceMotion
    val p = if (reduce) null else rememberInfiniteTransition(label = RIM).animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = RIM)
    return this.drawBehind {
        val v = p?.value ?: 0.5f
        val outline = shape.createOutline(size, layoutDirection, this)
        val ring = c.danger
        drawOutline(outline, ring.copy(alpha = 0.25f + 0.45f * v), style = Stroke(width = (2f + 4f * v).dp.toPx()))
    }
}

private const val RIM = "rim" // animation inspector id, not UI copy

/**
 * Content drawn ON hero material: text and accent roles are swapped for the hero's own, so every existing component
 * (labels, gauges, badges) stays legible without per-call colours.
 */
@Composable
fun ProvideHeroColors(content: @Composable () -> Unit) {
    val c = MyHealthTheme.colors
    val on = c.onHero
    val swapped = c.copy(
        textPrimary = on, textSecondary = on.copy(alpha = 0.82f), textTertiary = on.copy(alpha = 0.70f),
        accent = c.heroAccent, onAccent = c.hero, accentBright = c.heroAccent,
        accentSoft = on.copy(alpha = 0.14f), accentContainer = on.copy(alpha = 0.16f), onAccentContainer = on,
        surface = on.copy(alpha = 0.10f), surfaceRaised = on.copy(alpha = 0.14f), surfaceSunken = on.copy(alpha = 0.12f),
        outline = on.copy(alpha = 0.22f), outlineStrong = on.copy(alpha = 0.6f), glassBorder = on.copy(alpha = 0.22f),
        glassTop = on.copy(alpha = 0.10f), glassBottom = on.copy(alpha = 0.04f),
    )
    CompositionLocalProvider(LocalMyHealthColors provides swapped, content = content)
}
