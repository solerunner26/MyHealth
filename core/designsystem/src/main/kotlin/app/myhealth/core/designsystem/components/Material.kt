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
    return when (kind) {
        SurfaceKind.Card -> if (c.isGlass) this
            .background(Brush.verticalGradient(listOf(c.glassTop, c.glassBottom)), shape)
            .border(MyHealthTheme.stroke.hairline, c.glassBorder, shape)
        else this.shadow(10.dp, shape, ambientColor = c.shadow, spotColor = c.shadow).background(c.surfaceRaised, shape)
        SurfaceKind.Soft -> this
            .then(if (c.isGlass) Modifier else Modifier.shadow(4.dp, shape, ambientColor = c.shadow, spotColor = c.shadow))
            .background(if (c.isGlass) Brush.verticalGradient(listOf(c.accentSoft.copy(alpha = 0.9f), c.glassBottom)) else Brush.verticalGradient(listOf(c.accentSoft, c.accentSoft)), shape)
            .border(MyHealthTheme.stroke.regular, c.accent.copy(alpha = if (c.isGlass) 0.55f else 0.9f), shape)
        SurfaceKind.Alert -> this
            .then(if (c.isGlass) Modifier else Modifier.shadow(10.dp, shape, ambientColor = c.danger.copy(alpha = 0.3f), spotColor = c.danger.copy(alpha = 0.4f)))
            .background(Brush.verticalGradient(listOf(c.dangerSoft, if (c.isGlass) c.dangerSoft.copy(alpha = 0.75f) else c.surfaceRaised)), shape)
            .border(MyHealthTheme.stroke.regular, c.danger, shape)
            .alertRim(shape)
        SurfaceKind.Hero -> {
            val base = if (c.isGlass) this
                .shadow(18.dp, shape, ambientColor = c.glow, spotColor = c.glow)
                .background(Brush.linearGradient(listOf(c.hero, androidx.compose.ui.graphics.lerp(c.hero, c.accent, if (c.isDark) 0.35f else 0.15f))), shape)
                .border(MyHealthTheme.stroke.hairline, c.heroAccent.copy(alpha = 0.45f), shape)
            else this
                .shadow(16.dp, shape, ambientColor = c.hero, spotColor = c.hero.copy(alpha = 0.55f))
                .background(Brush.linearGradient(listOf(c.hero, androidx.compose.ui.graphics.lerp(c.hero, c.heroAccent, 0.10f))), shape)
            base
        }
    }
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
