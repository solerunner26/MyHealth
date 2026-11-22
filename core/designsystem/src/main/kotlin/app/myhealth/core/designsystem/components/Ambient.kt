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
import app.myhealth.core.designsystem.theme.MyHealthTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calm animated backdrop: two or three very soft glows of the theme's accent drift slowly behind the content
 * (an "aurora"). Purely decorative, never behind text without a glass card in between, and static when the user
 * disabled animations.
 */
@Composable
fun MyHealthAmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = MyHealthTheme.colors
    val reduce = MyHealthTheme.reduceMotion
    val t = if (reduce) 0.25f else {
        val tr = rememberInfiniteTransition(label = AMBIENT)
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(MyHealthTheme.motion.ambientMillis, easing = LinearEasing), RepeatMode.Reverse), label = AMBIENT).value
    }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.background, c.backgroundAlt)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val a = t * 2 * Math.PI.toFloat()
            fun glow(cx: Float, cy: Float, r: Float, alpha: Float) = drawCircle(
                Brush.radialGradient(listOf(c.glow.copy(alpha = c.glow.alpha * alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy),
            )
            glow(w * (0.15f + 0.10f * sin(a)), h * (0.10f + 0.05f * cos(a)), w * 0.85f, 0.9f)
            glow(w * (0.95f - 0.12f * cos(a)), h * (0.42f + 0.08f * sin(a * 0.7f)), w * 0.70f, 0.55f)
            glow(w * (0.35f + 0.15f * cos(a * 0.6f)), h * (0.95f - 0.05f * sin(a)), w * 0.9f, 0.45f)
        }
        content()
    }
}

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
