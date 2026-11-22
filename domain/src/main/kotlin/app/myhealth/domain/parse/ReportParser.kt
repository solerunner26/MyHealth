package app.myhealth.domain.parse

import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Sex
import java.time.LocalDate

/**
 * Turns extracted page text into structured candidate rows for the review screen. Deterministic: same text in,
 * same rows out. Nothing is saved from here; the user confirms every row.
 */
class ReportParser(private val today: LocalDate? = null) {

    fun parse(pages: List<String>, sex: Sex? = null, ageYears: Int? = null): ParsedDocument {
        val lines = TextNormalizer.lines(pages)
        val lab = LabLineParser(sex, ageYears)
        val rows = mutableListOf<ParsedLabRow>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val next = lines.getOrNull(i + 1)?.takeIf { startsWithValue(it) }
            val row = lab.parse(line, next)
            if (row != null) {
                rows += row
                if (ParseWarning.ValueOnNextLine in row.warnings) i++
            }
            i++
        }
        // one row per analyte per report: keep the most confident (then first) occurrence
        val deduped = rows.groupBy { it.analyteId }.values.map { g -> g.minWith(compareBy<ParsedLabRow> { it.confidence.ordinal }) }
            .sortedBy { rows.indexOf(it) }

        val vitals = VitalsParser.parse(lines)
        val medicines = MedicineParser.parse(lines)
        val type = DocumentClassifier.classify(lines, deduped.size, medicines.size)
        return ParsedDocument(
            type = type,
            reportDate = DateParser.reportDate(lines, today),
            labName = labName(lines),
            patientName = patientName(lines),
            labRows = deduped,
            vitals = vitals,
            medicines = if (type == ReportType.LabReport && deduped.size > medicines.size) emptyList() else medicines,
            pageCount = pages.size,
            lineCount = lines.size,
        )
    }

    private fun startsWithValue(s: String) = Regex("""^\s*(<|>|<=|>=|≤|≥)?\s*\d""").containsMatchIn(s)

    private val nameRe = Regex("""(?:patient(?:'s)?\s*name|name\s*of\s*patient|pt\.?\s*name|^name)\s*[:\-]\s*(?:mr|mrs|ms|miss|master|baby|dr|smt|shri)?\.?\s*([A-Za-z][A-Za-z .']{1,60}?)(?=\s{2,}|\s+(?:age|sex|gender|uhid|id|dob|ref|date|mrn)\b|$)""", RegexOption.IGNORE_CASE)

    fun patientName(lines: List<String>): String? = lines.firstNotNullOfOrNull { l -> nameRe.find(l)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 } }

    private val labRe = Regex("""(laborator(y|ies)|diagnostic|pathology|path lab|labs\b|hospital|clinic|healthcare|medical centre|medical center|imaging|scan centre)""", RegexOption.IGNORE_CASE)

    fun labName(lines: List<String>): String? = lines.take(8).firstOrNull { labRe.containsMatchIn(it) && it.length <= 80 && !it.contains(':') }
}

object DocumentClassifier {
    private val imaging = Regex("""(ultrasound|usg|sonography|x-ray|xray|radiograph|mri|ct scan|computed tomography|impression|echocardiograph|doppler|mammogra)""", RegexOption.IGNORE_CASE)
    private val bill = Regex("""(invoice|bill no|gstin|gst|mrp|batch|qty|amount|total amount|net amount|pharmacy|chemist|drug lic)""", RegexOption.IGNORE_CASE)
    private val rx = Regex("""(\brx\b|℞|prescription|advice|adv\.|diagnosis|follow up|review after|c/o|chief complaint)""", RegexOption.IGNORE_CASE)
    private val discharge = Regex("""(discharge summary|date of admission|date of discharge|course in hospital)""", RegexOption.IGNORE_CASE)
    private val lab = Regex("""(reference range|biological ref|ref\. range|normal range|specimen|sample type|units|investigation|test name|result)""", RegexOption.IGNORE_CASE)

    fun classify(lines: List<String>, labRows: Int, medicines: Int): ReportType {
        fun score(re: Regex) = lines.count { re.containsMatchIn(it) }
        val s = mapOf(
            ReportType.Discharge to score(discharge) * 3,
            ReportType.Imaging to score(imaging) * 2,
            ReportType.PharmacyBill to score(bill) + if (medicines > 0) 1 else 0,
            ReportType.Prescription to score(rx) * 2 + medicines * 2,
            ReportType.LabReport to score(lab) + labRows * 2,
        )
        val best = s.maxBy { it.value }
        return if (best.value == 0) ReportType.Other else best.key
    }
}
