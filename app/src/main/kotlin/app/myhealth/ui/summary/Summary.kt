package app.myhealth.ui.summary

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthProgress
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.RangeStatus
import app.myhealth.domain.analytics.VitalBand
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.summary.DoctorSummary
import app.myhealth.domain.summary.DoctorSummaryBuilder
import app.myhealth.ui.LocalSession
import app.myhealth.ui.Session
import app.myhealth.ui.common.Share
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.analyteRange
import app.myhealth.ui.common.analyteValue
import app.myhealth.ui.common.focusText
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.label
import app.myhealth.ui.common.rememberVm
import app.myhealth.ui.common.vitalValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SummaryViewModel(session: Session) : ViewModel() {
    val summary: StateFlow<DoctorSummary?> = session.snapshot.map { DoctorSummaryBuilder.build(session.focusInputs(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** All text of the PDF, already localised (resolved in composition, rendered off the main thread). */
data class SummaryText(
    val title: String, val generated: String, val patientLine: String, val profileLines: List<String>,
    val sections: List<Pair<String, List<String>>>, val disclaimer: String, val pageLabel: (Int) -> String,
)

@Composable
fun summaryText(s: DoctorSummary): SummaryText {
    val m = s.member
    val none = stringResource(R.string.summary_none)
    fun orNone(l: List<String>) = l.ifEmpty { listOf(none) }
    val profile = listOfNotNull(
        m.dateOfBirth?.let { stringResource(R.string.field_dob) + ": " + formatDate(it) },
        stringResource(R.string.field_sex) + ": " + label(m.sex),
        m.bloodGroup.takeIf { it != app.myhealth.domain.model.BloodGroup.Unknown }?.let { stringResource(R.string.field_blood_group) + ": " + it.label },
        m.heightCm?.let { stringResource(R.string.field_height) + ": " + ValueFormat.compact(it, 1) },
        stringResource(R.string.field_allergies) + ": " + m.allergies.joinToString(", ").ifBlank { none },
        stringResource(R.string.field_conditions) + ": " + m.conditions.joinToString(", ").ifBlank { none },
        m.emergencyContact.takeIf { !it.isEmpty }?.let { stringResource(R.string.section_emergency) + ": " + listOf(it.name, it.relation, it.phone).filter { x -> x.isNotBlank() }.joinToString(", ") },
    )
    val meds = s.activeMedicines.map { md -> listOf(md.name, md.strength, md.dose, label(md.frequency), md.instructions).filter { it.isNotBlank() }.joinToString(" · ") }
    val labs = s.labs.map { t ->
        val prev = t.previous?.let { " (" + stringResource(R.string.summary_previous, analyteValue(t.analyte.id, it.value, it.comparator) + ", " + formatDate(it.date)) + ")" } ?: ""
        val range = analyteRange(t.analyte.id, t.latest.refLow, t.latest.refHigh).takeIf { it.isNotBlank() }?.let { " · " + stringResource(R.string.trend_range, it) } ?: ""
        "${analyteName(t.analyte.id)}: ${analyteValue(t.analyte.id, t.latest.value, t.latest.comparator)} — ${label(t.latest.status)}, ${formatDate(t.latest.date)}$range$prev"
    }
    val vitals = s.vitals.map { (v, b) -> "${label(v.type)}: ${vitalValue(v.type, v.value, v.value2, v.unit)}" + (if (b != VitalBand.Unknown) " — ${label(b)}" else "") }
    val focus = s.focus.map { f -> focusText(f).let { "${it.title}. ${it.body}" } }
    val treatments = s.treatments.map { t -> listOfNotNull(t.name, label(t.kind), formatDate(t.date), t.provider.ifBlank { null }, t.followUpDate?.let { stringResource(R.string.treat_followup_on, formatDate(it)) }).joinToString(" · ") }
    val res = androidx.compose.ui.platform.LocalResources.current
    return SummaryText(
        title = stringResource(R.string.summary_title), generated = stringResource(R.string.summary_generated, formatDate(s.generatedOn)),
        patientLine = listOfNotNull(m.name, s.ageYears?.let { stringResource(R.string.summary_age, it) }).joinToString(" · "),
        profileLines = profile,
        sections = listOf(
            stringResource(R.string.summary_focus) to orNone(focus), stringResource(R.string.summary_meds) to orNone(meds),
            stringResource(R.string.summary_labs) to orNone(labs), stringResource(R.string.summary_vitals) to orNone(vitals),
            stringResource(R.string.summary_treatments) to orNone(treatments),
        ),
        disclaimer = stringResource(R.string.disclaimer_short), pageLabel = { n -> res.getString(R.string.summary_page, n) },
    )
}

/**
 * A4 PDF via the platform PdfDocument: system fonts with HarfBuzz shaping render Hindi and Gujarati correctly
 * (PdfBox's built-in fonts cannot). Greyscale text with the single accent hue for section rules.
 */
object SummaryPdf {
    private const val W = 595; private const val H = 842; private const val M = 40

    fun render(t: SummaryText, accent: Int): ByteArray {
        val doc = PdfDocument()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFF141414.toInt() }
        val small = TextPaint(body).apply { textSize = 8.5f; color = 0xFF4D4D4D.toInt() }
        val h1 = TextPaint(body).apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val h2 = TextPaint(body).apply { textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val rule = Paint().apply { color = accent; strokeWidth = 1.5f }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun layout(text: String, p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, W - 2 * M).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build()
        fun finishPage() { page?.let { pg -> val f = layout(t.pageLabel(pageNo) + " · " + t.generated, small); pg.canvas.save(); pg.canvas.translate(M.toFloat(), (H - M + 8).toFloat()); f.draw(pg.canvas); pg.canvas.restore(); doc.finishPage(pg) } }
        fun newPage() { finishPage(); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); y = M.toFloat() }
        fun draw(text: String, p: TextPaint, gapAfter: Float = 4f) {
            val l = layout(text, p)
            if (page == null || y + l.height > H - M - 16) newPage()
            page!!.canvas.save(); page!!.canvas.translate(M.toFloat(), y); l.draw(page!!.canvas); page!!.canvas.restore()
            y += l.height + gapAfter
        }
        draw(t.title, h1, 2f)
        draw(t.patientLine, h2, 2f)
        draw(t.generated, small, 8f)
        t.profileLines.forEach { draw(it, body, 1f) }
        y += 6f
        t.sections.forEach { (title, lines) ->
            if (y + 40 > H - M - 16) newPage()
            draw(title, h2, 2f)
            page!!.canvas.drawLine(M.toFloat(), y, (W - M).toFloat(), y, rule); y += 6f
            lines.forEach { draw("•  $it", body, 2f) }
            y += 8f
        }
        draw(t.disclaimer, small)
        finishPage()
        return java.io.ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }
}

@Composable
fun SummaryScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { SummaryViewModel(session) }
    val s by vm.summary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val sp = MyHealthTheme.spacing
    val accent = MyHealthTheme.colors.accent
    val chooser = stringResource(R.string.share_chooser)
    Column(Modifier.fillMaxSize().testTag("summary")) {
        MyHealthTopBar(stringResource(R.string.summary_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
        val summary = s
        if (summary == null) { MyHealthLoadingState(stringResource(R.string.summary_preparing)); return@Column }
        val text = summaryText(summary)
        val csvLabels = csvLabels()
        Column(Modifier.padding(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            app.myhealth.core.designsystem.components.MyHealthCard {
                androidx.compose.material3.Text(stringResource(R.string.summary_intro, summary.member.name), style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary)
                summary.labs.firstOrNull()?.let { androidx.compose.material3.Text(text.sections.first().second.first(), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary) }
            }
            if (busy) MyHealthProgress(stringResource(R.string.summary_preparing), null)
            if (failed) MyHealthInfoBanner(stringResource(R.string.export_failed), icon = MyHealthIcons.StatusError)
            MyHealthPrimaryButton(stringResource(R.string.summary_pdf), {
                busy = true; failed = false
                scope.launch {
                    val ok = runCatching {
                        val bytes = withContext(Dispatchers.Default) { SummaryPdf.render(text, android.graphics.Color.argb((accent.alpha * 255).toInt(), (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())) }
                        Share.launch(context, Share.write(context, "${summary.member.name}-summary.pdf", bytes), "application/pdf", chooser)
                    }.isSuccess
                    busy = false; failed = !ok
                }
            }, Modifier.fillMaxWidth().testTag("sharePdf"), icon = MyHealthIcons.Pdf, enabled = !busy)
            MyHealthSecondaryButton(stringResource(R.string.summary_csv), {
                failed = runCatching {
                    Share.launch(context, Share.write(context, "${summary.member.name}-summary.csv", DoctorSummaryBuilder.csv(summary, csvLabels).toByteArray()), "text/csv", chooser)
                }.isFailure
            }, Modifier.fillMaxWidth().testTag("shareCsv"), icon = MyHealthIcons.Csv)
            MyHealthInfoBanner(stringResource(R.string.report_share_warning), icon = MyHealthIcons.Privacy)
            MyHealthDisclaimer(stringResource(R.string.disclaimer_short))
        }
    }
}


@Composable
fun csvLabels(): DoctorSummaryBuilder.CsvLabels {
    val statuses = RangeStatus.entries.associateWith { label(it) }
    val bands = VitalBand.entries.associateWith { label(it) }
    val vitals = VitalType.entries.associateWith { label(it) }
    val names = app.myhealth.domain.catalogue.AnalyteCatalogue.all.associate { it.id to analyteName(it.id) }
    return DoctorSummaryBuilder.CsvLabels(
        header = listOf(R.string.summary_section, R.string.summary_item, R.string.csv_value, R.string.csv_unit, R.string.csv_date, R.string.summary_note).map { stringResource(it) },
        profile = stringResource(R.string.summary_profile), medicine = stringResource(R.string.summary_medicine), lab = stringResource(R.string.summary_lab),
        vital = stringResource(R.string.summary_vital), treatment = stringResource(R.string.summary_treatment),
        analyteName = { names[it] ?: it }, vitalName = { vitals.getValue(it) }, status = { statuses.getValue(it) }, band = { bands.getValue(it) },
        allergies = stringResource(R.string.field_allergies), conditions = stringResource(R.string.field_conditions), bloodGroup = stringResource(R.string.field_blood_group),
        dob = stringResource(R.string.field_dob),
    )
}
