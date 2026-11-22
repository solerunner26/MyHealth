package app.myhealth.ui.care

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.Safe
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthChoiceField
import app.myhealth.core.designsystem.components.MyHealthDateField
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIconButton
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthSwitchRow
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTimeField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.DoseSchedule
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.MedicineForm
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.TreatmentKind
import app.myhealth.domain.util.IdGenerator
import app.myhealth.domain.util.UuidIds
import app.myhealth.reminders.Notifications
import app.myhealth.ui.Session
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.formatTime
import app.myhealth.ui.common.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class CareState(val loading: Boolean = true, val medicines: List<Medicine> = emptyList(), val logs: List<DoseLog> = emptyList(), val treatments: List<Treatment> = emptyList(), val today: LocalDate = LocalDate.MIN)

@OptIn(ExperimentalCoroutinesApi::class)
class CareViewModel(session: Session) : ViewModel() {
    private val clock = session.container.clock
    val state: StateFlow<CareState> = session.activeMember.filterNotNull().flatMapLatest { m ->
        combine(session.stack.medicines.observe(m.id), session.stack.medicines.observeDoseLogs(m.id, clock.instant().minus(Duration.ofDays(8))), session.stack.treatments.observe(m.id)) { a, b, c ->
            CareState(false, a, b, c, LocalDate.now(clock))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareState())
}

enum class CareTab { Medicines, Treatments }

@Composable
fun CareContent(state: CareState, clock: Clock, onAddMedicine: () -> Unit, onEditMedicine: (String) -> Unit, onAddTreatment: () -> Unit, onEditTreatment: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(CareTab.Medicines) }
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize().testTag("care")) {
        MyHealthChipRow(CareTab.entries, tab, { stringResource(if (it == CareTab.Medicines) R.string.care_tab_medicines else R.string.care_tab_treatments) }, { tab = it }, Modifier.padding(horizontal = sp.gutter))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        when (tab) {
            CareTab.Medicines -> {
                if (state.medicines.isEmpty()) { MyHealthEmptyState(MyHealthIcons.Medicines, stringResource(R.string.med_empty_title), stringResource(R.string.med_empty_body),
                    actionLabel = stringResource(R.string.med_add), onAction = onAddMedicine); return@Column }
                val (active, past) = state.medicines.partition { it.isActiveOn(state.today) || it.startDate.isAfter(state.today) && !it.stopped }
                LazyColumn(Modifier.fillMaxSize().testTag("medList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    item { MyHealthPrimaryButton(stringResource(R.string.med_add), onAddMedicine, Modifier.fillMaxWidth().testTag("addMedicine"), icon = MyHealthIcons.Add) }
                    if (active.isNotEmpty()) item { MyHealthSectionHeader(stringResource(R.string.med_active)) }
                    items(active, key = { it.id }) { m -> MedicineCard(m, state, clock, onEditMedicine) }
                    if (past.isNotEmpty()) item { MyHealthSectionHeader(stringResource(R.string.med_past)) }
                    items(past, key = { it.id }) { m -> MedicineCard(m, state, clock, onEditMedicine) }
                }
            }
            CareTab.Treatments -> {
                if (state.treatments.isEmpty()) { MyHealthEmptyState(MyHealthIcons.Treatments, stringResource(R.string.treat_empty_title), stringResource(R.string.treat_empty_body),
                    actionLabel = stringResource(R.string.treat_add), onAction = onAddTreatment); return@Column }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    item { MyHealthPrimaryButton(stringResource(R.string.treat_add), onAddTreatment, Modifier.fillMaxWidth(), icon = MyHealthIcons.Add) }
                    items(state.treatments, key = { it.id }) { t ->
                        MyHealthCard(onClick = { onEditTreatment(t.id) }) {
                            MyHealthListRow(t.name, subtitle = listOfNotNull(stringResource(R.string.join_dot, label(t.kind), formatDate(t.date)), t.provider.ifBlank { null },
                                t.followUpDate?.let { stringResource(R.string.treat_followup_on, formatDate(it)) }).joinToString("\n"), leadingIcon = MyHealthIcons.Treatments) {
                                if (t.followUpDate?.isAfter(state.today) == false && !t.followUpDone) MyHealthStatusBadge(stringResource(R.string.sev_moderate), StatusKind.SeverityModerate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicineCard(m: Medicine, state: CareState, clock: Clock, onEdit: (String) -> Unit) {
    val now = clock.instant()
    val next = DoseSchedule.next(m, now, clock.zone)
    val weekAgo = now.minus(Duration.ofDays(7))
    val expected = DoseSchedule.occurrences(m, maxOf(weekAgo, m.startDate.atStartOfDay(clock.zone).toInstant()), now, clock.zone).size
    val taken = state.logs.count { it.medicineId == m.id && it.status == DoseStatus.Taken && it.scheduledAt.isAfter(weekAgo) }
    MyHealthCard(onClick = { onEdit(m.id) }, modifier = Modifier.testTag("med_${m.name}")) {
        MyHealthListRow(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "),
            subtitle = listOfNotNull(stringResource(R.string.join_dot, label(m.form), label(m.frequency)), m.endDate?.let { stringResource(R.string.med_until, formatDate(it)) },
                next?.let { stringResource(R.string.med_next_dose, stringResource(R.string.join_dot, formatDate(it.atZone(clock.zone).toLocalDate()), formatTime(it.atZone(clock.zone).toLocalTime()))) },
                if (m.remindersEnabled && expected > 0) stringResource(R.string.med_adherence, taken.coerceAtMost(expected), expected) else null).joinToString("\n"),
            leadingIcon = MyHealthIcons.Medicines) {
            if (m.remindersEnabled && !m.stopped) MyHealthStatusBadge(stringResource(R.string.med_reminders_on), StatusKind.Info)
        }
    }
}

// ---------------- Medicine editor
data class MedicineFormState(
    val name: String = "", val strength: String = "", val form: MedicineForm = MedicineForm.Tablet, val dose: String = "", val frequency: Frequency = Frequency.OnceDaily,
    val start: LocalDate, val end: LocalDate? = null, val durationText: String = "", val reminders: Boolean = false, val times: List<LocalTime> = Frequency.OnceDaily.defaultTimes(),
    val instructions: String = "", val prescribedBy: String = "", val stopped: Boolean = false,
)

enum class MedError { Name, End, Times }

data class MedicineEditorState(val loading: Boolean = true, val form: MedicineFormState, val isNew: Boolean = true, val errors: Set<MedError> = emptySet(), val done: Boolean = false, val failed: Boolean = false)

class MedicineEditorViewModel(private val session: Session, private val id: String?, private val ids: IdGenerator = UuidIds) : ViewModel() {
    private val clock = session.container.clock
    private val _state = MutableStateFlow(MedicineEditorState(form = MedicineFormState(start = LocalDate.now(clock))))
    val state: StateFlow<MedicineEditorState> = _state.asStateFlow()
    private var existing: Medicine? = null

    init {
        viewModelScope.launch(Safe.handler) {
            existing = id?.let { runCatching { session.stack.medicines.get(it) }.getOrNull() }
            val m = existing
            _state.update { s -> s.copy(loading = false, isNew = m == null, form = m?.let {
                MedicineFormState(it.name, it.strength, it.form, it.dose, it.frequency, it.startDate, it.endDate, "", it.remindersEnabled, it.reminderTimes, it.instructions, it.prescribedBy, it.stopped)
            } ?: s.form) }
        }
    }

    fun edit(t: (MedicineFormState) -> MedicineFormState) = _state.update { s ->
        val before = s.form; var f = t(before)
        if (f.frequency != before.frequency && (before.times.isEmpty() || before.times == before.frequency.defaultTimes())) f = f.copy(times = f.frequency.defaultTimes())
        s.copy(form = f, errors = emptySet())
    }

    private fun validate(f: MedicineFormState): Set<MedError> = buildSet {
        if (f.name.isBlank()) add(MedError.Name)
        val end = effectiveEnd(f)
        if (end != null && end.isBefore(f.start)) add(MedError.End)
        if (f.reminders && f.frequency != Frequency.AsNeeded && f.times.isEmpty()) add(MedError.Times)
    }

    private fun effectiveEnd(f: MedicineFormState) = f.durationText.toIntOrNull()?.takeIf { it > 0 }?.let { f.start.plusDays(it - 1L) } ?: f.end

    fun save(stopOverride: Boolean? = null) {
        val f = _state.value.form.let { if (stopOverride != null) it.copy(stopped = stopOverride) else it }
        val errors = validate(f)
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        val member = existing?.memberId ?: session.activeMember.value?.id ?: return
        val m = Medicine(existing?.id ?: ids.next(), member, f.name.trim(), f.strength.trim(), f.form, f.frequency, f.dose.trim(), f.start, effectiveEnd(f),
            f.times.distinct().sorted(), f.reminders, f.instructions.trim(), f.prescribedBy.trim(), existing?.reportId, f.stopped)
        viewModelScope.launch(Safe.handler) {
            runCatching {
                session.stack.medicines.upsert(m)
                session.container.reminders.scheduleNext(m, clock.instant())
            }.onSuccess { _state.update { it.copy(done = true) } }.onFailure { _state.update { it.copy(failed = true) } }
        }
    }

    fun delete() {
        val m = existing ?: return
        viewModelScope.launch(Safe.handler) {
            runCatching { session.container.reminders.cancel(m.id); session.stack.medicines.delete(m.id) }
                .onSuccess { _state.update { it.copy(done = true) } }.onFailure { _state.update { it.copy(failed = true) } }
        }
    }
}

@Composable
fun MedicineEditorScreen(state: MedicineEditorState, onEdit: ((MedicineFormState) -> MedicineFormState) -> Unit, onSave: (Boolean?) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    val sp = MyHealthTheme.spacing; val f = state.form
    val context = LocalContext.current
    var notifDenied by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notifDenied = !granted }
    Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).testTag("medicineEditor")) {
        MyHealthTopBar(stringResource(if (state.isNew) R.string.med_editor_add else R.string.med_editor_edit), onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            MyHealthTextField(f.name, { v -> onEdit { it.copy(name = v) } }, stringResource(R.string.med_name), Modifier.testTag("medName"),
                errorText = if (MedError.Name in state.errors) stringResource(R.string.med_err_name) else null)
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                MyHealthTextField(f.strength, { v -> onEdit { it.copy(strength = v) } }, stringResource(R.string.med_strength), Modifier.weight(1f))
                MyHealthTextField(f.dose, { v -> onEdit { it.copy(dose = v) } }, stringResource(R.string.med_dose), Modifier.weight(1f))
            }
            MyHealthChoiceField(stringResource(R.string.med_form), MedicineForm.entries, f.form, { label(it) }, { v -> onEdit { it.copy(form = v) } }, confirmLabel = stringResource(R.string.action_close))
            MyHealthChoiceField(stringResource(R.string.med_frequency), Frequency.entries, f.frequency, { label(it) }, { v -> onEdit { it.copy(frequency = v) } }, confirmLabel = stringResource(R.string.action_close))
            MyHealthDateField(stringResource(R.string.med_start), f.start, { formatDate(it) }, "", { d -> onEdit { it.copy(start = d) } }, stringResource(R.string.action_ok), stringResource(R.string.action_cancel))
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                MyHealthDateField(stringResource(R.string.med_end), f.end, { formatDate(it) }, stringResource(R.string.date_not_set), { d -> onEdit { it.copy(end = d, durationText = "") } },
                    stringResource(R.string.action_ok), stringResource(R.string.action_cancel), Modifier.weight(1f), errorText = if (MedError.End in state.errors) stringResource(R.string.med_err_end) else null)
                MyHealthTextField(f.durationText, { v -> onEdit { it.copy(durationText = v.filter(Char::isDigit).take(3)) } }, stringResource(R.string.med_duration), Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            MyHealthSectionHeader(stringResource(R.string.med_reminders))
            MyHealthSwitchRow(stringResource(R.string.med_reminders), f.reminders, { on ->
                onEdit { it.copy(reminders = on) }
                if (on && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, subtitle = stringResource(R.string.med_reminders_sub), modifier = Modifier.testTag("medReminders"))
            if (f.reminders && (notifDenied || !Notifications.canPost(context))) MyHealthInfoBanner(stringResource(R.string.med_notif_denied), icon = MyHealthIcons.StatusWarning)
            if (f.reminders && f.frequency != Frequency.AsNeeded) {
                Text(stringResource(R.string.med_reminder_times), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
                f.times.forEachIndexed { i, t ->
                    Row {
                        MyHealthTimeField(stringResource(R.string.med_reminder_times), t, { formatTime(it) }, { nt -> onEdit { s -> s.copy(times = s.times.toMutableList().also { it[i] = nt }) } },
                            stringResource(R.string.action_ok), stringResource(R.string.action_cancel), Modifier.weight(1f))
                        MyHealthIconButton(MyHealthIcons.Close, stringResource(R.string.med_remove_time, formatTime(t)), { onEdit { s -> s.copy(times = s.times.filterIndexed { j, _ -> j != i }) } })
                    }
                }
                if (MedError.Times in state.errors) MyHealthInfoBanner(stringResource(R.string.med_err_times), icon = MyHealthIcons.StatusError)
                MyHealthTextButton(stringResource(R.string.med_add_time), { onEdit { it.copy(times = it.times + LocalTime.of(12, 0)) } }, icon = MyHealthIcons.Add)
            }
            MyHealthTextField(f.instructions, { v -> onEdit { it.copy(instructions = v) } }, stringResource(R.string.med_instructions), singleLine = false)
            MyHealthTextField(f.prescribedBy, { v -> onEdit { it.copy(prescribedBy = v) } }, stringResource(R.string.med_prescribed_by))
            if (state.failed) MyHealthInfoBanner(stringResource(R.string.state_error_body), icon = MyHealthIcons.StatusError)
            MyHealthPrimaryButton(stringResource(R.string.action_save), { onSave(null) }, Modifier.fillMaxWidth().testTag("saveMedicine"), icon = MyHealthIcons.Check)
            if (!state.isNew) {
                if (f.stopped) MyHealthSecondaryButton(stringResource(R.string.med_restart), { onSave(false) }, Modifier.fillMaxWidth())
                else MyHealthSecondaryButton(stringResource(R.string.med_finished), { onSave(true) }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Check)
                MyHealthTextButton(stringResource(R.string.med_delete), { confirmDelete = true }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Delete)
            }
            Spacer(Modifier.height(sp.xl))
        }
    }
    if (confirmDelete) MyHealthDialog(stringResource(R.string.med_delete_title), stringResource(R.string.med_delete_body), stringResource(R.string.action_delete), { confirmDelete = false; onDelete() },
        stringResource(R.string.action_cancel), { confirmDelete = false }, icon = MyHealthIcons.Delete)
}

// ---------------- Treatment editor
data class TreatmentForm(val name: String = "", val kind: TreatmentKind = TreatmentKind.Procedure, val date: LocalDate, val provider: String = "", val followUp: LocalDate? = null, val followUpDone: Boolean = false, val notes: String = "")
data class TreatmentEditorState(val loading: Boolean = true, val form: TreatmentForm, val isNew: Boolean = true, val nameError: Boolean = false, val followUpError: Boolean = false, val done: Boolean = false, val failed: Boolean = false)

class TreatmentEditorViewModel(private val session: Session, private val id: String?, private val ids: IdGenerator = UuidIds) : ViewModel() {
    private val _state = MutableStateFlow(TreatmentEditorState(form = TreatmentForm(date = LocalDate.now(session.container.clock))))
    val state: StateFlow<TreatmentEditorState> = _state.asStateFlow()
    private var existing: Treatment? = null
    init {
        viewModelScope.launch(Safe.handler) {
            val member = session.activeMember.value
            existing = id?.let { tid -> member?.let { m -> runCatching { session.stack.treatments.all(m.id) }.getOrDefault(emptyList()).firstOrNull { it.id == tid } } }
            _state.update { s -> s.copy(loading = false, isNew = existing == null, form = existing?.let { TreatmentForm(it.name, it.kind, it.date, it.provider, it.followUpDate, it.followUpDone, it.notes) } ?: s.form) }
        }
    }
    fun edit(t: (TreatmentForm) -> TreatmentForm) = _state.update { it.copy(form = t(it.form), nameError = false, followUpError = false) }
    fun save() {
        val f = _state.value.form
        val fuErr = f.followUp != null && f.followUp.isBefore(f.date)
        if (f.name.isBlank() || fuErr) { _state.update { it.copy(nameError = f.name.isBlank(), followUpError = fuErr) }; return }
        val member = existing?.memberId ?: session.activeMember.value?.id ?: return
        viewModelScope.launch(Safe.handler) {
            runCatching { session.stack.treatments.upsert(Treatment(existing?.id ?: ids.next(), member, f.name.trim(), f.kind, f.date, f.provider.trim(), f.followUp, f.notes.trim(), f.followUpDone)) }
                .onSuccess { _state.update { it.copy(done = true) } }.onFailure { _state.update { it.copy(failed = true) } }
        }
    }
    fun delete() { val t = existing ?: return
        viewModelScope.launch(Safe.handler) { runCatching { session.stack.treatments.delete(t.id) }.onSuccess { _state.update { it.copy(done = true) } } } }
}

@Composable
fun TreatmentEditorScreen(state: TreatmentEditorState, onEdit: ((TreatmentForm) -> TreatmentForm) -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    val sp = MyHealthTheme.spacing; val f = state.form
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).testTag("treatmentEditor")) {
        MyHealthTopBar(stringResource(if (state.isNew) R.string.treat_editor_add else R.string.treat_editor_edit), onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            MyHealthTextField(f.name, { v -> onEdit { it.copy(name = v) } }, stringResource(R.string.treat_name), errorText = if (state.nameError) stringResource(R.string.treat_err_name) else null)
            MyHealthChoiceField(stringResource(R.string.treat_kind), TreatmentKind.entries, f.kind, { label(it) }, { v -> onEdit { it.copy(kind = v) } }, confirmLabel = stringResource(R.string.action_close))
            MyHealthDateField(stringResource(R.string.treat_date), f.date, { formatDate(it) }, "", { d -> onEdit { it.copy(date = d) } }, stringResource(R.string.action_ok), stringResource(R.string.action_cancel))
            MyHealthTextField(f.provider, { v -> onEdit { it.copy(provider = v) } }, stringResource(R.string.treat_provider))
            MyHealthDateField(stringResource(R.string.treat_followup), f.followUp, { formatDate(it) }, stringResource(R.string.date_not_set), { d -> onEdit { it.copy(followUp = d) } },
                stringResource(R.string.action_ok), stringResource(R.string.action_cancel), errorText = if (state.followUpError) stringResource(R.string.treat_err_followup) else null)
            if (f.followUp != null) MyHealthSwitchRow(stringResource(R.string.treat_followup_done), f.followUpDone, { v -> onEdit { it.copy(followUpDone = v) } })
            MyHealthTextField(f.notes, { v -> onEdit { it.copy(notes = v) } }, stringResource(R.string.treat_notes), singleLine = false)
            if (state.failed) MyHealthInfoBanner(stringResource(R.string.state_error_body), icon = MyHealthIcons.StatusError)
            MyHealthPrimaryButton(stringResource(R.string.action_save), onSave, Modifier.fillMaxWidth(), icon = MyHealthIcons.Check)
            if (!state.isNew) MyHealthTextButton(stringResource(R.string.treat_delete), { confirm = true }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Delete)
            Spacer(Modifier.height(sp.xl))
        }
    }
    if (confirm) MyHealthDialog(stringResource(R.string.treat_delete_title), stringResource(R.string.treat_delete_body), stringResource(R.string.action_delete), { confirm = false; onDelete() },
        stringResource(R.string.action_cancel), { confirm = false }, icon = MyHealthIcons.Delete)
}

