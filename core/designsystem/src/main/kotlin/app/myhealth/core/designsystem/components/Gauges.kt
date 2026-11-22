package app.myhealth.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Decorative, animated gauges. They never carry the only copy of information: the tile around them always states
 * the value, unit and status in text, so gauges are hidden from accessibility services (clearAndSetSemantics).
 */

private const val WAVE = "wave" // animation inspector id, not UI copy

/** Fraction of [v] on [min..max], clamped. */
fun gaugeFraction(v: Double, min: Double, max: Double): Float = if (max <= min) 0.5f else ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)

private fun DrawScope.hatch(rect: Rect, color: Color, step: Float) {
    val p = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2))) }
    clipPath(p) {
        var x = rect.left - rect.height
        while (x < rect.right) { drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), strokeWidth = 1.5f); x += step }
    }
}

/** Pill track: hatched outside zones, calm optimal zone, glowing marker gliding to the value. */
@Composable
fun ZoneTrackGauge(fraction: Float, zoneStart: Float?, zoneEnd: Float?, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(fraction)
    val breath = rememberBreath()
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val trackH = size.height * 0.46f
        val top = (size.height - trackH) / 2
        val track = Rect(0f, top, size.width, top + trackH)
        drawRoundRect(c.surfaceSunken, track.topLeft, track.size, CornerRadius(trackH / 2))
        hatch(track, c.outline, 9.dp.toPx())
        val zs = (zoneStart ?: 0f) * size.width; val ze = (zoneEnd ?: 1f) * size.width
        if (zoneStart != null || zoneEnd != null) {
            drawRoundRect(Brush.horizontalGradient(listOf(c.accentSoft, c.accentContainer, c.accentSoft), zs, ze), Offset(zs, top), Size(ze - zs, trackH), CornerRadius(trackH / 2))
            drawRoundRect(c.accent.copy(alpha = 0.55f), Offset(zs, top), Size(ze - zs, trackH), CornerRadius(trackH / 2), style = Stroke(1.dp.toPx()))
        }
        drawRoundRect(c.outline, track.topLeft, track.size, CornerRadius(trackH / 2), style = Stroke(1.dp.toPx()))
        val x = (fraction * reveal).coerceIn(0f, 1f) * size.width
        val r = size.height * 0.36f
        val cx = x.coerceIn(r, size.width - r); val cy = size.height / 2
        drawCircle(Brush.radialGradient(listOf(c.glow, Color.Transparent), Offset(cx, cy), r * (2.1f + 0.4f * breath)), r * (2.1f + 0.4f * breath), Offset(cx, cy))
        drawCircle(c.surfaceRaised, r, Offset(cx, cy))
        drawCircle(c.accent, r, Offset(cx, cy), style = Stroke(3.dp.toPx()))
        drawCircle(c.accent, r * 0.38f, Offset(cx, cy))
    }
}

/** Concentric chronometer rings: outer = [primary], inner = [secondary] (optional). Tick marks every 6°. */
@Composable
fun RingGauge(primary: Float, secondary: Float? = null, modifier: Modifier = Modifier, diameter: Dp = 96.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(primary)
    val breath = rememberBreath()
    Canvas(modifier.size(diameter).clearAndSetSemantics { }) {
        val stroke = size.minDimension * 0.085f
        val pad = stroke * 1.6f
        val d = size.minDimension - pad * 2
        val tl = Offset(pad, pad)
        for (i in 0 until 60) {
            val a = (i * 6 - 90) * PI / 180
            val r1 = size.minDimension / 2 - stroke * 0.2f; val r2 = r1 - stroke * (if (i % 5 == 0) 0.9f else 0.5f)
            val o = center
            drawLine(if (i / 60f <= primary * reveal) c.accent.copy(alpha = 0.7f) else c.outline, Offset(o.x + (r1 * cos(a)).toFloat(), o.y + (r1 * sin(a)).toFloat()),
                Offset(o.x + (r2 * cos(a)).toFloat(), o.y + (r2 * sin(a)).toFloat()), strokeWidth = 1.2.dp.toPx())
        }
        drawArc(c.surfaceSunken, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
        val sweep = 360f * primary * reveal
        drawArc(Brush.sweepGradient(listOf(c.accent, c.accentBright, c.accent), center), -90f, sweep, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        val end = (-90f + sweep) * PI / 180
        val ex = center.x + (d / 2 * cos(end)).toFloat(); val ey = center.y + (d / 2 * sin(end)).toFloat()
        drawCircle(Brush.radialGradient(listOf(c.glow, Color.Transparent), Offset(ex, ey), stroke * (2f + breath)), stroke * (2f + breath), Offset(ex, ey))
        if (secondary != null) {
            val d2 = d - stroke * 3.2f; val tl2 = Offset(center.x - d2 / 2, center.y - d2 / 2)
            drawArc(c.surfaceSunken, 0f, 360f, false, tl2, Size(d2, d2), style = Stroke(stroke * 0.7f))
            drawArc(c.accent.copy(alpha = 0.65f), -90f, 360f * secondary * reveal, false, tl2, Size(d2, d2), style = Stroke(stroke * 0.7f, cap = StrokeCap.Round))
        }
    }
}

/** Apothecary vial with a gently moving liquid surface; ticks at [marks] (fractions). */
@Composable
fun VialGauge(fraction: Float, marks: List<Float>, modifier: Modifier = Modifier, width: Dp = 44.dp, height: Dp = 112.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(fraction)
    val phase = if (MyHealthTheme.reduceMotion) 0f else {
        val tr = rememberInfiniteTransition(label = WAVE)
        tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = WAVE).value
    }
    Canvas(modifier.size(width, height).clearAndSetSemantics { }) {
        val capH = size.height * 0.10f
        val body = Rect(size.width * 0.14f, capH + 2.dp.toPx(), size.width * 0.86f, size.height)
        val r = body.width / 2
        val tube = Path().apply { addRoundRect(RoundRect(body, topLeft = CornerRadius(6.dp.toPx()), topRight = CornerRadius(6.dp.toPx()), bottomLeft = CornerRadius(r), bottomRight = CornerRadius(r))) }
        drawRoundRect(c.outlineStrong, Offset(size.width * 0.06f, 0f), Size(size.width * 0.88f, capH), CornerRadius(4.dp.toPx()))
        drawPath(tube, c.surfaceSunken.copy(alpha = 0.8f))
        val level = body.bottom - body.height * fraction * reveal
        clipPath(tube) {
            val wave = Path().apply {
                moveTo(body.left, level)
                var x = body.left
                while (x <= body.right) { lineTo(x, level + 2.5.dp.toPx() * sin((x / body.width) * 2 * PI.toFloat() * 1.5f + phase)); x += 2f }
                lineTo(body.right, body.bottom); lineTo(body.left, body.bottom); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(c.accentBright, c.accent), level, body.bottom))
            drawRect(Color.White.copy(alpha = 0.14f), Offset(body.left + body.width * 0.15f, body.top), Size(body.width * 0.14f, body.height))
        }
        drawPath(tube, c.outlineStrong, style = Stroke(1.5.dp.toPx()))
        marks.forEach { m ->
            val y = body.bottom - body.height * m
            drawLine(c.textTertiary, Offset(body.right - body.width * 0.35f, y), Offset(body.right, y), strokeWidth = 1.2.dp.toPx())
        }
    }
}

/** Twin rails with knobs for systolic/diastolic, joined by a balance beam; shaded target zone on each rail. */
@Composable
fun TwinRailGauge(top: Float, bottom: Float, topZone: Pair<Float, Float>, bottomZone: Pair<Float, Float>, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(top + bottom)
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val railW = 8.dp.toPx()
        val xs = listOf(size.width * 0.28f, size.width * 0.72f)
        val pad = 10.dp.toPx(); val h = size.height - pad * 2
        val knobs = mutableListOf<Offset>()
        listOf(top to topZone, bottom to bottomZone).forEachIndexed { i, (f, z) ->
            val x = xs[i]
            for (t in 0..10) { val y = pad + h * t / 10f; drawLine(c.outline, Offset(x - railW * (if (t % 5 == 0) 2.2f else 1.5f), y), Offset(x - railW, y), strokeWidth = 1.dp.toPx()) }
            drawRoundRect(c.surfaceSunken, Offset(x - railW / 2, pad), Size(railW, h), CornerRadius(railW / 2))
            val zy1 = pad + h * (1 - z.second); val zy2 = pad + h * (1 - z.first)
            drawRoundRect(c.accentContainer, Offset(x - railW / 2, zy1), Size(railW, zy2 - zy1), CornerRadius(railW / 2))
            drawRoundRect(c.outlineStrong, Offset(x - railW / 2, pad), Size(railW, h), CornerRadius(railW / 2), style = Stroke(1.dp.toPx()))
            val y = pad + h * (1 - (0.5f + (f - 0.5f) * reveal))
            knobs += Offset(x, y)
        }
        drawLine(Brush.linearGradient(listOf(c.accent, c.accentBright), knobs[0], knobs[1]), knobs[0], knobs[1], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        knobs.forEach { k ->
            drawCircle(Brush.radialGradient(listOf(c.glow, Color.Transparent), k, 22.dp.toPx()), 22.dp.toPx(), k)
            drawRoundRect(c.surfaceRaised, Offset(k.x - 13.dp.toPx(), k.y - 9.dp.toPx()), Size(26.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()))
            drawRoundRect(c.accent, Offset(k.x - 13.dp.toPx(), k.y - 9.dp.toPx()), Size(26.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
    }
}

/** Stepped horizon: each threshold raises the ground; a flag marks the value. */
@Composable
fun LadderGauge(fraction: Float, steps: List<Float>, modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(fraction)
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val edges = listOf(0f) + steps + listOf(1f)
        val levels = edges.size - 1
        for (i in 0 until levels) {
            val x1 = edges[i] * size.width; val x2 = edges[i + 1] * size.width
            val stepH = size.height * (0.30f + 0.22f * i)
            val rect = Rect(x1, size.height - stepH, x2, size.height)
            if (i == 0) drawRect(Brush.verticalGradient(listOf(c.accentContainer, c.accentSoft), rect.top, rect.bottom), rect.topLeft, rect.size)
            else { drawRect(c.surfaceSunken, rect.topLeft, rect.size); val p = Path().apply { addRect(rect) }; clipPath(p) { var x = x1 - rect.height; while (x < x2) { drawLine(c.outline, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), 1.2f); x += 8.dp.toPx() } } }
            drawLine(c.outlineStrong, Offset(x1, rect.top), Offset(x2, rect.top), strokeWidth = 1.5.dp.toPx())
            if (i > 0) drawLine(c.outlineStrong, Offset(x1, size.height - size.height * (0.30f + 0.22f * (i - 1))), Offset(x1, rect.top), strokeWidth = 1.5.dp.toPx())
        }
        val x = (fraction * reveal).coerceIn(0.02f, 0.98f) * size.width
        val idx = edges.indexOfLast { it * size.width <= x }.coerceIn(0, levels - 1)
        val groundY = size.height - size.height * (0.30f + 0.22f * idx)
        drawLine(c.accent, Offset(x, groundY), Offset(x, groundY - 14.dp.toPx()), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(c.glow, Color.Transparent), Offset(x, groundY - 14.dp.toPx()), 18.dp.toPx()), 18.dp.toPx(), Offset(x, groundY - 14.dp.toPx()))
        drawCircle(c.accent, 6.dp.toPx(), Offset(x, groundY - 16.dp.toPx()))
        drawCircle(c.surfaceRaised, 2.5.dp.toPx(), Offset(x, groundY - 16.dp.toPx()))
    }
}

/** Semicircular instrument dial with a needle and a highlighted healthy arc. */
@Composable
fun DialGauge(fraction: Float, bandStart: Float?, bandEnd: Float?, modifier: Modifier = Modifier, width: Dp = 140.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(fraction)
    Canvas(modifier.size(width, width * 0.6f).clearAndSetSemantics { }) {
        val stroke = size.width * 0.06f
        val d = size.width - stroke * 2
        val tl = Offset(stroke, stroke)
        drawArc(c.surfaceSunken, 180f, 180f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        if (bandStart != null && bandEnd != null) drawArc(Brush.sweepGradient(listOf(c.accent, c.accentBright, c.accent), Offset(size.width / 2, stroke + d / 2)),
            180f + 180f * bandStart, 180f * (bandEnd - bandStart), false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        val cx = size.width / 2; val cy = stroke + d / 2
        for (i in 0..20) {
            val a = (180 + i * 9) * PI / 180; val r1 = d / 2 - stroke; val r2 = r1 - (if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f)
            drawLine(c.textTertiary, Offset(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat()), Offset(cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat()), strokeWidth = 1.dp.toPx())
        }
        rotate(180f * (fraction * reveal) - 90f, Offset(cx, cy)) {
            drawLine(c.accent, Offset(cx, cy), Offset(cx, cy - d / 2 + stroke * 2.2f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(c.glow, stroke * 1.4f, Offset(cx, cy))
        drawCircle(c.accent, stroke * 0.7f, Offset(cx, cy))
    }
}

/** Smooth bezier sparkline with a soft glow fill; the last point is emphasised. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, height: Dp = 36.dp) {
    val c = MyHealthTheme.colors
    val reveal = rememberReveal(values.size)
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        if (values.size < 2) return@Canvas
        val mn = values.min(); val mx = values.max(); val span = (mx - mn).takeIf { it > 1e-9 } ?: 1.0
        val pad = 4.dp.toPx()
        val pts = values.mapIndexed { i, v -> Offset(pad + (size.width - 2 * pad) * i / (values.size - 1), pad + (size.height - 2 * pad) * (1 - ((v - mn) / span).toFloat())) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) { val a = pts[i - 1]; val b = pts[i]; val mx2 = (a.x + b.x) / 2; cubicTo(mx2, a.y, mx2, b.y, b.x, b.y) }
        }
        val fill = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
        val visibleW = size.width * reveal
        clipRect(right = visibleW) {
            drawPath(fill, Brush.verticalGradient(listOf(c.glow, Color.Transparent)))
            drawPath(line, Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.6f), c.accentBright)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (reveal > 0.98f) { drawCircle(c.glow, 7.dp.toPx(), pts.last()); drawCircle(c.accent, 3.5.dp.toPx(), pts.last()) }
    }
}

