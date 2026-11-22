package app.myhealth.ui.importer

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthCheckbox
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthChoiceField
import app.myhealth.core.designsystem.components.MyHealthDateField
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthErrorState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthProgress
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.data.security.SecureDelete
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.importing.ImportDraft
import app.myhealth.domain.importing.LabRowDraft
import app.myhealth.domain.importing.MedicineDraft
import app.myhealth.domain.importing.VitalDraft
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.parse.Confidence
import app.myhealth.ingest.AndroidTextExtractor
import app.myhealth.ingest.DocumentReader
import app.myhealth.ingest.ImportPipeline
import app.myhealth.ingest.IngestError
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.label
import app.myhealth.ui.common.rememberVm
import java.io.File
import java.time.LocalDate

/** Wires the stateless screen to its ViewModel and the system file picker / camera. */
@Composable
fun ImportRoute(onBack: () -> Unit, onSaved: (reportId: String) -> Unit, startManual: Boolean = false) {
    val session = LocalSession.current
    val context = LocalContext.current
    val vm = rememberVm {
        ImportViewModel(session, DocumentReader(context.applicationContext.contentResolver),
            ImportPipeline(AndroidTextExtractor(context.applicationContext), session.stack.records), session.container.clock)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraMissing by remember { mutableStateOf(false) }
    val fallbackName = stringResource(R.string.rtype_other)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.onEvent(ImportEvent.PickUri(uri, fallbackName))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = cameraFile
        if (ok && path != null) {
            val f = File(path)
            vm.onEvent(ImportEvent.PickUri(FileProvider.getUriForFile(context, context.packageName + ".files", f), f.name))
        } else if (path != null) runCatching { SecureDelete.overwriteAndDelete(File(path)) }
    }
    LaunchedEffect(state.phase) {
        val p = state.phase
        if (p is ImportPhase.Saved) {
            cameraFile?.let { runCatching { SecureDelete.overwriteAndDelete(File(it)) } }
            onSaved(p.reportId)
        }
        if (p is ImportPhase.Review || p is ImportPhase.Failed) cameraFile?.let { runCatching { SecureDelete.overwriteAndDelete(File(it)) }; cameraFile = null }
    }
    LaunchedEffect(startManual) { if (startManual && state.phase == ImportPhase.Choose) vm.onEvent(ImportEvent.Manual) }

    ImportScreen(
        state = state, onEvent = vm::onEvent, onBack = onBack, cameraMissing = cameraMissing,
        onPickFile = { runCatching { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) } },
        onTakePhoto = {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "capture-${System.currentTimeMillis()}.jpg")
            cameraFile = f.absolutePath
            try { camera.launch(FileProvider.getUriForFile(context, context.packageName + ".files", f)) } catch (_: ActivityNotFoundException) { cameraMissing = true }
        },
    )
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    onEvent: (ImportEvent) -> Unit,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    cameraMissing: Boolean = false,
) {
    val c = MyHealthTheme.colors
    Column(Modifier.fillMaxSize().testTag("import")) {
        val title = if (state.phase is ImportPhase.Review || state.phase is ImportPhase.Saving) stringResource(R.string.review_title) else stringResource(R.string.import_title)
        MyHealthTopBar(title, subtitle = state.member?.let { stringResource(R.string.import_for_member, it.name) }, onBack = onBack, backLabel = stringResource(R.string.action_back))
        when (val p = state.phase) {
            ImportPhase.Choose -> ChooseSource(onPickFile, onTakePhoto, { onEvent(ImportEvent.Manual) }, cameraMissing)
            is ImportPhase.Reading -> Box(Modifier.fillMaxSize().padding(MyHealthTheme.spacing.xl), contentAlignment = Alignment.Center) {
                MyHealthProgress(
                    if (p.total > 0) stringResource(R.string.import_reading_page, (p.done + 1).coerceAtMost(p.total), p.total) else stringResource(R.string.import_reading),
                    if (p.total > 0) p.done / p.total.toFloat() else null, Modifier.testTag("reading"),
                )
            }
            is ImportPhase.Failed -> FailedState(p.error, { onEvent(ImportEvent.Reset) }, { onEvent(ImportEvent.Manual) })
            ImportPhase.Review, ImportPhase.Saving, is ImportPhase.Saved -> state.draft?.let { ReviewContent(state, it, onEvent) }
        }
    }
}

@Composable
private fun ChooseSource(onPickFile: () -> Unit, onTakePhoto: () -> Unit, onManual: () -> Unit, cameraMissing: Boolean) {
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(sp.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            MyHealthEmptyState(MyHealthIcons.Import, stringResource(R.string.import_intro_title), stringResource(R.string.import_intro_body), Modifier.height(300.dp))
            MyHealthPrimaryButton(stringResource(R.string.import_pick_file), onPickFile, Modifier.fillMaxWidth().testTag("pickFile"), icon = MyHealthIcons.Document)
            MyHealthSecondaryButton(stringResource(R.string.import_take_photo), onTakePhoto, Modifier.fillMaxWidth(), icon = MyHealthIcons.Camera)
            if (cameraMissing) MyHealthInfoBanner(stringResource(R.string.import_error_camera), icon = MyHealthIcons.StatusWarning)
            MyHealthTextButton(stringResource(R.string.import_manual), onManual, Modifier.fillMaxWidth().testTag("manualEntry"), icon = MyHealthIcons.Edit)
            MyHealthInfoBanner(stringResource(R.string.state_offline_banner), icon = MyHealthIcons.Lock)
        }
    }
}

@Composable
private fun FailedState(error: IngestError, onAnother: () -> Unit, onManual: () -> Unit) {
    val (t, b) = when (error) {
        IngestError.Unreadable, IngestError.NoText -> R.string.import_error_unreadable_title to R.string.import_error_unreadable_body
        IngestError.Unsupported -> R.string.import_error_unsupported_title to R.string.import_error_unsupported_body
        IngestError.TooLarge -> R.string.import_error_too_large_title to R.string.import_error_too_large_body
        IngestError.PasswordProtected -> R.string.import_error_password_title to R.string.import_error_password_body
        IngestError.OcrUnavailable -> R.string.import_error_ocr_title to R.string.import_error_ocr_body
        IngestError.PermissionDenied -> R.string.import_error_permission_title to R.string.import_error_permission_body
    }
    MyHealthErrorState(stringResource(t), stringResource(b), Modifier.testTag("importFailed"),
        actionLabel = stringResource(R.string.import_choose_another), onAction = onAnother,
        secondaryLabel = stringResource(R.string.import_manual), onSecondary = onManual)
}

@Composable
private fun ReviewContent(state: ImportUiState, d: ImportDraft, onEvent: (ImportEvent) -> Unit) {
    val sp = MyHealthTheme.spacing
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "new" or a row key
    val fallbackTitle = label(d.type)
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("reviewList"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = sp.gutter, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.md)) {
            item { MyHealthInfoBanner(stringResource(R.string.review_intro)) }
            d.duplicateOf?.let { dup ->
                item { MyHealthCard(emphasis = app.myhealth.core.designsystem.components.Emphasis.Medium) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MyHealthIcons.Copy, null, tint = MyHealthTheme.colors.accent); Spacer(Modifier.width(sp.sm))
                        Text(stringResource(R.string.review_duplicate_title), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                    }
                    Text(stringResource(R.string.review_duplicate_body, dup.report.title, formatDate(dup.report.reportDate)), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                } }
            }
            if (state.ocrUsed) item { MyHealthInfoBanner(stringResource(R.string.import_ocr_note), icon = MyHealthIcons.StatusWarning) }
            d.patientName?.let { item { MyHealthInfoBanner(stringResource(R.string.review_name_on_report, it), icon = MyHealthIcons.Profile) } }
            if (state.saveFailed) item { MyHealthInfoBanner(stringResource(R.string.import_save_failed), icon = MyHealthIcons.StatusError) }
            if (state.showIssues && state.issues.isNotEmpty()) item { MyHealthInfoBanner(stringResource(R.string.review_fix_errors), icon = MyHealthIcons.StatusError) }

            item {
                MyHealthCard {
                    Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                        Text(stringResource(R.string.review_details), style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                        if (state.members.size > 1) {
                            Text(stringResource(R.string.review_saving_to), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
                            MyHealthChipRow(state.members.map { it.id }, d.memberId, { id -> state.members.first { it.id == id }.name }, { onEvent(ImportEvent.SetMember(it)) })
                        }
                        val dateErr = state.issuesFor(null).firstOrNull { it.error == app.myhealth.domain.importing.DraftError.MissingDate || it.error == app.myhealth.domain.importing.DraftError.FutureDate }
                        MyHealthDateField(stringResource(R.string.review_report_date), d.reportDate, { formatDate(it) }, stringResource(R.string.date_pick),
                            { onEvent(ImportEvent.SetDate(it)) }, stringResource(R.string.action_ok), stringResource(R.string.action_cancel), maxDate = today,
                            errorText = dateErr?.let { label(it.error) })
                        MyHealthTextField(d.title, { onEvent(ImportEvent.SetTitle(it)) }, stringResource(R.string.review_report_title), supportingText = if (d.title.isBlank()) fallbackTitle else null)
                        MyHealthTextField(d.labName, { onEvent(ImportEvent.SetLab(it)) }, stringResource(R.string.review_lab))
                        MyHealthChoiceField(stringResource(R.string.review_type), ReportType.entries, d.type, { label(it) }, { onEvent(ImportEvent.SetType(it)) }, confirmLabel = stringResource(R.string.action_close))
                    }
                }
            }
            item { MyHealthSectionHeader(stringResource(R.string.review_lab_values)) }
            if (d.labRows.isEmpty()) item {
                MyHealthCard { Text(stringResource(R.string.review_no_values_title), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                    Text(stringResource(R.string.review_no_values_body), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary) }
            }
            items(d.labRows, key = { it.key }) { row -> LabRowCard(row, state, onEvent, onChangeTest = { picking = row.key }) }
            item { MyHealthSecondaryButton(stringResource(R.string.review_add_value), { picking = "new" }, Modifier.fillMaxWidth().testTag("addValue"), icon = MyHealthIcons.Add) }
            if (d.vitals.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.review_vitals)) }
                items(d.vitals, key = { it.key }) { v -> VitalRowCard(v, state, onEvent) }
            }
            if (d.medicines.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.review_medicines)) }
                items(d.medicines, key = { it.key }) { m -> MedicineRowCard(m, state, onEvent) }
            }
            item { Spacer(Modifier.height(sp.lg)) }
        }
        Box(Modifier.fillMaxWidth().background(MyHealthTheme.colors.surface).navigationBarsPadding().padding(sp.gutter)) {
            val n = d.includedCount
            MyHealthPrimaryButton(
                when { state.phase == ImportPhase.Saving -> stringResource(R.string.saving); n == 0 -> stringResource(R.string.review_save_document); else -> pluralStringResource(R.plurals.review_save, n, n) },
                { onEvent(ImportEvent.Save(fallbackTitle)) }, Modifier.fillMaxWidth().testTag("saveImport"), icon = MyHealthIcons.Check, enabled = state.phase != ImportPhase.Saving,
            )
        }
    }
    picking?.let { target ->
        AnalytePickerDialog(onPick = { id ->
            if (target == "new") onEvent(ImportEvent.AddLab(id))
            else onEvent(ImportEvent.EditLab(target) { r -> r.copy(analyteId = id, unit = AnalyteCatalogue.byId(id)!!.canonicalUnit) })
            picking = null
        }, onDismiss = { picking = null })
    }
}

@Composable
private fun LabRowCard(row: LabRowDraft, state: ImportUiState, onEvent: (ImportEvent) -> Unit, onChangeTest: () -> Unit) {
    val sp = MyHealthTheme.spacing; val c = MyHealthTheme.colors
    val analyte = row.analyteId?.let { AnalyteCatalogue.byId(it) }
    val name = row.analyteId?.let { analyteName(it) } ?: row.printedName
    val issues = state.issuesFor(row.key).map { it.error }
    fun edit(t: (LabRowDraft) -> LabRowDraft) = onEvent(ImportEvent.EditLab(row.key, t))
    MyHealthCard(Modifier.testTag("labRow_${row.analyteId}"), emphasis = if (row.include) app.myhealth.core.designsystem.components.Emphasis.Subtle else app.myhealth.core.designsystem.components.Emphasis.Subtle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyHealthCheckbox(row.include, { v -> edit { it.copy(include = v) } }, stringResource(R.string.review_include, name), Modifier.testTag("include_${row.analyteId}"))
            Column(Modifier.weight(1f)) {
                MyHealthTextButton(name, onChangeTest, icon = MyHealthIcons.Expand)
            }
            val kind = when (row.confidence) { Confidence.High -> StatusKind.Normal; Confidence.Medium -> StatusKind.Attention; Confidence.Low -> StatusKind.Unknown }
            if (!row.manual) MyHealthStatusBadge(label(row.confidence), kind)
        }
        if (row.include) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                    MyHealthTextField(row.valueText, { v -> edit { it.copy(valueText = v) } }, stringResource(R.string.review_value), Modifier.weight(1f).testTag("value_${row.analyteId}"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        errorText = issues.firstOrNull { it == app.myhealth.domain.importing.DraftError.InvalidValue }?.let { label(it) })
                    if (analyte != null) {
                        MyHealthChoiceField(stringResource(R.string.review_unit), listOf(analyte.canonicalUnit) + analyte.conversions.keys, row.unit, { it.ifBlank { "—" } },
                            { u -> edit { it.copy(unit = u) } }, Modifier.weight(1f), confirmLabel = stringResource(R.string.action_close))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                    MyHealthTextField(row.lowText, { v -> edit { it.copy(lowText = v) } }, stringResource(R.string.review_low), Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    MyHealthTextField(row.highText, { v -> edit { it.copy(highText = v) } }, stringResource(R.string.review_high), Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                issues.filter { it != app.myhealth.domain.importing.DraftError.InvalidValue }.forEach { MyHealthInfoBanner(label(it), icon = MyHealthIcons.StatusError) }
                if (row.rawLine.isNotBlank()) Text(stringResource(R.string.review_read_as, row.rawLine), style = MyHealthTheme.typography.caption, color = c.textTertiary)
                row.warnings.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MyHealthIcons.StatusWarning, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(sp.xs))
                        Text(label(w), style = MyHealthTheme.typography.caption, color = c.textSecondary)
                    }
                }
                state.conflicts[row.analyteId]?.let { old ->
                    val a = AnalyteCatalogue.byId(old.analyteId)
                    MyHealthInfoBanner(stringResource(R.string.review_replaces, ValueFormat.number(old.value, a?.decimals ?: 1) + " " + old.unit), icon = MyHealthIcons.Restore)
                }
            }
        }
    }
}

@Composable
private fun VitalRowCard(v: VitalDraft, state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    val sp = MyHealthTheme.spacing
    fun edit(t: (VitalDraft) -> VitalDraft) = onEvent(ImportEvent.EditVital(v.key, t))
    val err = state.issuesFor(v.key).firstOrNull()
    MyHealthCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyHealthCheckbox(v.include, { x -> edit { it.copy(include = x) } }, stringResource(R.string.review_include, label(v.type)))
            Text(label(v.type), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(v.type.canonicalUnit, style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary)
        }
        if (v.include) Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            MyHealthTextField(v.valueText, { x -> edit { it.copy(valueText = x) } }, if (v.type.paired) stringResource(R.string.review_systolic) else stringResource(R.string.review_value),
                Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), errorText = err?.let { label(it.error) })
            if (v.type.paired) MyHealthTextField(v.value2Text, { x -> edit { it.copy(value2Text = x) } }, stringResource(R.string.review_diastolic), Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
}

@Composable
private fun MedicineRowCard(m: MedicineDraft, state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    val sp = MyHealthTheme.spacing
    fun edit(t: (MedicineDraft) -> MedicineDraft) = onEvent(ImportEvent.EditMedicine(m.key, t))
    val err = state.issuesFor(m.key).firstOrNull()
    MyHealthCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyHealthCheckbox(m.include, { x -> edit { it.copy(include = x) } }, stringResource(R.string.review_include, m.name))
            Text(stringResource(R.string.join_dot, label(m.form), m.name), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        }
        if (m.include) Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            MyHealthTextField(m.name, { x -> edit { it.copy(name = x) } }, stringResource(R.string.review_med_name), errorText = err?.let { label(it.error) })
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                MyHealthTextField(m.strength, { x -> edit { it.copy(strength = x) } }, stringResource(R.string.review_strength), Modifier.weight(1f))
                MyHealthTextField(m.durationText, { x -> edit { it.copy(durationText = x.filter(Char::isDigit).take(3)) } }, stringResource(R.string.review_duration_days), Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            MyHealthChoiceField(stringResource(R.string.review_frequency), Frequency.entries, m.frequency, { label(it) }, { f -> edit { it.copy(frequency = f) } }, confirmLabel = stringResource(R.string.action_close))
        }
    }
}
