package app.myhealth.ui.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.Safe
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthErrorState
import app.myhealth.core.designsystem.components.MyHealthIconButton
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.TrendEngine
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.model.Attachment
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.Vital
import app.myhealth.ui.Session
import app.myhealth.ui.common.Share
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.analyteRange
import app.myhealth.ui.common.analyteValue
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.kind
import app.myhealth.ui.common.label
import app.myhealth.ui.common.vitalValue
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportDetailState(
    val loading: Boolean = true,
    val report: Report? = null,
    val results: List<LabResult> = emptyList(),
    val vitals: List<Vital> = emptyList(),
    val deleted: Boolean = false,
    val error: Boolean = false,
)

class ReportDetailViewModel(private val session: Session, private val reportId: String) : ViewModel() {
    private val _state = MutableStateFlow(ReportDetailState())
    val state: StateFlow<ReportDetailState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch(Safe.handler) {
        runCatching {
            val r = session.stack.records.report(reportId)
            val results = session.stack.records.resultsForReport(reportId)
            val vitals = r?.let { session.stack.vitals.all(it.memberId).filter { v -> v.reportId == reportId } }.orEmpty()
            ReportDetailState(false, r, results, vitals)
        }.onSuccess { _state.value = it }.onFailure { _state.value = ReportDetailState(loading = false, error = true) }
    }

    fun delete() = viewModelScope.launch(Safe.handler) {
        runCatching { session.stack.records.deleteReport(reportId) }.onSuccess { _state.update { it.copy(deleted = true) } }.onFailure { _state.update { it.copy(error = true) } }
    }

    suspend fun bytes(a: Attachment): ByteArray? = withContext(Dispatchers.IO) { runCatching { session.stack.records.attachmentBytes(a.id) }.getOrNull() }
}

@Composable
fun ReportDetailScreen(
    state: ReportDetailState,
    onBack: () -> Unit,
    onView: (Attachment) -> Unit,
    onShare: (Attachment) -> Unit,
    onDelete: () -> Unit,
    onOpenTrend: (String) -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize().testTag("reportDetail")) {
        val r = state.report
        MyHealthTopBar(r?.title ?: stringResource(R.string.records_tab_reports), subtitle = r?.let { stringResource(R.string.join_dot, label(it.type), formatDate(it.reportDate)) },
            onBack = onBack, backLabel = stringResource(R.string.action_back)) {
            if (r != null) MyHealthIconButton(MyHealthIcons.Delete, stringResource(R.string.report_delete), { confirm = true })
        }
        when {
            state.loading -> MyHealthLoadingState(stringResource(R.string.state_loading))
            state.error -> MyHealthErrorState(stringResource(R.string.state_error_title), stringResource(R.string.state_error_body))
            r == null -> MyHealthEmptyState(MyHealthIcons.Document, stringResource(R.string.report_not_found), stringResource(R.string.records_empty_body))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                r.labName?.let { item { MyHealthInfoBanner(it, icon = MyHealthIcons.Labs) } }
                if (r.attachments.isNotEmpty()) {
                    item { MyHealthSectionHeader(stringResource(R.string.report_attachments)) }
                    items(r.attachments, key = { it.id }) { a ->
                        MyHealthCard {
                            MyHealthListRow(a.displayName, subtitle = pluralStringResource(R.plurals.records_pages, a.pageCount, a.pageCount),
                                leadingIcon = if (a.kind == AttachmentKind.Pdf) MyHealthIcons.Pdf else MyHealthIcons.Camera)
                            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                                MyHealthPrimaryButton(stringResource(R.string.report_view), { onView(a) }, Modifier.weight(1f).testTag("viewAttachment"), icon = MyHealthIcons.Document)
                                MyHealthTextButton(stringResource(R.string.report_share_file), { onShare(a) }, Modifier.weight(1f), icon = MyHealthIcons.Share)
                            }
                            MyHealthInfoBanner(stringResource(R.string.report_share_warning), icon = MyHealthIcons.Privacy)
                        }
                    }
                }
                item { MyHealthSectionHeader(stringResource(R.string.report_values)) }
                if (state.results.isEmpty() && state.vitals.isEmpty()) item { MyHealthInfoBanner(stringResource(R.string.report_no_values)) }
                items(state.results, key = { it.id }) { res ->
                    val a = AnalyteCatalogue.byId(res.analyteId)
                    val status = a?.let { TrendEngine.status(it, res.value, res.refLow, res.refHigh) }
                    MyHealthCard(onClick = { onOpenTrend(res.analyteId) }, modifier = Modifier.testTag("result_${res.analyteId}")) {
                        MyHealthListRow(analyteName(res.analyteId),
                            subtitle = listOfNotNull(analyteValue(res.analyteId, res.value, res.comparator),
                                analyteRange(res.analyteId, res.refLow, res.refHigh).takeIf { it.isNotBlank() }?.let { stringResource(R.string.trend_range, it) }).joinToString("  ")) {
                            if (status != null) MyHealthStatusBadge(label(status), status.kind())
                        }
                        if (res.printedValue.isNotBlank()) Text(stringResource(R.string.report_printed, listOf(res.printedName, res.printedValue, res.printedUnit, res.printedRange).filter { it.isNotBlank() }.joinToString(" ")),
                            style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary)
                    }
                }
                items(state.vitals, key = { it.id }) { v ->
                    MyHealthCard { MyHealthListRow(label(v.type), subtitle = vitalValue(v.type, v.value, v.value2, v.unit), leadingIcon = MyHealthIcons.Vitals) }
                }
                item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
            }
        }
    }
    if (confirm) MyHealthDialog(stringResource(R.string.report_delete_title), stringResource(R.string.report_delete_body), stringResource(R.string.action_delete),
        { confirm = false; onDelete() }, stringResource(R.string.action_cancel), { confirm = false }, icon = MyHealthIcons.Delete)
}

/** Renders a decrypted attachment entirely in memory (no plaintext file). */
class AttachmentViewerViewModel(private val session: Session, private val attachmentId: String) : ViewModel() {
    data class State(val loading: Boolean = true, val pages: List<Bitmap> = emptyList(), val total: Int = 0, val error: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(Safe.handler) {
            _state.value = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = session.stack.records.attachmentBytes(attachmentId)
                    when (app.myhealth.ingest.DocumentSniffer.kind(bytes)) {
                        AttachmentKind.Pdf -> {
                            PDDocument.load(bytes).use { doc ->
                                val r = PDFRenderer(doc)
                                val n = doc.numberOfPages
                                State(false, (0 until minOf(n, MAX_PAGES)).map { r.renderImageWithDPI(it, 110f) }, n)
                            }
                        }
                        AttachmentKind.Jpeg, AttachmentKind.Png, AttachmentKind.Webp, AttachmentKind.Heic -> {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            State(false, listOfNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)), 1)
                        }
                        null -> State(false, error = true)
                    }
                }.getOrElse { State(false, error = true) }
            }
        }
    }

    override fun onCleared() { _state.value.pages.forEach { runCatching { it.recycle() } } }

    companion object { const val MAX_PAGES = 30 }
}

@Composable
fun AttachmentViewerScreen(state: AttachmentViewerViewModel.State, title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag("viewer")) {
        MyHealthTopBar(title, onBack = onBack, backLabel = stringResource(R.string.action_back))
        when {
            state.loading -> MyHealthLoadingState(stringResource(R.string.viewer_loading))
            state.error -> MyHealthErrorState(stringResource(R.string.viewer_error), stringResource(R.string.state_error_body))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MyHealthTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
                itemsIndexed(state.pages) { i, bmp ->
                    Column {
                        Text(stringResource(R.string.viewer_page, i + 1, state.total), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary)
                        Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.viewer_page, i + 1, state.total), modifier = Modifier.fillMaxWidth()
                            .background(MyHealthTheme.colors.surface), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}

/** Decrypts to the share cache and opens the share sheet. */
suspend fun shareAttachment(context: android.content.Context, vm: ReportDetailViewModel, a: Attachment, chooserTitle: String): Boolean {
    val bytes = vm.bytes(a) ?: return false
    return runCatching {
        val f = withContext(Dispatchers.IO) { Share.write(context, a.displayName, bytes) }
        Share.launch(context, f, Share.mimeFor(a.kind), chooserTitle)
    }.isSuccess
}

