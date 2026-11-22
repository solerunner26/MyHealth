package app.myhealth.domain.importing

import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.model.Attachment
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.MedicineForm
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalSource
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.parse.Confidence
import app.myhealth.domain.parse.Numbers
import app.myhealth.domain.parse.ParseWarning
import app.myhealth.domain.parse.ParsedDocument
import app.myhealth.domain.repository.DuplicateHit
import app.myhealth.domain.repository.ImportBundle
import app.myhealth.domain.util.IdGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** An editable lab row. Text fields hold exactly what the user sees; conversion happens on save. */
data class LabRowDraft(
    val key: String,
    val include: Boolean,
    val analyteId: String?,
    val valueText: String,
    /** Canonical unit key the [valueText] and range are expressed in (catalogue unit or a convertible one). */
    val unit: String,
    val lowText: String,
    val highText: String,
    val comparator: Comparator = Comparator.Eq,
    val printedName: String = "",
    val printedValue: String = "",
    val printedUnit: String = "",
    val printedRange: String = "",
    val rawLine: String = "",
    val confidence: Confidence = Confidence.High,
    val warnings: Set<ParseWarning> = emptySet(),
    val manual: Boolean = false,
)

data class VitalDraft(val key: String, val include: Boolean, val type: VitalType, val valueText: String, val value2Text: String = "", val rawLine: String = "")

data class MedicineDraft(
    val key: String, val include: Boolean, val name: String, val strength: String, val form: MedicineForm,
    val frequency: Frequency, val durationText: String, val rawLine: String = "",
)

data class SourceFile(val displayName: String, val kind: AttachmentKind, val sha256: String, val sizeBytes: Long, val pageCount: Int, val ocrPages: Int)

data class ImportDraft(
    val memberId: String,
    val source: SourceFile?,
    val type: ReportType,
    val reportDate: LocalDate?,
    val title: String,
    val labName: String,
    val patientName: String?,
    val duplicateOf: DuplicateHit?,
    val labRows: List<LabRowDraft>,
    val vitals: List<VitalDraft>,
    val medicines: List<MedicineDraft>,
    /** Further pages/files added to the same record (e.g. several photos of a hospital file). */
    val extraSources: List<SourceFile> = emptyList(),
) {
    val sources get() = listOfNotNull(source) + extraSources
    val includedCount get() = labRows.count { it.include } + vitals.count { it.include } + medicines.count { it.include }
}

enum class DraftError { MissingDate, FutureDate, InvalidValue, UnknownAnalyte, UnitNotConvertible, RangeInverted, InvalidVital, MissingMedicineName, DuplicateAnalyte }

data class DraftIssue(val rowKey: String?, val error: DraftError)

object ImportDrafts {

    /** Builds the editable draft from parser output. Low-confidence rows start unticked so nothing doubtful is saved by default. */
    fun fromParsed(doc: ParsedDocument, member: FamilyMember, source: SourceFile?, duplicate: DuplicateHit?, defaultTitle: String = ""): ImportDraft {
        var n = 0
        fun key(prefix: String) = "$prefix${n++}"
        return ImportDraft(
            memberId = member.id, source = source, type = doc.type, reportDate = doc.reportDate,
            title = defaultTitle, labName = doc.labName.orEmpty(), patientName = doc.patientName, duplicateOf = duplicate,
            labRows = doc.labRows.map { r ->
                val useCanonical = r.canonicalUnit != null
                LabRowDraft(
                    key = key("lab"), include = r.confidence != Confidence.Low, analyteId = r.analyteId,
                    // Show the value in the canonical unit so ranges/trends are comparable; the printed text stays alongside.
                    valueText = r.canonicalValue?.let { fmt(it) } ?: r.value?.let { fmt(it) } ?: "",
                    unit = if (useCanonical) r.canonicalUnit!! else (r.analyteId?.let { AnalyteCatalogue.byId(it)?.canonicalUnit } ?: ""),
                    lowText = r.refLow?.let { fmt(it) } ?: "", highText = r.refHigh?.let { fmt(it) } ?: "",
                    comparator = r.comparator, printedName = r.printedName, printedValue = r.printedValue, printedUnit = r.printedUnit,
                    printedRange = r.printedRange, rawLine = r.rawLine, confidence = r.confidence, warnings = r.warnings,
                )
            },
            vitals = doc.vitals.map { v -> VitalDraft(key("vit"), true, v.type, fmt(v.value), v.value2?.let { fmt(it) } ?: "", v.rawLine) },
            medicines = doc.medicines.map { m ->
                MedicineDraft(key("med"), true, m.name, m.strength, m.form, m.frequency ?: Frequency.OnceDaily, m.durationDays?.toString() ?: "", m.rawLine)
            },
        )
    }

    fun emptyManual(member: FamilyMember, today: LocalDate, title: String = "") = ImportDraft(
        member.id, null, ReportType.LabReport, today, title, "", null, null, emptyList(), emptyList(), emptyList(),
    )

    fun newLabRow(key: String, analyteId: String): LabRowDraft {
        val a = AnalyteCatalogue.byId(analyteId)!!
        return LabRowDraft(key, true, analyteId, "", a.canonicalUnit, "", "", manual = true)
    }

    fun validate(d: ImportDraft, today: LocalDate): List<DraftIssue> {
        val out = mutableListOf<DraftIssue>()
        when {
            d.reportDate == null -> out += DraftIssue(null, DraftError.MissingDate)
            d.reportDate.isAfter(today) -> out += DraftIssue(null, DraftError.FutureDate)
        }
        val seen = mutableSetOf<String>()
        d.labRows.filter { it.include }.forEach { r ->
            val a = r.analyteId?.let { AnalyteCatalogue.byId(it) }
            if (a == null) { out += DraftIssue(r.key, DraftError.UnknownAnalyte); return@forEach }
            if (!seen.add(a.id)) out += DraftIssue(r.key, DraftError.DuplicateAnalyte)
            val v = Numbers.parse(r.valueText)
            if (v == null || v < 0) out += DraftIssue(r.key, DraftError.InvalidValue)
            if (a.conversionFor(r.unit) == null) out += DraftIssue(r.key, DraftError.UnitNotConvertible)
            val lo = r.lowText.takeIf { it.isNotBlank() }?.let { Numbers.parse(it) ?: -1.0 }
            val hi = r.highText.takeIf { it.isNotBlank() }?.let { Numbers.parse(it) ?: -1.0 }
            if ((lo != null && lo < 0) || (hi != null && hi < 0) || (lo != null && hi != null && lo > hi)) out += DraftIssue(r.key, DraftError.RangeInverted)
        }
        d.vitals.filter { it.include }.forEach { v ->
            val a = Numbers.parse(v.valueText)
            val b = if (v.type.paired) Numbers.parse(v.value2Text) else 0.0
            if (a == null || b == null || a <= 0 || (v.type.paired && a <= b!!)) out += DraftIssue(v.key, DraftError.InvalidVital)
        }
        d.medicines.filter { it.include }.forEach { m -> if (m.name.isBlank()) out += DraftIssue(m.key, DraftError.MissingMedicineName) }
        return out
    }

    /** Converts a validated draft to the rows that will be stored. Throws if [validate] would report an issue. */
    /**
     * Adds another page/file to the same record. Values already present are kept (the user may have edited them);
     * only new analytes, vitals and medicines are added. Missing date/lab/name are filled from the new page.
     */
    fun merge(d: ImportDraft, parsed: ParsedDocument, source: SourceFile): ImportDraft {
        val more = fromParsed(parsed, FamilyMember(d.memberId, "", app.myhealth.domain.model.Relationship.Other), source, null)
        val tag = "p${d.sources.size}-"
        val haveA = d.labRows.mapNotNull { it.analyteId }.toSet(); val haveV = d.vitals.map { it.type }.toSet(); val haveM = d.medicines.map { it.name.lowercase() }.toSet()
        return d.copy(
            extraSources = d.extraSources + source,
            reportDate = d.reportDate ?: more.reportDate, labName = d.labName.ifBlank { more.labName }, patientName = d.patientName ?: more.patientName,
            labRows = d.labRows + more.labRows.filter { it.analyteId == null || it.analyteId !in haveA }.map { it.copy(key = tag + it.key) },
            vitals = d.vitals + more.vitals.filter { it.type !in haveV }.map { it.copy(key = tag + it.key) },
            medicines = d.medicines + more.medicines.filter { it.name.lowercase() !in haveM }.map { it.copy(key = tag + it.key) },
        )
    }

    /**
     * Combines two drafts of the SAME test date into one record (files picked together): [b]'s files become extra
     * sources of [a]; values already present in [a] are kept from [a] (first file wins, like extra pages).
     */
    fun combine(a: ImportDraft, b: ImportDraft): ImportDraft {
        val tag = "f${a.sources.size}-"
        val haveA = a.labRows.mapNotNull { it.analyteId }.toSet(); val haveV = a.vitals.map { it.type }.toSet(); val haveM = a.medicines.map { it.name.lowercase() }.toSet()
        return a.copy(
            extraSources = a.extraSources + b.sources,
            reportDate = a.reportDate ?: b.reportDate, labName = a.labName.ifBlank { b.labName }, patientName = a.patientName ?: b.patientName,
            title = a.title.ifBlank { b.title }, duplicateOf = a.duplicateOf ?: b.duplicateOf,
            labRows = a.labRows + b.labRows.filter { it.analyteId == null || it.analyteId !in haveA }.map { it.copy(key = tag + it.key) },
            vitals = a.vitals + b.vitals.filter { it.type !in haveV }.map { it.copy(key = tag + it.key) },
            medicines = a.medicines + b.medicines.filter { it.name.lowercase() !in haveM }.map { it.copy(key = tag + it.key) },
        )
    }

    /**
     * Files picked together become one record per TEST DATE: drafts with the same printed date (and the same printed
     * patient, when both name one) merge; different dates stay separate. A file with no date found stays on its own.
     * Order follows the dates (newest first), files keep their pick order inside a group.
     */
    fun <T> groupByDate(items: List<Pair<ImportDraft, T>>): List<Pair<ImportDraft, List<T>>> {
        val groups = mutableListOf<Pair<ImportDraft, MutableList<T>>>()
        for ((d, tag) in items) {
            val i = if (d.reportDate == null) -1 else groups.indexOfFirst { (g, _) ->
                g.reportDate == d.reportDate && samePerson(g.patientName, d.patientName)
            }
            if (i < 0) groups += d to mutableListOf(tag)
            else { val (g, tags) = groups[i]; groups[i] = combine(g, d) to tags.also { it += tag } }
        }
        return groups.sortedWith(compareByDescending<Pair<ImportDraft, MutableList<T>>> { it.first.reportDate ?: LocalDate.MIN }).map { it.first to it.second.toList() }
    }

    private fun samePerson(a: String?, b: String?): Boolean =
        a.isNullOrBlank() || b.isNullOrBlank() || NameMatcher.tokens(a) == NameMatcher.tokens(b) || NameMatcher.score(a, FamilyMember("", b, app.myhealth.domain.model.Relationship.Other)) >= 0.8

    fun toBundle(d: ImportDraft, ids: IdGenerator, now: Instant, zone: ZoneId, today: LocalDate, fallbackTitle: String = "Report"): Pair<ImportBundle, List<Attachment>> {
        require(validate(d, today).isEmpty()) { "draft has validation issues" }
        val date = d.reportDate!!
        val reportId = ids.next()
        val attachments = d.sources.map { Attachment(ids.next(), reportId, it.kind, it.displayName, it.sha256, it.sizeBytes, it.pageCount) }
        val report = Report(reportId, d.memberId, d.type, d.title.trim().ifBlank { fallbackTitle }, date, d.labName.trim().ifBlank { null }, createdAt = now,
            attachments = attachments)
        val results = d.labRows.filter { it.include }.map { r ->
            val a = AnalyteCatalogue.byId(r.analyteId!!)!!
            val conv = a.conversionFor(r.unit)!!
            LabResult(
                id = ids.next(), memberId = d.memberId, reportId = reportId, analyteId = a.id, date = date,
                value = conv.toCanonical(Numbers.parse(r.valueText)!!), unit = a.canonicalUnit, comparator = r.comparator,
                refLow = r.lowText.takeIf { it.isNotBlank() }?.let { conv.toCanonical(Numbers.parse(it)!!) },
                refHigh = r.highText.takeIf { it.isNotBlank() }?.let { conv.toCanonical(Numbers.parse(it)!!) },
                printedName = r.printedName.ifBlank { a.name }, printedValue = r.printedValue.ifBlank { r.valueText },
                printedUnit = r.printedUnit.ifBlank { r.unit }, printedRange = r.printedRange,
            )
        }
        // Vitals extracted from a document are dated at local noon of the report date (time of day is rarely printed).
        val measuredAt = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val vitals = d.vitals.filter { it.include }.map { v ->
            Vital(ids.next(), d.memberId, v.type, Numbers.parse(v.valueText)!!, if (v.type.paired) Numbers.parse(v.value2Text) else null,
                measuredAt = measuredAt, source = VitalSource.Report, reportId = reportId)
        }
        val medicines = d.medicines.filter { it.include }.map { m ->
            val days = m.durationText.toIntOrNull()?.takeIf { it > 0 }
            Medicine(ids.next(), d.memberId, m.name.trim(), m.strength.trim(), m.form, m.frequency, startDate = date,
                endDate = days?.let { date.plusDays(it - 1L) }, reminderTimes = m.frequency.defaultTimes(), remindersEnabled = false,
                prescribedBy = "", reportId = reportId)
        }
        return ImportBundle(report, results, vitals, medicines) to attachments
    }

    private fun fmt(v: Double): String = app.myhealth.domain.format.ValueFormat.compact(v, 3)
}
