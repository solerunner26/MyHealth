package app.myhealth.ui.importer

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import app.myhealth.core.designsystem.components.appear
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

    var cameraForPage by rememberSaveable { mutableStateOf(false) }
    // Several files at once become one record (e.g. every page of a hospital file).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.onEvent(ImportEvent.PickUris(uris, fallbackName))
    }
    val pagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { vm.onEvent(ImportEvent.AddPage(it, fallbackName)) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = cameraFile
        if (ok && path != null) {
            val f = File(path)
            val u = FileProvider.getUriForFile(context, context.packageName + ".files", f)
            vm.onEvent(if (cameraForPage) ImportEvent.AddPage(u, f.name) else ImportEvent.PickUri(u, f.name))
        } else if (path != null) runCatching { SecureDelete.overwriteAndDelete(File(path)) }
    }
    LaunchedEffect(state.phase) {
        val p = state.phase
        if (p is ImportPhase.Saved) {
            cameraFile?.let { runCatching { SecureDelete.overwriteAndDelete(File(it)) } }
            onSaved(p.reportId)
        }
        // camera captures are deleted only once they are imported (encrypted) or abandoned
        if ((p is ImportPhase.Review && !cameraForPage) || p is ImportPhase.Failed) cameraFile?.let { runCatching { SecureDelete.overwriteAndDelete(File(it)) }; cameraFile = null }
    }
    LaunchedEffect(startManual) { if (startManual && state.phase == ImportPhase.Choose) vm.onEvent(ImportEvent.Manual) }

    val mimes = arrayOf("application/pdf", "image/*")
    val takePhoto: (Boolean) -> Unit = { forPage ->
        cameraForPage = forPage
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        cameraFile = f.absolutePath
        try { camera.launch(FileProvider.getUriForFile(context, context.packageName + ".files", f)) } catch (_: ActivityNotFoundException) { cameraMissing = true }
    }
    ImportScreen(
        state = state, onEvent = vm::onEvent, onBack = onBack, cameraMissing = cameraMissing,
        onAddPageFile = { runCatching { pagePicker.launch(mimes) } }, onAddPagePhoto = { takePhoto(true) },
        onPickFile = { runCatching { picker.launch(mimes) } },
        onTakePhoto = { takePhoto(false) },
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
    onAddPageFile: () -> Unit = {},
    onAddPagePhoto: () -> Unit = {},
) {
    val c = MyHealthTheme.colors
    Column(Modifier.fillMaxSize().testTag("import")) {
        val batchList = state.phase == ImportPhase.Batch || (state.draft == null && state.batch.isNotEmpty())
        val title = when {
            batchList -> stringResource(R.string.batch_title)
            state.phase is ImportPhase.Review || state.phase is ImportPhase.Saving -> stringResource(R.string.review_title)
            else -> stringResource(R.string.import_title)
        }
        // inside a batch, back returns to the list of reports instead of leaving the import
        val back = if (state.inBatch) ({ onEvent(ImportEvent.BackToBatch) }) else onBack
        androidx.activity.compose.BackHandler(enabled = state.inBatch) { onEvent(ImportEvent.BackToBatch) }
        MyHealthTopBar(title, subtitle = if (batchList) null else state.member?.let { stringResource(R.string.import_for_member, it.name) }, onBack = back, backLabel = stringResource(R.string.action_back))
        when (val p = state.phase) {
            ImportPhase.Choose -> ChooseSource(onPickFile, onTakePhoto, { onEvent(ImportEvent.Manual) }, cameraMissing)
            is ImportPhase.Reading -> Box(Modifier.fillMaxSize().padding(MyHealthTheme.spacing.xl), contentAlignment = Alignment.Center) {
                MyHealthProgress(
                    if (p.total > 0) stringResource(R.string.import_reading_page, (p.done + 1).coerceAtMost(p.total), p.total) else stringResource(R.string.import_reading),
                    if (p.total > 0) p.done / p.total.toFloat() else null, Modifier.testTag("reading"),
                )
            }
            is ImportPhase.Failed -> FailedState(p.error, { onEvent(ImportEvent.Reset) }, { onEvent(ImportEvent.Manual) })
            is ImportPhase.ChooseMember -> ChooseMemberContent(p, onEvent)
            ImportPhase.Batch -> BatchContent(state, onEvent)
            ImportPhase.Review, ImportPhase.Saving, is ImportPhase.Saved ->
                if (state.draft == null && state.batch.isNotEmpty()) BatchContent(state, onEvent)
                else state.draft?.let { ReviewContent(state, it, onEvent, onAddPageFile, onAddPagePhoto) }
        }
    }
}

@Composable
private fun ChooseSource(onPickFile: () -> Unit, onTakePhoto: () -> Unit, onManual: () -> Unit, cameraMissing: Boolean) {
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(sp.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            MyHealthEmptyState(MyHealthIcons.Import, stringResource(R.string.import_intro_title), stringResource(R.string.import_intro_body2), Modifier.height(320.dp))
            MyHealthPrimaryButton(stringResource(R.string.import_pick_files), onPickFile, Modifier.fillMaxWidth().testTag("pickFile"), icon = MyHealthIcons.Document)
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
private fun ReviewContent(state: ImportUiState, d: ImportDraft, onEvent: (ImportEvent) -> Unit, onAddPageFile: () -> Unit, onAddPagePhoto: () -> Unit) {
    val sp = MyHealthTheme.spacing
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "new" or a row key
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var changePerson by rememberSaveable { mutableStateOf(false) }
    val fallbackTitle = label(d.type)
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("reviewList"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = sp.gutter, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.md)) {
            state.autoMatchedName?.let { n -> item { MyHealthInfoBanner(stringResource(R.string.review_auto_member, n, state.member?.name.orEmpty()), icon = MyHealthIcons.Profile) } }
            d.duplicateOf?.let { dup ->
                item { MyHealthCard(emphasis = app.myhealth.core.designsystem.components.Emphasis.Medium) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MyHealthIcons.Copy, null, tint = MyHealthTheme.colors.accent); Spacer(Modifier.width(sp.sm))
                        Text(stringResource(R.string.review_duplicate_title), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                    }
                    Text(stringResource(R.string.review_duplicate_body, dup.report.title, formatDate(dup.report.reportDate)), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                } }
            }
            if (state.saveFailed) item { MyHealthInfoBanner(stringResource(R.string.import_save_failed), icon = MyHealthIcons.StatusError) }
            if (state.pageFailed != null) item { MyHealthInfoBanner(stringResource(R.string.review_page_failed), icon = MyHealthIcons.StatusWarning) }
            if (state.showIssues && state.issues.isNotEmpty()) item { MyHealthInfoBanner(stringResource(R.string.review_fix_errors), icon = MyHealthIcons.StatusError) }

            // ---- Summary: what was found, for whom, when (the only thing most people need to look at)
            item {
                MyHealthCard(Modifier.testTag("reviewSummary"), emphasis = app.myhealth.core.designsystem.components.Emphasis.Medium) {
                    Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                        val n = d.labRows.size + d.vitals.size + d.medicines.size
                        Text(if (n == 0) stringResource(R.string.review_document_only) else pluralStringResource(R.plurals.review_found, n, n),
                            style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                        Text(listOfNotNull(label(d.type), d.labName.ifBlank { null }).joinToString(" · "), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.review_for, state.member?.name.orEmpty()), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                            if (state.members.size > 1) MyHealthTextButton(stringResource(R.string.review_change_person), { changePerson = true }, icon = MyHealthIcons.Family)
                        }
                        val dateErr = state.issuesFor(null).firstOrNull { it.error == app.myhealth.domain.importing.DraftError.MissingDate || it.error == app.myhealth.domain.importing.DraftError.FutureDate }
                        MyHealthDateField(stringResource(R.string.review_report_date), d.reportDate, { formatDate(it) }, stringResource(R.string.date_pick),
                            { onEvent(ImportEvent.SetDate(it)) }, stringResource(R.string.action_ok), stringResource(R.string.action_cancel), maxDate = today,
                            errorText = dateErr?.let { label(it.error) })
                        if (state.ocrUsed) Text(stringResource(R.string.import_ocr_note), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                        if (d.sources.isNotEmpty()) Text(pluralStringResource(R.plurals.review_files, d.sources.size, d.sources.size), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                            MyHealthTextButton(stringResource(R.string.review_add_page_file), onAddPageFile, Modifier.weight(1f).testTag("addPageFile"), icon = MyHealthIcons.Document)
                            MyHealthTextButton(stringResource(R.string.review_add_page_photo), onAddPagePhoto, Modifier.weight(1f), icon = MyHealthIcons.Camera)
                        }
                    }
                }
            }
            val needsCheck = d.labRows.count { it.include && (it.confidence != Confidence.High || it.warnings.isNotEmpty()) }
            if (needsCheck > 0) item { MyHealthInfoBanner(pluralStringResource(R.plurals.review_needs_check, needsCheck, needsCheck), icon = MyHealthIcons.StatusWarning) }
            if (d.labRows.isNotEmpty() || d.vitals.isNotEmpty() || d.medicines.isNotEmpty()) item {
                Text(stringResource(R.string.review_tap_to_edit), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
            }

            // ---- Values: one calm line each; the full editor opens on demand
            if (d.labRows.isNotEmpty()) item { MyHealthSectionHeader(stringResource(R.string.review_lab_values)) }
            items(d.labRows, key = { it.key }) { row ->
                val open = showDetails || editing == row.key || state.issuesFor(row.key).isNotEmpty()
                if (open) LabRowCard(row, state, onEvent, onChangeTest = { picking = row.key })
                else CompactRow(row.include, row.analyteId?.let { analyteName(it) } ?: row.printedName,
                    listOf(row.valueText, row.unit).filter { it.isNotBlank() }.joinToString(" "), row.confidence != Confidence.High || row.warnings.isNotEmpty(),
                    { v -> onEvent(ImportEvent.EditLab(row.key) { it.copy(include = v) }) }, { editing = row.key }, Modifier.testTag("labRow_${row.analyteId}"))
            }
            if (d.vitals.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.review_vitals)) }
                items(d.vitals, key = { it.key }) { v ->
                    if (showDetails || editing == v.key || state.issuesFor(v.key).isNotEmpty()) VitalRowCard(v, state, onEvent)
                    else CompactRow(v.include, label(v.type), app.myhealth.ui.common.vitalValue(v.type, app.myhealth.domain.parse.Numbers.parse(v.valueText) ?: 0.0,
                        app.myhealth.domain.parse.Numbers.parse(v.value2Text)), false, { x -> onEvent(ImportEvent.EditVital(v.key) { it.copy(include = x) }) }, { editing = v.key })
                }
            }
            if (d.medicines.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.review_medicines)) }
                items(d.medicines, key = { it.key }) { m ->
                    if (showDetails || editing == m.key || state.issuesFor(m.key).isNotEmpty()) MedicineRowCard(m, state, onEvent)
                    else CompactRow(m.include, m.name, listOf(m.strength, label(m.frequency)).filter { it.isNotBlank() }.joinToString(" · "), false,
                        { x -> onEvent(ImportEvent.EditMedicine(m.key) { it.copy(include = x) }) }, { editing = m.key })
                }
            }
            item {
                MyHealthTextButton(stringResource(if (showDetails) R.string.review_hide_details else R.string.review_show_details), { showDetails = !showDetails },
                    Modifier.fillMaxWidth().testTag("toggleDetails"), icon = MyHealthIcons.Expand)
            }
            if (showDetails) {
                item {
                    MyHealthCard {
                        Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                            Text(stringResource(R.string.review_details), style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                            d.patientName?.let { Text(stringResource(R.string.review_name_on_report, it), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary) }
                            MyHealthTextField(d.title, { onEvent(ImportEvent.SetTitle(it)) }, stringResource(R.string.review_report_title), supportingText = if (d.title.isBlank()) fallbackTitle else null)
                            MyHealthTextField(d.labName, { onEvent(ImportEvent.SetLab(it)) }, stringResource(R.string.review_lab))
                            MyHealthChoiceField(stringResource(R.string.review_type), ReportType.entries, d.type, { label(it) }, { onEvent(ImportEvent.SetType(it)) }, confirmLabel = stringResource(R.string.action_close))
                        }
                    }
                }
                item { MyHealthSecondaryButton(stringResource(R.string.review_add_value), { picking = "new" }, Modifier.fillMaxWidth().testTag("addValue"), icon = MyHealthIcons.Add) }
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
    if (changePerson) state.member?.let { cur ->
        app.myhealth.ui.shell.MemberPickerDialog(state.members, cur, { onEvent(ImportEvent.ChooseMember(it)) }, {}, {}) { changePerson = false }
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

/** One tidy line per extracted value: keep-tick, name, value, "check" glyph; tap opens the full editor. */
@Composable
private fun CompactRow(include: Boolean, name: String, value: String, needsCheck: Boolean, onInclude: (Boolean) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val editLabel = stringResource(R.string.review_edit_row, name)
    MyHealthCard(modifier, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyHealthCheckbox(include, onInclude, stringResource(R.string.review_include, name))
            Column(Modifier.weight(1f)) {
                Text(name, style = MyHealthTheme.typography.subtitle, color = if (include) c.textPrimary else c.textTertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (needsCheck) Text(label(app.myhealth.domain.parse.Confidence.Medium), style = MyHealthTheme.typography.caption, color = c.accent)
            }
            Text(value, style = MyHealthTheme.typography.metricSmall, color = if (include) c.textPrimary else c.textTertiary, maxLines = 1)
            Spacer(Modifier.width(MyHealthTheme.spacing.sm))
            Icon(if (needsCheck) MyHealthIcons.StatusWarning else MyHealthIcons.Edit, editLabel, tint = if (needsCheck) c.accent else c.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ChooseMemberContent(p: ImportPhase.ChooseMember, onEvent: (ImportEvent) -> Unit) {
    val sp = MyHealthTheme.spacing
    val clean = app.myhealth.domain.importing.NameMatcher.cleanName(p.printedName)
    LazyColumn(Modifier.fillMaxSize().testTag("chooseMember"), contentPadding = androidx.compose.foundation.layout.PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
        item {
            Text(stringResource(R.string.choose_member_title), style = MyHealthTheme.typography.headline, color = MyHealthTheme.colors.textPrimary)
            Text(stringResource(R.string.choose_member_body, p.printedName), style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary)
        }
        items(p.ranked, key = { it.member.id }) { r ->
            MyHealthCard(onClick = { onEvent(ImportEvent.ChooseMember(r.member.id)) }, modifier = Modifier.testTag("choose_${r.member.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app.myhealth.core.designsystem.components.MyHealthAvatar(r.member.initials, false)
                    Spacer(Modifier.width(sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(r.member.name, style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                        Text(label(r.member.relationship), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                    }
                    if (r == p.ranked.first() && r.score > 0.25) app.myhealth.core.designsystem.components.MyHealthStatusBadge(stringResource(R.string.choose_member_closest), app.myhealth.core.designsystem.components.StatusKind.Info)
                }
            }
        }
        if (clean.isNotBlank()) item {
            MyHealthPrimaryButton(stringResource(R.string.choose_member_add, clean), { onEvent(ImportEvent.AddMemberFromReport(clean)) }, Modifier.fillMaxWidth().testTag("addFromReport"), icon = MyHealthIcons.Add)
        }
    }
}

/** Item 14: one card per test date, oldest last; each opens the normal review, ready ones can be saved together. */
@Composable
private fun BatchContent(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    val sp = MyHealthTheme.spacing
    val fallbackTitle = label(ReportType.LabReport)
    val ready = state.batch.count { it.ready }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().testTag("batch"), contentPadding = androidx.compose.foundation.layout.PaddingValues(sp.gutter),
        verticalArrangement = Arrangement.spacedBy(sp.md)) {
        item {
            app.myhealth.core.designsystem.components.MyHealthCard(emphasis = app.myhealth.core.designsystem.components.Emphasis.Strong) {
                Text(stringResource(R.string.batch_banner_title).uppercase(), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textSecondary)
                Text(stringResource(R.string.batch_banner_body), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (state.batchFailedFiles > 0) item { MyHealthInfoBanner(pluralStringResource(R.plurals.batch_failed, state.batchFailedFiles, state.batchFailedFiles), icon = MyHealthIcons.StatusWarning) }
        if (state.saveFailed) item { MyHealthInfoBanner(stringResource(R.string.import_save_failed), icon = MyHealthIcons.StatusError) }
        items(state.batch.size) { i -> BatchCard(state.batch[i], onEvent, Modifier.appear(i)) }
        if (state.batch.any { it.date == null }) item { MyHealthInfoBanner(stringResource(R.string.batch_undated_note)) }
        if (ready > 0) item {
            MyHealthPrimaryButton(if (state.phase == ImportPhase.Saving) stringResource(R.string.saving) else pluralStringResource(R.plurals.batch_save_all, ready, ready),
                { onEvent(ImportEvent.SaveAllReady(fallbackTitle)) }, Modifier.fillMaxWidth().testTag("saveAllReady"), icon = MyHealthIcons.Check,
                enabled = state.phase != ImportPhase.Saving)
        }
    }
}

@Composable
private fun BatchCard(item: BatchItem, onEvent: (ImportEvent) -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val sp = MyHealthTheme.spacing
    app.myhealth.core.designsystem.components.MyHealthCard(modifier.testTag("batch_${item.index}"), onClick = if (item.saved) null else ({ onEvent(ImportEvent.ReviewGroup(item.index)) })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // date block: day + month, like a calendar leaf
            Column(
                Modifier.size(width = 54.dp, height = 58.dp).clip(MyHealthTheme.radii.mdShape)
                    .background(if (item.saved) c.surfaceSunken else c.hero),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val fg = if (item.saved) c.textSecondary else c.onHero
                if (item.date != null) {
                    Text(item.date.dayOfMonth.toString().padStart(2, '0'), style = MyHealthTheme.typography.title, color = fg)
                    Text(item.date.month.getDisplayName(java.time.format.TextStyle.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).uppercase(), style = MyHealthTheme.typography.overline, color = fg)
                } else Icon(MyHealthIcons.Help, null, tint = fg)
            }
            Spacer(Modifier.width(sp.md))
            Column(Modifier.weight(1f)) {
                Text(item.title.ifBlank { label(item.type) }, style = MyHealthTheme.typography.subtitle, color = c.textPrimary, maxLines = 2)
                val meta = listOfNotNull(
                    item.date?.let { app.myhealth.ui.common.formatDate(it) } ?: stringResource(R.string.batch_no_date),
                    item.lab.takeIf { it.isNotBlank() },
                    pluralStringResource(R.plurals.batch_values, item.values, item.values),
                ).joinToString(" · ")
                Text(meta, style = MyHealthTheme.typography.bodySmall, color = c.textSecondary)
                Text(item.memberName?.let { stringResource(R.string.batch_for, it) } ?: stringResource(R.string.batch_choose), style = MyHealthTheme.typography.caption,
                    color = if (item.memberName == null) c.accent else c.textSecondary)
            }
        }
        Text(pluralStringResource(R.plurals.batch_files, item.files.size, item.files.size) + ": " + item.files.joinToString(", "),
            style = MyHealthTheme.typography.caption, color = c.textTertiary, maxLines = 2, modifier = Modifier.padding(top = sp.sm))
        Row(Modifier.padding(top = sp.sm), verticalAlignment = Alignment.CenterVertically) {
            when {
                item.saved -> MyHealthStatusBadge(stringResource(R.string.batch_saved), app.myhealth.core.designsystem.components.StatusKind.Normal)
                item.ready -> MyHealthStatusBadge(stringResource(R.string.batch_ready), app.myhealth.core.designsystem.components.StatusKind.Normal)
                else -> MyHealthStatusBadge(if (item.issues > 0) pluralStringResource(R.plurals.batch_to_check, item.issues, item.issues) else stringResource(R.string.batch_choose),
                    app.myhealth.core.designsystem.components.StatusKind.Worsening)
            }
            Spacer(Modifier.weight(1f))
            if (!item.saved) MyHealthTextButton(stringResource(R.string.batch_review), { onEvent(ImportEvent.ReviewGroup(item.index)) }, Modifier.testTag("reviewGroup_${item.index}"))
        }
    }
}
