package app.myhealth.ui.vitals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.Safe
import app.myhealth.core.designsystem.components.ChartPoint
import app.myhealth.core.designsystem.components.ChartSeries
import app.myhealth.core.designsystem.components.MyHealthBandChart
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthDateField
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIconButton
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthMetricTile
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTimeField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.EntryUnit
import app.myhealth.domain.analytics.VitalEntry
import app.myhealth.domain.analytics.VitalEntryError
import app.myhealth.domain.analytics.VitalRules
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalSource
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.util.IdGenerator
import app.myhealth.domain.util.UuidIds
import app.myhealth.ui.Session
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.formatTime
import app.myhealth.ui.common.kind
import app.myhealth.ui.common.label
import app.myhealth.ui.common.vitalValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Latest reading per type, BMI derived from weight + profile height when not recorded. */
data class VitalsState(val loading: Boolean = true, val member: FamilyMember? = null, val vitals: List<Vital> = emptyList(), val activity: List<app.myhealth.domain.model.DailyActivity> = emptyList()) {
    /** Average daily steps over the last 7 complete days (Health Connect), null when not enough data. */
    fun stepsAverage(today: LocalDate): Double? = activity.filter { it.steps != null && it.date.isBefore(today) && !it.date.isBefore(today.minusDays(7)) }
        .takeIf { it.size >= 3 }?.mapNotNull { it.steps }?.average()
    fun latest(t: VitalType): Vital? = vitals.filter { it.type == t }.maxByOrNull { it.measuredAt } ?: if (t == VitalType.Bmi) derivedBmi().lastOrNull() else null
    fun series(t: VitalType): List<Vital> = vitals.filter { it.type == t }.sortedBy { it.measuredAt }.ifEmpty { if (t == VitalType.Bmi) derivedBmi() else emptyList() }
    fun derivedBmi(): List<Vital> {
        val h = member?.heightCm ?: vitals.filter { it.type == VitalType.Height }.maxByOrNull { it.measuredAt }?.value ?: return emptyList()
        return vitals.filter { it.type == VitalType.Weight }.sortedBy { it.measuredAt }.mapNotNull { w -> VitalRules.bmi(w.value, h)?.let { w.copy(type = VitalType.Bmi, value = it, unit = VitalType.Bmi.canonicalUnit, id = "bmi-" + w.id) } }
    }
    val typesWithData get() = VitalType.entries.filter { latest(it) != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VitalsViewModel(private val session: Session) : ViewModel() {
    val state: StateFlow<VitalsState> = session.activeMember.filterNotNull().flatMapLatest { m ->
        kotlinx.coroutines.flow.combine(session.stack.vitals.observe(m.id), session.stack.activity.observe(m.id)) { v, a -> VitalsState(false, m, v, a) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VitalsState())

    fun delete(id: String) = viewModelScope.launch(Safe.handler) { runCatching { session.stack.vitals.delete(id) } }
}

/** Section shown at the top of the Trends tab. */
@Composable
fun VitalsSection(state: VitalsState, onAdd: () -> Unit, onOpen: (VitalType) -> Unit, zone: ZoneId) {
    val sp = MyHealthTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.sm), modifier = Modifier.testTag("vitalsSection")) {
        MyHealthSectionHeader(stringResource(R.string.vitals_title))
        val types = state.typesWithData
        if (types.isEmpty()) {
            MyHealthCard {
                Text(stringResource(R.string.vitals_empty_title), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                Text(stringResource(R.string.vitals_empty_body), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
            }
        }
        types.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                pair.forEach { t ->
                    val v = state.latest(t)!!
                    val band = VitalRules.band(v)
                    MyHealthMetricTile(label(t), vitalValue(t, v.value, v.value2, ""), Modifier.weight(1f).testTag("vital_${t.name}"), unit = v.unit.ifBlank { null },
                        caption = formatDate(v.measuredAt.atZone(zone).toLocalDate()), status = if (band == app.myhealth.domain.analytics.VitalBand.Unknown) null else label(band) to band.kind(),
                        onClick = { onOpen(t) })
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        state.stepsAverage(LocalDate.now())?.let { avg ->
            MyHealthMetricTile(stringResource(R.string.activity_title), ValueFormat.number(avg, 0), Modifier.fillMaxWidth().testTag("activityTile"),
                caption = stringResource(R.string.activity_steps_avg, ValueFormat.number(avg, 0)))
        }
        MyHealthSecondaryButton(stringResource(R.string.vitals_add), onAdd, Modifier.fillMaxWidth().testTag("logVital"), icon = MyHealthIcons.Add)
    }
}

@Composable
fun VitalDetailScreen(state: VitalsState, type: VitalType, zone: ZoneId, onBack: () -> Unit, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    val sp = MyHealthTheme.spacing
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().testTag("vitalDetail")) {
        MyHealthTopBar(label(type), onBack = onBack, backLabel = stringResource(R.string.action_back)) { MyHealthIconButton(MyHealthIcons.Add, stringResource(R.string.vitals_add), onAdd) }
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        val series = state.series(type)
        if (series.isEmpty()) { MyHealthEmptyState(MyHealthIcons.Vitals, stringResource(R.string.vitals_empty_title), stringResource(R.string.vitals_empty_body), actionLabel = stringResource(R.string.vitals_add), onAction = onAdd); return@Column }
        val band = VitalRules.normalRange(type)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            item {
                MyHealthCard {
                    val latest = series.last()
                    val x = { v: Vital -> v.measuredAt.toEpochMilli() / 86_400_000.0 }
                    val s1 = ChartSeries(series.map { ChartPoint(x(it), it.value, VitalRules.band(it).let { b -> b != app.myhealth.domain.analytics.VitalBand.Normal && b != app.myhealth.domain.analytics.VitalBand.Unknown }) })
                    val s2 = if (type.paired) ChartSeries(series.mapNotNull { v -> v.value2?.let { ChartPoint(x(v), it, it >= 80 || it < 60) } }, dashed = true) else null
                    MyHealthBandChart(listOfNotNull(s1, s2), stringResource(R.string.vital_chart_cd, label(type), series.size, vitalValue(type, latest.value, latest.value2, latest.unit)),
                        bandLow = band?.first, bandHigh = band?.second, yLabelTop = latest.unit,
                        xLabelStart = formatDate(series.first().measuredAt.atZone(zone).toLocalDate()), xLabelEnd = formatDate(latest.measuredAt.atZone(zone).toLocalDate()), modifier = Modifier.testTag("vitalChart"))
                    if (type.paired) Text(stringResource(R.string.join_dot, stringResource(R.string.legend_systolic), stringResource(R.string.legend_diastolic)), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                    band?.let { Text(stringResource(R.string.vital_typical, ValueFormat.range(it.first, it.second, 1)), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary) }
                }
            }
            item { MyHealthSectionHeader(stringResource(R.string.trend_history)) }
            items(series.reversed(), key = { it.id }) { v ->
                val at = v.measuredAt.atZone(zone)
                val b = VitalRules.band(v)
                val src = when (v.source) { VitalSource.Manual -> R.string.vital_source_manual; VitalSource.Report -> R.string.vital_source_report; VitalSource.HealthConnect -> R.string.vital_source_hc }
                MyHealthCard {
                    MyHealthListRow(vitalValue(type, v.value, v.value2, v.unit),
                        subtitle = listOf(stringResource(R.string.join_dot, formatDate(at.toLocalDate()), formatTime(at.toLocalTime())),
                            if (v.id.startsWith("bmi-")) stringResource(R.string.vital_bmi_derived) else stringResource(src)).joinToString("\n") + (v.note?.let { "\n$it" } ?: "")) {
                        if (b != app.myhealth.domain.analytics.VitalBand.Unknown) MyHealthStatusBadge(label(b), b.kind())
                        if (!v.id.startsWith("bmi-")) MyHealthIconButton(MyHealthIcons.Delete, stringResource(R.string.vital_delete), { confirm = v.id })
                    }
                }
            }
            item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
        }
    }
    confirm?.let { id -> MyHealthDialog(stringResource(R.string.vital_delete_title), stringResource(R.string.vital_delete_body), stringResource(R.string.action_delete),
        { onDelete(id); confirm = null }, stringResource(R.string.action_cancel), { confirm = null }, icon = MyHealthIcons.Delete) }
}

data class VitalForm(
    val type: VitalType = VitalType.BloodPressure, val value: String = "", val value2: String = "", val unit: EntryUnit = EntryUnit.Canonical,
    val date: LocalDate, val time: LocalTime, val note: String = "", val customName: String = "", val customUnit: String = "",
)

data class VitalEditorState(val form: VitalForm, val errors: Map<String, VitalEntryError> = emptyMap(), val saved: Boolean = false, val failed: Boolean = false, val heightCm: Double? = null)

class VitalEditorViewModel(private val session: Session, private val clock: Clock, initialType: VitalType?, private val ids: IdGenerator = UuidIds) : ViewModel() {
    private val _state = MutableStateFlow(VitalEditorState(VitalForm(type = initialType ?: VitalType.BloodPressure, date = LocalDate.now(clock), time = LocalTime.now(clock).withSecond(0).withNano(0)),
        heightCm = session.activeMember.value?.heightCm))
    val state: StateFlow<VitalEditorState> = _state.asStateFlow()

    fun edit(t: (VitalForm) -> VitalForm) = _state.update { s -> val f = t(s.form); s.copy(form = if (f.unit !in VitalEntry.unitsFor(f.type)) f.copy(unit = EntryUnit.Canonical) else f, errors = emptyMap()) }

    fun save() {
        val s = _state.value; val f = s.form
        val member = session.activeMember.value ?: return
        val r = VitalEntry.parse(f.type, f.value, f.value2, f.unit, f.customName)
        if (!r.ok) { _state.update { it.copy(errors = r.errors) }; return }
        val at = f.date.atTime(f.time).atZone(clock.zone).toInstant()
        viewModelScope.launch(Safe.handler) {
            runCatching {
                session.stack.vitals.upsert(Vital(ids.next(), member.id, f.type, r.value, r.value2,
                    unit = if (f.type == VitalType.Custom) f.customUnit.trim() else f.type.canonicalUnit, measuredAt = at, customName = f.customName.trim().ifBlank { null }, note = f.note.trim().ifBlank { null }))
            }.onSuccess { _state.update { it.copy(saved = true) } }.onFailure { _state.update { it.copy(failed = true) } }
        }
    }
}

@Composable
fun VitalEditorScreen(state: VitalEditorState, onEdit: ((VitalForm) -> VitalForm) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    val sp = MyHealthTheme.spacing; val f = state.form
    fun err(k: String) = state.errors[k]?.let { e -> when (e) {
        VitalEntryError.Missing -> R.string.vital_err_missing; VitalEntryError.NotANumber -> R.string.vital_err_number; VitalEntryError.OutOfBounds -> R.string.vital_err_bounds
        VitalEntryError.DiastolicNotBelowSystolic -> R.string.vital_err_diastolic; VitalEntryError.CustomNameMissing -> R.string.vital_err_name } }
    Column(Modifier.fillMaxSize().testTag("vitalEditor")) {
        MyHealthTopBar(stringResource(R.string.vital_editor_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            Text(stringResource(R.string.vital_type), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(VitalType.entries.filter { it != VitalType.Bmi }, f.type, { label(it) }, { t -> onEdit { it.copy(type = t) } }, Modifier.testTag("vitalTypes"))
            if (f.type == VitalType.Custom) {
                MyHealthTextField(f.customName, { v -> onEdit { it.copy(customName = v) } }, stringResource(R.string.vital_custom_name), errorText = err("name")?.let { stringResource(it) })
                MyHealthTextField(f.customUnit, { v -> onEdit { it.copy(customUnit = v) } }, stringResource(R.string.vital_unit))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                MyHealthTextField(f.value, { v -> onEdit { it.copy(value = v) } }, if (f.type.paired) stringResource(R.string.review_systolic) else stringResource(R.string.vital_value),
                    Modifier.weight(1f).testTag("vitalValue"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), errorText = err("value")?.let { stringResource(it) })
                if (f.type.paired) MyHealthTextField(f.value2, { v -> onEdit { it.copy(value2 = v) } }, stringResource(R.string.review_diastolic), Modifier.weight(1f).testTag("vitalValue2"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), errorText = err("value2")?.let { stringResource(it) })
            }
            val units = VitalEntry.unitsFor(f.type)
            if (units.size > 1) MyHealthChipRow(units, f.unit, { if (it == EntryUnit.Canonical) f.type.canonicalUnit else it.symbol }, { u -> onEdit { it.copy(unit = u) } })
            else if (f.type.canonicalUnit.isNotBlank()) Text(f.type.canonicalUnit, style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary)
            if (f.type == VitalType.Weight && state.heightCm != null) {
                app.myhealth.domain.parse.Numbers.parse(f.value)?.let { VitalEntry.toCanonical(f.type, it, f.unit) }?.let { VitalRules.bmi(it, state.heightCm) }?.let {
                    MyHealthInfoBanner(stringResource(R.string.vital_bmi_auto, ValueFormat.number(it, 1)), icon = MyHealthIcons.Info)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                MyHealthDateField(stringResource(R.string.vital_when), f.date, { formatDate(it) }, "", { d -> onEdit { it.copy(date = d) } }, stringResource(R.string.action_ok), stringResource(R.string.action_cancel),
                    Modifier.weight(1f), maxDate = LocalDate.now())
                MyHealthTimeField(stringResource(R.string.vital_time), f.time, { formatTime(it) }, { t -> onEdit { it.copy(time = t) } }, stringResource(R.string.action_ok), stringResource(R.string.action_cancel), Modifier.weight(1f))
            }
            MyHealthTextField(f.note, { v -> onEdit { it.copy(note = v) } }, stringResource(R.string.vital_note), singleLine = false)
            if (state.failed) MyHealthInfoBanner(stringResource(R.string.state_error_body), icon = MyHealthIcons.StatusError)
            Spacer(Modifier.height(sp.sm))
            MyHealthPrimaryButton(stringResource(R.string.action_save), onSave, Modifier.fillMaxWidth().testTag("saveVital"), icon = MyHealthIcons.Check)
            Spacer(Modifier.height(sp.xl))
        }
    }
}
