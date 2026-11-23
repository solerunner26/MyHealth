package app.myhealth.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.myhealth.core.designsystem.theme.MyHealthTheme

enum class NeonStatus { Normal, High, Low }

/**
 * The owner's VitalTile spec, in the selected theme:
 * - 1.5 dp border with two opposing "comets" chasing round it (6 s per lap when normal, 3.5 s when out of range), drawn
 *   in the draw phase only (no recomposition per frame); [pauseMotion] (Doctor View) or reduced motion → a crisp
 *   static 1.5 dp border in the status colour.
 * - Row 1: test name (16 sp SemiBold, max 2 lines) + status pill (OK / HIGH ↑ / LOW ↓, 11 sp bold, tinted 16 %, border 40 %).
 * - Row 2: value (32 sp Bold, tabular figures, -0.02 em) + baseline unit (14 sp) + 30 dp icon in the status colour with a glow.
 * - Row 3: 4 dp track with a 10 dp glowing marker at [position] (already clamped to 0.05..0.95) and a "Ref:" caption.
 * Normal uses the theme accent; HIGH/LOW use the out-of-range red. Press: scale 0.97 in 100 ms, spring back.
 */
@Composable
fun NeonVitalTile(
    name: String,
    value: String,
    unit: String,
    status: NeonStatus,
    statusLabel: String,
    position: Float,
    refCaption: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pauseMotion: Boolean = false,
) {
    val c = MyHealthTheme.colors
    val alert = status != NeonStatus.Normal
    val accent = if (alert) c.danger else if (c.isDark) c.accentBright else c.accent
    val still = pauseMotion || MyHealthTheme.reduceMotion
    val angle = if (still) null else rememberInfiniteTransition(label = BEAM).animateFloat(
        0f, 360f, infiniteRepeatable(tween(if (alert) 3500 else 6000, easing = LinearEasing), RepeatMode.Restart), label = BEAM,
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, if (pressed) tween(100) else spring(dampingRatio = 1f, stiffness = 300f), label = PRESS_T)
    val radius = 20.dp; val border = 1.5.dp
    val outer = RoundedCornerShape(radius); val inner = RoundedCornerShape(radius - border)
    val resting = if (c.isDark) Color.White.copy(alpha = 0.08f) else c.outline
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(outer)
            .background(resting)
            .drawWithCache {
                val sweep = Brush.sweepGradient(
                    0.00f to Color.Transparent, 0.15f to Color.Transparent, 0.24f to accent, 0.27f to Color.Transparent,
                    0.65f to Color.Transparent, 0.74f to accent, 0.77f to Color.Transparent, 1.00f to Color.Transparent,
                )
                onDrawBehind {
                    val a = angle?.value
                    if (a == null) drawRoundRect(accent.copy(alpha = 0.75f), cornerRadius = CornerRadius(radius.toPx()))
                    else rotate(a) { drawCircle(sweep, radius = size.maxDimension) }
                }
            }
            .padding(border)
            .clip(inner)
            .background(if (c.isDark) c.surfaceRaised else Color.White)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // long names shrink (down to 12 sp) before they would be cut off
                BasicText(name, style = TextStyle(fontFamily = MyHealthTheme.typography.title.fontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, color = c.textPrimary),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 0.5.sp), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(statusLabel, style = TextStyle(fontFamily = MyHealthTheme.typography.label.fontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
                    color = accent, maxLines = 1,
                    modifier = Modifier.clip(MyHealthTheme.radii.pillShape).background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.40f), MyHealthTheme.radii.pillShape).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    BasicText(value, style = TextStyle(fontFamily = MyHealthTheme.typography.metric.fontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em, lineHeight = 34.sp, fontFeatureSettings = "tnum", color = c.textPrimary), maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 32.sp), modifier = Modifier.weight(1f, fill = false))
                    if (unit.isNotBlank()) Text(unit, style = TextStyle(fontSize = 14.sp), color = c.textSecondary, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent))))
                    Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val track = if (c.isDark) Color.White.copy(alpha = 0.10f) else c.surfaceSunken
                val zone = (if (c.isDark) c.accentBright else c.accent).copy(alpha = 0.22f)
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val y = size.height / 2; val h = 4.dp.toPx()
                    drawRoundRect(track, Offset(0f, y - h / 2), androidx.compose.ui.geometry.Size(size.width, h), CornerRadius(h / 2))
                    // reference interval = middle half of the track
                    drawRoundRect(zone, Offset(size.width * 0.25f, y - h / 2), androidx.compose.ui.geometry.Size(size.width * 0.5f, h), CornerRadius(h / 2))
                    val x = size.width * position.coerceIn(0.05f, 0.95f)
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), Offset(x, y), 10.dp.toPx()), 10.dp.toPx(), Offset(x, y))
                    drawCircle(accent, 5.dp.toPx(), Offset(x, y))
                }
                if (refCaption != null) Text(refCaption, style = MyHealthTheme.typography.caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Grid header: title, the triage pill ("X Attention • Y Normal") and the Doctor View toggle (out-of-range first, still
 * borders).
 */
@Composable
fun TileGridHeader(title: String, triage: String, doctorLabel: String, doctorView: Boolean, onDoctorView: (Boolean) -> Unit, modifier: Modifier = Modifier, attention: Boolean = false) {
    val c = MyHealthTheme.colors
    Column(modifier.fillMaxWidth().padding(top = MyHealthTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MyHealthTheme.typography.title, color = c.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = if (attention) c.danger else c.accent
            Text(triage, style = MyHealthTheme.typography.label, color = tint, maxLines = 1,
                modifier = Modifier.clip(MyHealthTheme.radii.pillShape).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.4f), MyHealthTheme.radii.pillShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            // one toggle for TalkBack: the label and the switch are a single control
            Row(Modifier.clip(MyHealthTheme.radii.pillShape).toggleable(doctorView, role = Role.Switch, onValueChange = onDoctorView).padding(start = 8.dp).testTag("doctorView"),
                verticalAlignment = Alignment.CenterVertically) {
                Text(doctorLabel, style = MyHealthTheme.typography.label, color = c.textSecondary)
                androidx.compose.material3.Switch(doctorView, null, Modifier.padding(start = 6.dp), colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, uncheckedThumbColor = c.outlineStrong, uncheckedTrackColor = c.surfaceSunken))
            }
        }
    }
}

private const val BEAM = "beam" // animation inspector id, not UI copy
private const val PRESS_T = "pressTile" // animation inspector id, not UI copy
