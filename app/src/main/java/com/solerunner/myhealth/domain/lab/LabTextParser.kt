package com.solerunner.myhealth.domain.lab

import com.solerunner.myhealth.core.util.Comparator
import com.solerunner.myhealth.core.util.Numbers
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.ValueStatus
import java.time.LocalDate
import java.util.Locale

/** One analyte line read out of a report, before the user reviews it. */
data class ParsedAnalyteRow(
    val biomarkerKey: String?,
    val displayName: String,
    val category: AnalyteCategory,
    val printedValue: Double? = null,
    val comparator: Comparator = Comparator.EQUAL,
    val printedUnit: String? = null,
    val canonicalValue: Double? = null,
    val canonicalUnit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val refText: String? = null,
    val status: ValueStatus = ValueStatus.UNKNOWN,
    val decimals: Int = 2,
    val confidence: Double = 0.5,
    val printedLine: String = "",
)

data class ParsedVitalRow(
    val kind: MeasurementKind,
    val primary: Double,
    val secondary: Double? = null,
    val unit: String? = null,
    val printedLine: String = "",
)

data class ParsedReportHeader(
    val title: String? = null,
    val labName: String? = null,
    val doctorName: String? = null,
    val facilityName: String? = null,
    val patientName: String? = null,
    val ageYears: Int? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val patientId: String? = null,
    val referenceNo: String? = null,
    val sampleDate: LocalDate? = null,
    val reportDate: LocalDate? = null,
    val kind: ReportKind = ReportKind.LAB,
)

/** Everything the document pipeline could understand from one file. */
data class ParsedDocument(
    val header: ParsedReportHeader,
    val analytes: List<ParsedAnalyteRow> = emptyList(),
    val vitals: List<ParsedVitalRow> = emptyList(),
    val warnings: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val textLength: Int = 0,
) {
    val isEmpty: Boolean get() = analytes.isEmpty() && vitals.isEmpty()
}

/**
 * Turns the text layer of a lab report (PDF text or on-device OCR) into structured rows.
 *
 * The parser is deliberately deterministic and rule based: no network, no model download, and every
 * decision can be explained to the user in the review screen ("read as: Haemoglobin, 13.5 g/dL").
 */
object LabTextParser {

    private val ignoreLinePatterns = listOf(
        "patient name", "name of patient", "age/sex", "age / sex", "ref by", "referred by", "referring",
        "method", "technique", "sample type", "specimen", "registered on", "printed on", "print date",
        "page ", "barcode", "uhid", "visit id", "hospital", "laboratory", "pathology", "biochemistry",
        "report status", "generated", "software", "nabl", "accredited", "correlation", "interpretation",
        "advice", "end of report", "disclaimer", "not for medico", "medico legal", "www.", "billing",
        "total amount", "gst", "received on", "doctor's signature", "authorised", "authorized",
        "kindly consult", "clinical significance", "prepared by", "verified by", "address", "phone",
        "customer care", "results are", "range of", "toll free", "email",
    )

    private val candidateRowPattern = Regex("[A-Za-z][^0-9]*\\d")

    fun parse(
        text: String,
        fileName: String? = null,
        dayFirst: Boolean = true,
        sex: Sex = Sex.UNSPECIFIED,
        ageYears: Int? = null,
    ): ParsedDocument {
        if (text.isBlank()) {
            return ParsedDocument(
                header = ParsedReportHeader(title = fileName?.substringBeforeLast('.')),
                warnings = listOf("EMPTY_TEXT"),
            )
        }

        val header = parseHeader(text, fileName, dayFirst)
        val resolvedSex = if (header.sex != Sex.UNSPECIFIED) header.sex else sex
        val resolvedAge = header.ageYears ?: ageYears
        val lines = text.lines().map { it.replace('\t', ' ').trim() }.filter { it.isNotBlank() }

        val rows = mutableListOf<ParsedAnalyteRow>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (shouldIgnore(line)) {
                index++
                continue
            }
            val normalized = BiomarkerCatalog.normalize(line)
            val match = BiomarkerCatalog.match(normalized)
            if (match != null) {
                // Look-ahead: tables very often print the value or the reference interval on the
                // following line (and sometimes the unit on the line before).
                val lookAhead = (index + 1..minOf(index + 2, lines.size - 1))
                    .filter { !shouldIgnore(lines[it]) }
                    .joinToString(" ") { lines[it] }
                val row = parseRow(line, lookAhead, match, resolvedSex, resolvedAge)
                if (row != null && row.printedValue != null) {
                    rows += row
                    index++
                    continue
                }
                if (row != null && candidateRowPattern.containsMatchIn(line)) {
                    // Alias recognised but no number close by: keep a low-confidence row so the
                    // review screen can say "value could not be read".
                    rows += row.copy(confidence = 0.2)
                }
            }
            index++
        }

        val distinct = rows
            .filter { it.displayName.isNotBlank() }
            .distinctBy { it.biomarkerKey ?: it.displayName.lowercase(Locale.ROOT) }

        val vitals = com.solerunner.myhealth.domain.vitals.VitalExtractor.extract(lines.joinToString("\n"))

        return ParsedDocument(
            header = header,
            analytes = distinct.sortedWith(
                compareBy({ it.category.ordinal }, { it.biomarkerKey ?: it.displayName }),
            ),
            vitals = vitals,
            warnings = buildList {
                if (header.sampleDate == null) add("NO_DATE")
                if (distinct.isEmpty()) add("NO_ANALYTES")
                if (distinct.any { it.confidence < 0.5 }) add("LOW_CONFIDENCE_ROWS")
            },
            confidence = scoreConfidence(header, distinct, text),
            textLength = text.length,
        )
    }

    private fun shouldIgnore(line: String): Boolean {
        val l = line.lowercase(Locale.ROOT)
        if (l.length <= 2) return true
        return ignoreLinePatterns.any { it in l }
    }

    // ---------------------------------------------------------------------------------------
    // Row parsing
    // ---------------------------------------------------------------------------------------

    private data class Reading(
        val value: Double,
        val comparator: Comparator,
        val unit: String?,
    )

    private val valuePattern = Regex("[<>]?\\s*\\d{1,3}(?:[ ,]\\d{3})+(?:[.,]\\d+)?|[<>]?\\s*\\d+(?:[.,]\\d+)?")

    private val unitPattern = Regex("^[a-z%µμ][a-z0-9%µμ/^.*]{0,11}$")

    private val stopWords = setOf(
        "h", "l", "n", "high", "low", "normal", "abnormal", "ref", "reference", "range", "bio",
        "method", "result", "units", "unit", "flag", "value", "positive", "negative", "nil", "na",
        "and", "to", "or", "of", "the", "in", "is", "at", "on", "by", "s", "no",
    )

    private fun parseRow(
        line: String,
        lookAhead: String,
        match: BiomarkerCatalog.Match,
        sex: Sex,
        age: Int?,
    ): ParsedAnalyteRow? {
        val biomarker = match.biomarker
        val normalized = BiomarkerCatalog.normalize(line)
        val remainderStart = match.end.coerceIn(0, normalized.length)
        val remainder = normalized.substring(remainderStart)
        val lookNormalized = BiomarkerCatalog.normalize(lookAhead)

        val inlineReading = readValue(remainder)
        val reading = inlineReading ?: readValue(lookNormalized)

        val printedRange = findRange(remainder + " " + lookNormalized)
        val catalogRange = biomarker.rangeFor(sex, age)

        val printedValue = reading?.value
        val unit = reading?.unit
        val canonicalValue = printedValue?.let { biomarker.toCanonical(it, unit) }

        val effectiveLow = printedRange?.low ?: catalogRange?.low
        val effectiveHigh = printedRange?.high ?: catalogRange?.high
        val status = when (readFlag(remainder)) {
            Flag.HIGH -> ValueStatus.HIGH
            Flag.LOW -> ValueStatus.LOW
            Flag.NONE -> classify(canonicalValue, effectiveLow, effectiveHigh, biomarker)
        }

        val confidence = when {
            reading == null -> 0.25
            unit != null && printedRange?.low != null -> 0.95
            unit != null || printedRange != null -> 0.85
            else -> 0.6
        }

        return ParsedAnalyteRow(
            biomarkerKey = biomarker.key,
            displayName = biomarker.displayName,
            category = biomarker.category,
            printedValue = printedValue,
            comparator = reading?.comparator ?: Comparator.EQUAL,
            printedUnit = unit,
            canonicalValue = canonicalValue,
            canonicalUnit = biomarker.canonicalUnit,
            refLow = effectiveLow,
            refHigh = effectiveHigh,
            refText = printedRange?.text,
            status = status,
            decimals = biomarker.decimals,
            confidence = confidence,
            printedLine = line.trim(),
        )
    }
    private fun readValue(text: String): Reading? {
        val match = valuePattern.find(text) ?: return null
        val parsed = Numbers.parse(match.value) ?: return null
        val afterIndex = match.range.last + 1
        val after = text.substring(afterIndex, minOf(afterIndex + 16, text.length)).trimStart()
        return Reading(parsed.value, parsed.comparator, detectUnit(after))
    }

    private fun detectUnit(after: String): String? {
        val stem = after.removePrefix("-").removePrefix("–").trimStart()
        if (stem.startsWith("to ") || stem.startsWith("ref") || stem.startsWith("(")) return null
        val token = stem.takeWhile { !it.isWhitespace() }.trim('(', ')', '[', ']', ':', ',', ';')
        if (token.isEmpty() || token.length > 12) return null
        if (token.lowercase(Locale.ROOT) in stopWords) return null
        if (token.first().isDigit()) return null
        if (!unitPattern.matches(token)) return null
        val normalized = normalizeUnitToken(token) ?: return null
        return displayUnit(normalized)
    }

    /** normalized (lower-case, µ→u) token → the way laboratories actually print it. */
    private val unitDisplay: Map<String, String> = mapOf(
        "g/dl" to "g/dL",
        "mg/dl" to "mg/dL",
        "mmol/l" to "mmol/L",
        "umol/l" to "µmol/L",
        "miu/l" to "mIU/L",
        "uiu/ml" to "µIU/mL",
        "iu/ml" to "IU/mL",
        "ng/ml" to "ng/mL",
        "pg/ml" to "pg/mL",
        "u/l" to "U/L",
        "iu/l" to "IU/L",
        "meq/l" to "mEq/L",
        "mmhg" to "mmHg",
        "ml/min/1.73m2" to "mL/min/1.73m²",
        "/ul" to "/µL",
        "fl" to "fL",
    )

    private fun displayUnit(normalized: String): String = unitDisplay[normalized] ?: normalized


    private data class Range(val low: Double?, val high: Double?, val text: String?)

    private val rangePattern = Regex("(\\d{1,4}(?:[.,]\\d+)?)\\s*(?:-|–|—|to)\\s*(\\d{1,4}(?:[.,]\\d+)?)")
    private val lessThanPattern = Regex("(?:<|less than)\\s*(\\d{1,4}(?:[.,]\\d+)?)")
    private val greaterThanPattern = Regex("(?:>|greater than)\\s*(\\d{1,4}(?:[.,]\\d+)?)")

    private fun findRange(text: String): Range? {
        val ranges = rangePattern.findAll(text).toList()
        if (ranges.isNotEmpty()) {
            val labelled = ranges.lastOrNull { candidate ->
                val start = maxOf(0, candidate.range.first - 24)
                val before = text.substring(start, candidate.range.first).lowercase(Locale.ROOT)
                listOf("ref", "normal", "range", "bio", "interval", "expected").any { it in before }
            }
            val chosen = labelled ?: ranges.last()
            val low = Numbers.parse(chosen.groupValues[1])?.value
            val high = Numbers.parse(chosen.groupValues[2])?.value
            if (low != null && high != null && high >= low) return Range(low, high, chosen.value)
        }
        lessThanPattern.find(text)?.let { lessThan ->
            val high = Numbers.parse(lessThan.groupValues[1])?.value
            if (high != null) return Range(null, high, "<" + lessThan.groupValues[1])
        }
        greaterThanPattern.find(text)?.let { greater ->
            val low = Numbers.parse(greater.groupValues[1])?.value
            if (low != null) return Range(low, null, ">" + greater.groupValues[1])
        }
        return null
    }

    private enum class Flag { NONE, HIGH, LOW }

    private fun readFlag(text: String): Flag {
        val t = " " + text.lowercase(Locale.ROOT).replace("*", " ") + " "
        val highTokens = listOf(" high ", " ↑ ", " h ", " abnormal ", " above ")
        val lowTokens = listOf(" low ", " ↓ ", " l ", " below ", " deficient ")
        return when {
            highTokens.any { it in t } -> Flag.HIGH
            lowTokens.any { it in t } -> Flag.LOW
            else -> Flag.NONE
        }
    }

    private fun classify(value: Double?, low: Double?, high: Double?, biomarker: Biomarker): ValueStatus {
        if (value == null) return ValueStatus.UNKNOWN
        if (low == null && high == null) return ValueStatus.UNKNOWN
        if (low != null && value < low) {
            val critical = biomarker.criticalLow != null && value <= biomarker.criticalLow
            return if (critical) ValueStatus.VERY_LOW else ValueStatus.LOW
        }
        if (high != null && value > high) {
            val critical = biomarker.criticalHigh != null && value >= biomarker.criticalHigh
            return if (critical) ValueStatus.VERY_HIGH else ValueStatus.HIGH
        }
        return ValueStatus.IN_RANGE
    }


    // ---------------------------------------------------------------------------------------
    // Header (patient, lab, dates, document kind)
    // ---------------------------------------------------------------------------------------

    private val doctorPattern = Regex("\\b(?:dr|doctor)\\.?\\s+([A-Z][A-Za-z.]*(?:\\s+[A-Z][A-Za-z.]*){0,3})")
    private val labHintPattern = Regex("(?i)(lab|laborator|diagnostic|pathology|healthcare|hospital|clinic|institute|medical centre|medical center)")
    private val ageSexPattern = Regex("(?i)\\b(\\d{1,3})\\s*(?:y|yr|yrs|years)?\\s*[/,\\-]\\s*(male|female|m|f)\\b")
    private val ageOnlyPattern = Regex("(?i)\\bage\\b\\D{0,4}(\\d{1,3})")
    private val idPattern = Regex(
        "(?i)\\b(?:patient\\s*id|uhid|pid|reg(?:istration)?\\s*(?:no|number)?|lab\\s*(?:no|id)|sample\\s*id|barcode|accession)\\b\\D{0,6}([A-Za-z0-9\\-/]{3,20})",
    )

    private fun parseHeader(text: String, fileName: String?, dayFirst: Boolean): ParsedReportHeader {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val head = lines.take(30)

        val labName = head.firstOrNull { line ->
            line.length in 4..90 &&
                labHintPattern.containsMatchIn(line) &&
                line.count { it.isLetter() } >= 4 &&
                !line.lowercase(Locale.ROOT).startsWith("department") &&
                !line.lowercase(Locale.ROOT).startsWith("patient")
        }?.take(90)

        val title = head.firstOrNull { line ->
            line.length in 6..60 &&
                line.count { it.isLetter() } >= 6 &&
                !labHintPattern.containsMatchIn(line) &&
                !line.contains(':') &&
                DateTextParser.findFirst(line, dayFirst) == null
        } ?: fileName?.substringBeforeLast('.')?.replace('_', ' ')?.take(60)

        val doctor = doctorPattern.find(text)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.length in 3..60 }

        val patientName = head.firstNotNullOfOrNull { line ->
            val l = line.lowercase(Locale.ROOT)
            val hasNameLabel = "name" in l && "lab" !in l && "doctor" !in l && "hospital" !in l
            if (hasNameLabel && line.contains(':')) {
                val value = line.substringAfter(':').trim()
                value.takeIf { it.length in 2..60 && it.count { c -> c.isLetter() } >= 3 }
            } else {
                null
            }
        }

        val ageSexMatch = ageSexPattern.find(text)
        val age = ageSexMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: ageOnlyPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..120 }
        val sex = when (ageSexMatch?.groupValues?.get(2)?.lowercase(Locale.ROOT)) {
            "male", "m" -> Sex.MALE
            "female", "f" -> Sex.FEMALE
            else -> when {
                Regex("(?i)\\bfemale\\b").containsMatchIn(text) -> Sex.FEMALE
                Regex("(?i)\\bmale\\b").containsMatchIn(text) -> Sex.MALE
                else -> Sex.UNSPECIFIED
            }
        }

        val id = idPattern.find(text)?.groupValues?.get(1)

        return ParsedReportHeader(
            title = title,
            labName = labName,
            doctorName = doctor,
            facilityName = labName,
            patientName = patientName,
            ageYears = age?.takeIf { it in 0..120 },
            sex = sex,
            patientId = id,
            referenceNo = id,
            sampleDate = DateTextParser.findSampleDate(text, dayFirst),
            reportDate = DateTextParser.findReportDate(text, dayFirst),
            kind = detectKind(text),
        )
    }

    private val billHintPattern = Regex("(?i)\\b(pharmacy|invoice|bill\\s*no|mrp|batch\\s*no|gstin|chemist)\\b")
    private val prescriptionHintPattern = Regex("(?i)\\b(prescription|advice|dosage)\\b|sig:")
    private val imagingHintPattern = Regex("(?i)\\b(impression|x-ray|xray|ultrasound|sonography|mri|doppler)\\b|ct\\s*scan")
    private val dischargeHintPattern = Regex("(?i)\\b(discharge\\s*summary|inpatient)\\b|admitted\\s+on")

    /**
     * Decides what kind of document this is. Keywords use word boundaries: "biochemistry" contains
     * the letters of "chemist" but is not a pharmacy bill, and mislabelling a lab report as a bill
     * would send its analytes down the medicine import path.
     */
    private fun detectKind(text: String): ReportKind {
        val l = text.lowercase(Locale.ROOT)
        val labLike = "haemoglobin" in l || "hemoglobin" in l || "creatinine" in l || "hba1c" in l
        return when {
            billHintPattern.containsMatchIn(l) && !labLike -> ReportKind.PHARMACY_BILL
            prescriptionHintPattern.containsMatchIn(l) && !labLike -> ReportKind.PRESCRIPTION
            imagingHintPattern.containsMatchIn(l) -> ReportKind.IMAGING
            dischargeHintPattern.containsMatchIn(l) -> ReportKind.DISCHARGE
            labLike -> ReportKind.LAB
            else -> ReportKind.OTHER
        }
    }

    private fun scoreConfidence(header: ParsedReportHeader, rows: List<ParsedAnalyteRow>, text: String): Double {
        if (text.length < 40) return 0.15
        var score = 0.25
        if (header.sampleDate != null) score += 0.2
        if (header.labName != null) score += 0.1
        if (rows.size >= 3) score += 0.2
        if (rows.size >= 8) score += 0.1
        if (rows.isNotEmpty()) score += 0.05 * (rows.count { it.confidence >= 0.8 }.toDouble() / rows.size)
        if (rows.any { it.confidence < 0.5 }) score -= 0.1
        return score.coerceIn(0.0, 0.99)
    }
}
