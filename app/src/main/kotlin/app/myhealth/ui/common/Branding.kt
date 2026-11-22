package app.myhealth.ui.common

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.myhealth.R
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.core.designsystem.tokens.MyHealthColors
import app.myhealth.core.designsystem.tokens.MyHealthThemeId

object Branding {
    @StringRes fun name(t: MyHealthThemeId) = when (t) {
        MyHealthThemeId.Aurora -> R.string.theme_aurora; MyHealthThemeId.Crystal -> R.string.theme_crystal; MyHealthThemeId.Pulse -> R.string.theme_pulse
        MyHealthThemeId.Evergreen -> R.string.theme_evergreen; MyHealthThemeId.Apricot -> R.string.theme_apricot
        MyHealthThemeId.Citrus -> R.string.theme_citrus; MyHealthThemeId.Iris -> R.string.theme_iris
    }
    @StringRes fun description(t: MyHealthThemeId) = when (t) {
        MyHealthThemeId.Aurora -> R.string.theme_aurora_desc; MyHealthThemeId.Crystal -> R.string.theme_crystal_desc; MyHealthThemeId.Pulse -> R.string.theme_pulse_desc
        MyHealthThemeId.Evergreen -> R.string.theme_evergreen_desc; MyHealthThemeId.Apricot -> R.string.theme_apricot_desc
        MyHealthThemeId.Citrus -> R.string.theme_citrus_desc; MyHealthThemeId.Iris -> R.string.theme_iris_desc
    }
}

/** Heart and pulse-line geometry in a 72 × 72 box (same art as the static launcher icon). */
private object HeartGeometry {
    const val BOX = 72f
    val heart: Path = PathParser().parsePathString("M36 60 C14 46 8 34 8 25 C8 16 15 10 23 10 C29 10 33 13 36 18 C39 13 43 10 49 10 C57 10 64 16 64 25 C64 34 58 46 36 60 Z").toPath()
    val ecg: Path = PathParser().parsePathString("M14 33 H27 L31 24 L37 42 L42 31 H58").toPath()
}

private const val BEAT = "beat" // animation inspector id, not UI copy
private const val BEAT_MILLIS = 1100

/**
 * The MyHealth mark: a heart that beats (double thump, ~55 bpm) with an ECG line drawing and erasing across it on
 * every beat (android-design-studio: splash / beating heart). Static when [alive] is false or animations are off.
 */
@Composable
fun HeartMark(size: Dp, heart: Color, line: Color, modifier: Modifier = Modifier, alive: Boolean = true, outlined: Boolean = false, glow: Color? = null) {
    val animate = alive && !MyHealthTheme.reduceMotion
    val tr = if (animate) rememberInfiniteTransition(label = BEAT) else null
    val beat = tr?.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = BEAT_MILLIS
        1f at 0; 1.14f at 132; 1f at 264; 1.09f at 396; 1f at 616
    }), label = BEAT)
    val trace = tr?.animateFloat(0f, 1f, infiniteRepeatable(tween(BEAT_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = BEAT)
    val measure = remember { PathMeasure().apply { setPath(HeartGeometry.ecg, false) } }
    val segment = remember { Path() }
    Canvas(modifier.size(size)) {
        val s = this.size.width / HeartGeometry.BOX
        val b = beat?.value ?: 1f
        // beat around the centre, then map the 72-unit art box onto the canvas
        scale(b, b, pivot = Offset(this.size.width / 2, this.size.height / 2)) {
            scale(s, s, pivot = Offset.Zero) {
                if (outlined) {
                    drawPath(HeartGeometry.heart, heart.copy(alpha = 0.14f))
                    drawPath(HeartGeometry.heart, heart, style = Stroke(3.5f, join = StrokeJoin.Round))
                } else drawPath(HeartGeometry.heart, heart)
                val stroke = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val p = trace?.value
                if (p == null) drawPath(HeartGeometry.ecg, line, style = stroke)
                else {
                    drawPath(HeartGeometry.ecg, line.copy(alpha = 0.22f), style = stroke)
                    val len = measure.length
                    val head = (p / 0.45f).coerceAtMost(1f); val tail = ((p - 0.45f) / 0.55f).coerceAtLeast(0f)
                    segment.reset()
                    if (head > tail && measure.getSegment(len * tail, len * head, segment, true)) {
                        if (glow != null) drawPath(segment, glow, style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(segment, line, style = stroke)
                    }
                }
            }
        }
    }
}

/**
 * The app mark on a rounded tile, in the CURRENT theme's material: Solid = hero shade with a light heart; Glass =
 * frosted tile with an outlined glowing heart. The launcher icon itself is static and never changes.
 */
@Composable
fun MyHealthLogo(size: Dp, modifier: Modifier = Modifier, colors: MyHealthColors = MyHealthTheme.colors, alive: Boolean = false) {
    val shape = RoundedCornerShape(size * 0.30f)
    val cd = stringResource(R.string.logo_cd)
    // No elevation shadow on glass: it would show through the translucent fill as a dark square.
    val tile = if (colors.isGlass) Modifier
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = if (colors.isDark) 0.18f else 0.75f), colors.accentSoft)), shape)
        .border(size * 0.012f, colors.glassBorder, shape)
    else Modifier.shadow(size * 0.14f, shape, ambientColor = colors.shadow, spotColor = colors.hero).background(colors.hero, shape)
    Box(modifier.size(size).then(tile).semantics { contentDescription = cd }, contentAlignment = Alignment.Center) {
        if (colors.isGlass) HeartMark(size * 0.62f, colors.accentBright.takeIf { colors.isDark } ?: colors.accent, colors.textPrimary, alive = alive, outlined = true, glow = colors.glow)
        else HeartMark(size * 0.62f, colors.onHero, colors.hero, alive = alive)
    }
}
