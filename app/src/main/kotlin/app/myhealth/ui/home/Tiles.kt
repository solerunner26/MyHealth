package app.myhealth.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myhealth.R
import app.myhealth.core.designsystem.components.DialGauge
import app.myhealth.core.designsystem.components.Emphasis
import app.myhealth.core.designsystem.components.LadderGauge
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.RingGauge
import app.myhealth.core.designsystem.components.Sparkline
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.core.designsystem.components.TwinRailGauge
import app.myhealth.core.designsystem.components.VialGauge
import app.myhealth.core.designsystem.components.ZoneTrackGauge
import app.myhealth.core.designsystem.components.appear
import app.myhealth.core.designsystem.components.gaugeFraction
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.AnalyteTrend
import app.myhealth.domain.analytics.VitalBand
import app.myhealth.domain.analytics.VitalRules
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.kind
import app.myhealth.ui.common.label
import java.time.ZoneId

/** Which instrument a test is drawn with. */
enum class TestGauge { Ladder, Vial, Track }

fun gaugeFor(analyteId: String): TestGauge = when (analyteId) {
    "hba1c", "fbs", "ppbs", "rbs", "eag" -> TestGauge.Ladder
    "vitd", "b12", "folate", "ferritin", "iron", "tsat" -> TestGauge.Vial
    else -> TestGauge.Track
}

/** Clinical category thresholds (scale min, step edges…, scale max) for the ladder tests. */
fun ladderScale(analyteId: String): List<Double> = when (analyteId) {
    "hba1c" -> listOf(4.0, 5.7, 6.5, 9.0)
    "fbs" -> listOf(60.0, 100.0, 126.0, 200.0)
    "eag" -> listOf(60.0, 117.0, 154.0, 250.0)
    else -> listOf(60.0, 140.0, 200.0, 300.0)
}

/** Shared tile frame: title, status, instrument, big value, change line. Same size and rhythm for every tile. */
@Composable
fun GaugeTile(
    title: String,
    value: String,
    unit: String,
    status: Pair<String, StatusKind>?,
    footer: String,
    onClick: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
    gauge: @Composable () -> Unit,
) {
    val c = MyHealthTheme.colors
    val cd = stringResource(R.string.dash_tile_cd, title, listOf(value, unit).filter { it.isNotBlank() }.joinToString(" "), status?.first ?: "", footer)
    // Out of range = a different MATERIAL (hero fill + breathing glow rim), slightly off = tinted container with an
    // accent edge, in range = the plain card. Checked in greyscale too: material, icon and label all change.
    val k = status?.second
    val outOfRange = k == StatusKind.Low || k == StatusKind.High || k == StatusKind.Critical
    val slightly = k == StatusKind.Attention || k == StatusKind.Worsening
    MyHealthCard(modifier.fillMaxHeight().appear(index).semantics(mergeDescendants = true) { contentDescription = cd }, onClick = onClick,
        emphasis = if (slightly) Emphasis.Medium else Emphasis.Subtle, alert = outOfRange) {
        val c = MyHealthTheme.colors
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = MyHealthTheme.typography.overline, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (status != null) Icon(status.second.icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(vertical = MyHealthTheme.spacing.sm), contentAlignment = Alignment.Center) { gauge() }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(value, style = MyHealthTheme.typography.metric.copy(color = c.textPrimary), maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = MyHealthTheme.typography.metric.fontSize), modifier = Modifier.weight(1f, fill = false))
            if (unit.isNotBlank()) Text(unit, style = MyHealthTheme.typography.caption, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        if (status != null) Box(Modifier.padding(top = 4.dp)) { MyHealthStatusBadge(status.first, if (outOfRange) StatusKind.Critical else status.second) }
        Text(footer, style = MyHealthTheme.typography.caption, color = c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Two tiles side by side with equal heights (no overlap at any font scale: the row grows instead). */
@Composable
fun TileRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.md)) { content(Modifier.weight(1f)) }
}

@Composable
private fun changeLine(delta: Double?, decimals: Int, unit: String, prevDate: java.time.LocalDate?): String =
    if (delta == null || prevDate == null) stringResource(R.string.dash_no_previous)
    else stringResource(R.string.dash_since, ValueFormat.signed(delta, decimals) + if (unit.isBlank()) "" else " $unit", formatDate(prevDate))

@Composable
fun TestTile(t: AnalyteTrend, onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    val a = t.analyte; val v = t.latest.value
    val unit = a.canonicalUnit.takeIf { it != "ratio" }.orEmpty()
    GaugeTile(analyteName(a.id), ValueFormat.withComparator(v, a.decimals, t.latest.comparator), unit, label(t.latest.status) to t.latest.status.kind(),
        changeLine(t.absoluteChange, a.decimals, unit, t.previous?.date), onClick, index, modifier.testTag("tile_${a.id}")) {
        when (gaugeFor(a.id)) {
            TestGauge.Ladder -> {
                val s = ladderScale(a.id)
                LadderGauge(gaugeFraction(v, s.first(), s.last()), s.drop(1).dropLast(1).map { gaugeFraction(it, s.first(), s.last()) })
            }
            TestGauge.Vial -> {
                val top = maxOf((t.bandHigh ?: v) * 1.25, v * 1.1, (t.bandLow ?: 0.0) * 2.5)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VialGauge(gaugeFraction(v, 0.0, top), listOfNotNull(t.bandLow, t.bandHigh).map { gaugeFraction(it, 0.0, top) })
                    Spacer(Modifier.width(MyHealthTheme.spacing.md))
                    Sparkline(t.points.map { it.value }, Modifier.width(72.dp), height = 48.dp)
                }
            }
            TestGauge.Track -> Column {
                val lo = t.bandLow; val hi = t.bandHigh
                val span = if (lo != null && hi != null) hi - lo else (hi ?: lo ?: v).coerceAtLeast(1e-6) * 0.5
                val min = (lo ?: (hi ?: v) - span * 2) - span * 0.6; val max = (hi ?: (lo ?: v) + span * 2) + span * 0.6
                ZoneTrackGauge(gaugeFraction(v, min, max), lo?.let { gaugeFraction(it, min, max) }, hi?.let { gaugeFraction(it, min, max) })
                Sparkline(t.points.map { it.value }, Modifier.padding(top = 6.dp), height = 34.dp)
            }
        }
    }
}

@Composable
fun VitalTile(type: VitalType, latest: Vital, previous: Vital?, heightCm: Double?, zone: ZoneId, onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    val band = VitalRules.band(latest)
    val status = if (band == VitalBand.Unknown) null else label(band) to band.kind()
    val unit = latest.unit
    val value = app.myhealth.ui.common.vitalValue(type, latest.value, latest.value2, "")
    val dec = if (type in setOf(VitalType.BloodPressure, VitalType.Pulse, VitalType.SpO2, VitalType.RespiratoryRate, VitalType.BloodGlucose)) 0 else 1
    val footer = changeLine(previous?.let { latest.value - it.value }, dec, unit, previous?.measuredAt?.atZone(zone)?.toLocalDate())
    GaugeTile(label(type), value, unit, status, footer, onClick, index, modifier.testTag("vtile_${type.name}")) {
        when (type) {
            VitalType.BloodPressure -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TwinRailGauge(gaugeFraction(latest.value, 80.0, 200.0), gaugeFraction(latest.value2 ?: 0.0, 40.0, 130.0),
                    gaugeFraction(90.0, 80.0, 200.0) to gaugeFraction(129.0, 80.0, 200.0), gaugeFraction(60.0, 40.0, 130.0) to gaugeFraction(79.0, 40.0, 130.0), height = 96.dp)
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceAround) {
                    Text(stringResource(R.string.dash_sys), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textTertiary)
                    Text(stringResource(R.string.dash_dia), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textTertiary)
                }
            }
            VitalType.Pulse -> RingGauge(gaugeFraction(latest.value, 30.0, 150.0))
            VitalType.SpO2 -> RingGauge(gaugeFraction(latest.value, 80.0, 100.0), secondary = 0.95f)
            VitalType.Weight, VitalType.Bmi -> {
                val bmi = if (type == VitalType.Bmi) latest.value else heightCm?.let { VitalRules.bmi(latest.value, it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DialGauge(gaugeFraction(bmi ?: 22.0, 14.0, 40.0), gaugeFraction(18.5, 14.0, 40.0), gaugeFraction(24.9, 14.0, 40.0), width = 128.dp)
                    if (type == VitalType.Weight && bmi != null) Text(stringResource(R.string.dash_bmi, ValueFormat.number(bmi, 1)), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                }
            }
            VitalType.BloodGlucose -> LadderGauge(gaugeFraction(latest.value, 60.0, 300.0), listOf(140.0, 200.0).map { gaugeFraction(it, 60.0, 300.0) })
            else -> {
                val r = VitalRules.normalRange(type) ?: (latest.value * 0.8 to latest.value * 1.2)
                val span = r.second - r.first
                ZoneTrackGauge(gaugeFraction(latest.value, r.first - span, r.second + span), 1f / 3f, 2f / 3f)
            }
        }
    }
}

