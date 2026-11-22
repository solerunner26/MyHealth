package app.myhealth.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import app.myhealth.core.designsystem.theme.MyHealthTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living backdrop, never a flat colour (android-design-studio: "backgrounds are never flat").
 * - Glass: three large blobs of the hue drift slowly, their tint fading between the accent, its bright step and the
 *   hero shade; a faint diagonal glare sweeps across every ~16 s.
 * - Solid: a warm canvas with a soft header wash that drifts and breathes, a second wash low on the page, and the
 *   same glare sweep, much fainter.
 * Drawing reads the animation values in the draw phase only (no recomposition per frame). Static when the user
 * turned animations off.
 */
@Composable
fun MyHealthAmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = MyHealthTheme.colors
    val reduce = MyHealthTheme.reduceMotion
    val tr = if (reduce) null else rememberInfiniteTransition(label = AMBIENT)
    val drift = tr?.animateFloat(0f, 1f, infiniteRepeatable(tween(MyHealthTheme.motion.ambientMillis, easing = LinearEasing), RepeatMode.Reverse), label = AMBIENT)
    val hue = tr?.animateFloat(0f, 1f, infiniteRepeatable(tween(MyHealthTheme.motion.ambientMillis * 4 / 3, easing = LinearEasing), RepeatMode.Reverse), label = AMBIENT)
    val glare = tr?.animateFloat(0f, 1f, infiniteRepeatable(tween(GLARE_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = AMBIENT)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.background, c.backgroundAlt)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val t = drift?.value ?: 0.25f
            val k = hue?.value ?: 0.3f
            val a = t * 2 * Math.PI.toFloat()
            fun glow(cx: Float, cy: Float, r: Float, color: Color) = drawCircle(
                Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy),
            )
            if (c.isGlass) {
                val base = c.glow.alpha
                glow(w * (0.15f + 0.12f * sin(a)), h * (0.10f + 0.06f * cos(a)), w * 0.9f, lerp(c.accent, c.accentBright, k).copy(alpha = base * 0.95f))
                glow(w * (0.95f - 0.14f * cos(a)), h * (0.45f + 0.10f * sin(a * 0.7f)), w * 0.75f, lerp(c.hero, c.accent, 1f - k).copy(alpha = base * 0.85f))
                glow(w * (0.30f + 0.18f * cos(a * 0.6f)), h * (0.98f - 0.06f * sin(a)), w * 0.95f, lerp(c.accentBright, c.hero, k).copy(alpha = base * 0.6f))
            } else {
                val wash = if (c.isDark) 0.22f else 0.55f
                glow(w * (0.10f + 0.20f * sin(a)), h * (0.02f + 0.04f * cos(a)), w * 1.05f, lerp(c.accentSoft, c.heroAccent, 0.25f + 0.25f * k).copy(alpha = wash))
                glow(w * (1.0f - 0.15f * cos(a)), h * (0.80f + 0.06f * sin(a)), w * 0.7f, lerp(c.accentSoft, c.accentContainer, k).copy(alpha = wash * 0.6f))
            }
            // Glare: a wide soft diagonal band crossing during the first 40 % of each cycle.
            val g = glare?.value ?: 1f
            if (g < 0.4f) {
                val x = -w * 0.6f + (g / 0.4f) * w * 2.2f
                val band = w * 0.45f
                val alpha = if (c.isGlass) 0.06f else if (c.isDark) 0.04f else 0.35f
                val tint = if (c.isGlass || c.isDark) Color.White else c.surfaceRaised
                rotate(20f, pivot = Offset(x, h / 2)) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, tint.copy(alpha = alpha), Color.Transparent), startX = x - band / 2, endX = x + band / 2),
                        topLeft = Offset(x - band / 2, -h), size = androidx.compose.ui.geometry.Size(band, h * 3))
                }
            }
        }
        content()
    }
}

private const val GLARE_MILLIS = 16_000
private const val AMBIENT = "ambient" // animation inspector id, not UI copy
private const val APPEAR = "appear" // animation inspector id, not UI copy

/** Fade + rise on first appearance, staggered by [index]. No-op when animations are off. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    if (MyHealthTheme.reduceMotion) return@composed this
    val motion = MyHealthTheme.motion
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(12) * motion.staggerMillis).toLong())
        p.animateTo(1f, tween(motion.longMillis + 120, easing = motion.emphasized))
    }
    graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 28f }
}

/** Slow "breathing" scale/alpha for live indicators. */
@Composable
fun rememberBreath(): Float {
    if (MyHealthTheme.reduceMotion) return 0.5f
    val tr = rememberInfiniteTransition(label = APPEAR)
    val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(MyHealthTheme.motion.breatheMillis, easing = MyHealthTheme.motion.standard), RepeatMode.Reverse), label = APPEAR)
    return v
}

/** 0 → 1 once, for gauges filling up on first display (instant when animations are off). */
@Composable
fun rememberReveal(key: Any? = Unit): Float {
    val reduce = MyHealthTheme.reduceMotion
    val p = remember(key) { Animatable(if (reduce) 1f else 0f) }
    val ms = MyHealthTheme.motion.gaugeMillis; val e = MyHealthTheme.motion.emphasized
    LaunchedEffect(key) { p.animateTo(1f, tween(ms, easing = e)) }
    return p.value
}
