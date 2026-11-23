package app.myhealth.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Science
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

/** How urgent a status is, for ordering: not-in-range first, then "slightly off", then in range. */
fun StatusKind?.urgency(): Int = when (this) {
    StatusKind.Critical -> 0; StatusKind.High, StatusKind.Low -> 1; StatusKind.Attention, StatusKind.Worsening -> 2; else -> 3
}

fun urgencyOf(k: StatusKind?): Int = k.urgency()

/**
 * Compact tile frame (three per row): name, small instrument, value, status pill. Not in range = red-tinted tile
 * with a red edge that breathes and a red High/Low pill; slightly off = tinted tile; in range = plain card.
 * The change since last time is in the spoken description and on the detail screen.
 */
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
    note: String? = null,
    gauge: @Composable () -> Unit,
) {
    val cd = stringResource(R.string.dash_tile_cd, title, listOf(value, unit).filter { it.isNotBlank() }.joinToString(" "), status?.first ?: "", footer)
    val k = status?.second
    // anything outside the normal range (including "slightly high") is red; trend-only warnings stay tinted
    val outOfRange = k.urgency() <= 1 || k == StatusKind.Attention
    val slightly = k == StatusKind.Worsening
    MyHealthCard(modifier.fillMaxHeight().appear(index).semantics(mergeDescendants = true) { contentDescription = cd }, onClick = onClick,
        emphasis = if (slightly) Emphasis.Medium else Emphasis.Subtle, alert = outOfRange, padding = MyHealthTheme.spacing.md) {
        val c = MyHealthTheme.colors
        Row(verticalAlignment = Alignment.Top) {
            // names shrink to fit instead of breaking mid-word
            BasicText(title.uppercase(), style = MyHealthTheme.typography.overline.copy(color = c.textSecondary), maxLines = if (title.trim().contains(' ')) 2 else 1, overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MyHealthTheme.typography.overline.fontSize, stepSize = 0.5.sp), modifier = Modifier.weight(1f))
            if (status != null) Icon(status.second.icon, null, tint = if (outOfRange) c.danger else c.accent, modifier = Modifier.size(14.dp))
        }
        Box(Modifier.fillMaxWidth().height(60.dp).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { gauge() }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(value, style = MyHealthTheme.typography.metric.copy(color = c.textPrimary, fontSize = 24.sp), maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 24.sp), modifier = Modifier.weight(1f, fill = false))
            if (unit.isNotBlank()) Text(unit, style = MyHealthTheme.typography.caption.copy(fontSize = 10.sp), color = c.textSecondary, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        if (status != null) Box(Modifier.padding(top = 4.dp)) { MyHealthStatusBadge(status.first, if (outOfRange && k == StatusKind.Attention) StatusKind.High else status.second, compact = true) }
        else if (note != null) Text(note, style = MyHealthTheme.typography.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Tiles three across with equal heights (no overlap at any font scale: the row grows instead). */
const val TILE_COLUMNS = 3

@Composable
fun TileRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) { content(Modifier.weight(1f)) }
}

/** An invitation tile for a vital with no reading yet: dashed-feel quiet card, "+ Log". */
@Composable
fun EmptyVitalTile(type: VitalType, onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val title = label(type)
    val add = stringResource(R.string.dash_log_short)
    val cd = stringResource(R.string.join_comma, title, add)
    MyHealthCard(modifier.fillMaxHeight().appear(index).semantics(mergeDescendants = true) { contentDescription = cd }.testTag("vempty_${type.name}"),
        onClick = onClick, padding = MyHealthTheme.spacing.md) {
        Text(title.uppercase(), style = MyHealthTheme.typography.overline, color = c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            Icon(app.myhealth.core.designsystem.components.MyHealthIcons.Add, null, tint = c.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(add, style = MyHealthTheme.typography.label, color = c.accent, maxLines = 1)
    }
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
                LadderGauge(gaugeFraction(v, s.first(), s.last()), s.drop(1).dropLast(1).map { gaugeFraction(it, s.first(), s.last()) }, height = 48.dp)
            }
            TestGauge.Vial -> {
                val top = maxOf((t.bandHigh ?: v) * 1.25, v * 1.1, (t.bandLow ?: 0.0) * 2.5)
                VialGauge(gaugeFraction(v, 0.0, top), listOfNotNull(t.bandLow, t.bandHigh).map { gaugeFraction(it, 0.0, top) }, width = 22.dp, height = 52.dp)
            }
            TestGauge.Track -> Column {
                val lo = t.bandLow; val hi = t.bandHigh
                val span = if (lo != null && hi != null) hi - lo else (hi ?: lo ?: v).coerceAtLeast(1e-6) * 0.5
                val min = (lo ?: (hi ?: v) - span * 2) - span * 0.6; val max = (hi ?: (lo ?: v) + span * 2) + span * 0.6
                ZoneTrackGauge(gaugeFraction(v, min, max), lo?.let { gaugeFraction(it, min, max) }, hi?.let { gaugeFraction(it, min, max) }, height = 22.dp)
                Sparkline(t.points.map { it.value }, Modifier.padding(top = 4.dp), height = 24.dp)
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
    val bmiNote = if (type == VitalType.Weight) heightCm?.let { VitalRules.bmi(latest.value, it) }?.let { stringResource(R.string.dash_bmi, ValueFormat.number(it, 1)) } else null
    GaugeTile(label(type), value, unit, status, footer, onClick, index, modifier.testTag("vtile_${type.name}"), note = bmiNote) {
        when (type) {
            VitalType.BloodPressure -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TwinRailGauge(gaugeFraction(latest.value, 80.0, 200.0), gaugeFraction(latest.value2 ?: 0.0, 40.0, 130.0),
                    gaugeFraction(90.0, 80.0, 200.0) to gaugeFraction(129.0, 80.0, 200.0), gaugeFraction(60.0, 40.0, 130.0) to gaugeFraction(79.0, 40.0, 130.0), height = 52.dp)
            }
            VitalType.Pulse -> RingGauge(gaugeFraction(latest.value, 30.0, 150.0), diameter = 52.dp)
            VitalType.SpO2 -> RingGauge(gaugeFraction(latest.value, 80.0, 100.0), secondary = 0.95f, diameter = 52.dp)
            VitalType.Weight, VitalType.Bmi -> {
                val bmi = if (type == VitalType.Bmi) latest.value else heightCm?.let { VitalRules.bmi(latest.value, it) }
                DialGauge(gaugeFraction(bmi ?: 22.0, 14.0, 40.0), gaugeFraction(18.5, 14.0, 40.0), gaugeFraction(24.9, 14.0, 40.0), width = 72.dp)
            }
            VitalType.BloodGlucose -> LadderGauge(gaugeFraction(latest.value, 60.0, 300.0), listOf(140.0, 200.0).map { gaugeFraction(it, 60.0, 300.0) }, height = 48.dp)
            else -> {
                val r = VitalRules.normalRange(type) ?: (latest.value * 0.8 to latest.value * 1.2)
                val span = r.second - r.first
                ZoneTrackGauge(gaugeFraction(latest.value, r.first - span, r.second + span), 1f / 3f, 2f / 3f, height = 22.dp)
            }
        }
    }
}



// ===================================================================== neon tiles (owner's VitalTile spec)

fun tileIcon(i: app.myhealth.domain.analytics.TileIcon): androidx.compose.ui.graphics.vector.ImageVector = when (i) {
    app.myhealth.domain.analytics.TileIcon.Blood -> androidx.compose.material.icons.Icons.Outlined.Bloodtype
    app.myhealth.domain.analytics.TileIcon.Sugar -> androidx.compose.material.icons.Icons.Outlined.WaterDrop
    app.myhealth.domain.analytics.TileIcon.Heart -> androidx.compose.material.icons.Icons.Outlined.FavoriteBorder
    app.myhealth.domain.analytics.TileIcon.Kidney -> androidx.compose.material.icons.Icons.Outlined.Opacity
    app.myhealth.domain.analytics.TileIcon.Liver -> androidx.compose.material.icons.Icons.Outlined.Spa
    app.myhealth.domain.analytics.TileIcon.Thyroid -> androidx.compose.material.icons.Icons.Outlined.Biotech
    app.myhealth.domain.analytics.TileIcon.Vitamin -> androidx.compose.material.icons.Icons.Outlined.WbSunny
    app.myhealth.domain.analytics.TileIcon.Lungs -> androidx.compose.material.icons.Icons.Outlined.Air
    app.myhealth.domain.analytics.TileIcon.Temperature -> androidx.compose.material.icons.Icons.Outlined.Thermostat
    app.myhealth.domain.analytics.TileIcon.Weight -> androidx.compose.material.icons.Icons.Outlined.MonitorWeight
    app.myhealth.domain.analytics.TileIcon.Pulse -> androidx.compose.material.icons.Icons.Outlined.MonitorHeart
    app.myhealth.domain.analytics.TileIcon.Oxygen -> androidx.compose.material.icons.Icons.Outlined.Air
    app.myhealth.domain.analytics.TileIcon.Lab -> androidx.compose.material.icons.Icons.Outlined.Science
}

@Composable
fun tileName(t: app.myhealth.domain.analytics.VitalTileData): String = t.analyteId?.let { analyteName(it) } ?: t.vitalType?.let { label(it) } ?: t.key

@Composable
fun NeonTile(t: app.myhealth.domain.analytics.VitalTileData, doctorView: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = tileName(t)
    val value = if (t.value2 != null) ValueFormat.number(t.value, 0) + "/" + ValueFormat.number(t.value2!!, 0) else ValueFormat.number(t.value, t.decimals)
    val label = stringResource(when (t.status) { app.myhealth.domain.analytics.VitalStatus.NORMAL -> R.string.tile_ok; app.myhealth.domain.analytics.VitalStatus.HIGH -> R.string.tile_high; else -> R.string.tile_low })
    val range = ValueFormat.range(t.refLow, t.refHigh, t.decimals)
    val ref = range.takeIf { it.isNotBlank() }?.let { stringResource(R.string.tile_ref, listOf(it, t.unit).filter { s -> s.isNotBlank() }.joinToString(" ")) }
    val cd = stringResource(R.string.tile_cd, name, value, t.unit, label)
    app.myhealth.core.designsystem.components.NeonVitalTile(
        name, value, t.unit,
        when (t.status) { app.myhealth.domain.analytics.VitalStatus.NORMAL -> app.myhealth.core.designsystem.components.NeonStatus.Normal; app.myhealth.domain.analytics.VitalStatus.HIGH -> app.myhealth.core.designsystem.components.NeonStatus.High; else -> app.myhealth.core.designsystem.components.NeonStatus.Low },
        label, t.position, ref, tileIcon(t.icon), onClick,
        modifier.semantics(mergeDescendants = true) { contentDescription = cd }, pauseMotion = doctorView,
    )
}

/** Two columns on phones, four on tablets; Doctor View puts HIGH/LOW first and stills the borders. */
fun androidx.compose.foundation.lazy.LazyListScope.neonGrid(
    tiles: List<app.myhealth.domain.analytics.VitalTileData>, columns: Int, doctorView: Boolean, onOpen: (app.myhealth.domain.analytics.VitalTileData) -> Unit,
) {
    val ordered = if (doctorView) app.myhealth.domain.analytics.VitalTiles.doctorOrder(tiles) else tiles
    items(ordered.chunked(columns).size) { r ->
        val row = ordered.chunked(columns)[r]
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { t ->
                val tag = t.analyteId?.let { "tile_$it" } ?: "vital_${t.vitalType?.name}"
                NeonTile(t, doctorView, { onOpen(t) }, Modifier.weight(1f).fillMaxHeight().testTag(tag))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun NeonGridHeader(title: String, tiles: List<app.myhealth.domain.analytics.VitalTileData>, doctorView: Boolean, onDoctorView: (Boolean) -> Unit) {
    val (attention, normal) = app.myhealth.domain.analytics.VitalTiles.triage(tiles)
    app.myhealth.core.designsystem.components.TileGridHeader(title, stringResource(R.string.tile_triage, attention, normal), stringResource(R.string.tile_doctor_view),
        doctorView, onDoctorView, Modifier.testTag("gridHeader"), attention = attention > 0)
}
