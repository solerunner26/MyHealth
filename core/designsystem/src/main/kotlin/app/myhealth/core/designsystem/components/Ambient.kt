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
 * Living backdrop (owner's request: colour that moves top-to-bottom and in wandering directions, never flat):
 * - a two-tone gradient of the hue whose direction slowly wanders (angle is a sum of unrelated slow sines);
 * - a soft band of light that travels from the top of the screen to the bottom, again and again;
 * - four large blurred "bubbles" (lighter/brighter steps of the same hue) drifting on independent paths, which is
 *   what makes the glass panels look like glass;
 * - a faint diagonal glare every ~16 s.
 * Everything is read in the draw phase only; static when the user turns Animations off (or the system does).
 */
@Composable
fun MyHealthAmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = MyHealthTheme.colors
    val reduce = MyHealthTheme.reduceMotion
    val tr = if (reduce) null else rememberInfiniteTransition(label = AMBIENT)
    // one slow clock (0..1 over ~60 s); every element derives its own motion from it at different rates
    val clock = tr?.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = AMBIENT)
    val glare = tr?.animateFloat(0f, 1f, infiniteRepeatable(tween(GLARE_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = AMBIENT)
    val top = lerp(c.background, c.accentSoft, if (c.isDark) 0.55f else 0.95f)
    val mid = lerp(c.background, lerp(c.accentSoft, c.accentBright, 0.25f), if (c.isDark) 0.25f else 0.55f)
    Box(modifier.fillMaxSize().background(c.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val t = clock?.value ?: 0.2f
            val a = (t * 2 * Math.PI).toFloat()
            // wandering gradient direction (mostly top → bottom)
            val angle = Math.toRadians(90.0 + 35.0 * sin(a) + 20.0 * sin(a * 2.3f + 1f) + 10.0 * cos(a * 3.7f)).toFloat()
            val cx = w / 2; val cy = h / 2; val r = maxOf(w, h)
            drawRect(Brush.linearGradient(listOf(top, mid, c.background),
                start = Offset(cx - cos(angle) * r / 2, cy - sin(angle) * r / 2), end = Offset(cx + cos(angle) * r / 2, cy + sin(angle) * r / 2)))
            fun glow(x: Float, y: Float, radius: Float, color: Color) = drawCircle(
                Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = radius), radius = radius, center = Offset(x, y))
            val strength = if (c.isDark) 0.40f else 0.55f
            // drifting bubbles: independent Lissajous paths look random but stay smooth
            glow(w * (0.20f + 0.25f * sin(a * 1.3f)), h * (0.18f + 0.12f * cos(a * 0.9f)), w * 0.75f, lerp(c.accentSoft, c.accentBright, 0.5f).copy(alpha = strength))
            glow(w * (0.85f - 0.20f * cos(a * 1.7f + 2f)), h * (0.45f + 0.20f * sin(a * 1.1f)), w * 0.65f, c.accentBright.copy(alpha = strength * 0.55f))
            glow(w * (0.35f + 0.30f * cos(a * 0.8f + 4f)), h * (0.82f + 0.10f * sin(a * 1.9f)), w * 0.80f, lerp(c.accentSoft, c.accent, 0.35f).copy(alpha = strength * 0.7f))
            glow(w * (0.70f + 0.25f * sin(a * 2.1f + 1f)), h * (0.10f + 0.15f * sin(a * 1.5f + 3f)), w * 0.45f, lerp(c.hero, c.accentBright, 0.6f).copy(alpha = strength * 0.35f))
            // a soft band of light travelling from top to bottom
            val bandY = ((t * 3f) % 1f) * (h * 1.4f) - h * 0.2f
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.accentBright.copy(alpha = if (c.isDark) 0.06f else 0.10f), Color.Transparent), startY = bandY - h * 0.15f, endY = bandY + h * 0.15f))
            // glare: a wide soft diagonal band crossing during the first 40 % of each cycle
            val g = glare?.value ?: 1f
            if (g < 0.4f) {
                val x = -w * 0.6f + (g / 0.4f) * w * 2.2f
                val band = w * 0.45f
                val alpha = if (c.isDark) 0.05f else 0.30f
                rotate(20f, pivot = Offset(x, h / 2)) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent), startX = x - band / 2, endX = x + band / 2),
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
