package com.solerunner.myhealth.domain.medicine

import com.solerunner.myhealth.core.util.Numbers
import com.solerunner.myhealth.domain.model.MedicineForm
import java.util.Locale

/** One medicine line read from a pharmacy bill, invoice or prescription. */
data class ParsedMedicineRow(
    val name: String,
    val molecule: String? = null,
    val form: MedicineForm = MedicineForm.TABLET,
    val strength: String? = null,
    val frequencyText: String? = null,
    val timesPerDay: Int? = null,
    val durationDays: Int? = null,
    val printedLine: String = "",
    val confidence: Double = 0.6,
)

/**
 * Reads medicines out of a pharmacy bill / prescription photo.
 *
 * Pharmacy bills are noisy: batch numbers, expiry dates, MRP, HSN codes and taxes all contain digits
 * and they all look like dosage text to a naive parser. The strategy here is:
 *   1. find a formulation keyword (TAB/CAP/SYR/INJ…) or a known brand name — without one, the line is
 *      not a medicine line at all;
 *   2. read the strength, the frequency code and the duration from what surrounds it;
 *   3. map the brand to its molecule using a local list of common Indian brands, so the app can warn
 *      about the same molecule appearing in two prescriptions.
 */
object MedicineBillParser {

    private val formKeywords: List<Pair<Regex, MedicineForm>> = listOf(
        Regex("(?i)\\b(tab|tabs|tablet|tablets|tb)\\.?\\b") to MedicineForm.TABLET,
        Regex("(?i)\\b(cap|caps|capsule|capsules|cp)\\.?\\b") to MedicineForm.CAPSULE,
        Regex("(?i)\\b(syr|syp|syrup|susp|suspension|liq|liquid|oral solution)\\.?\\b") to MedicineForm.SYRUP,
        Regex("(?i)\\b(inj|injection|vial|amp|ampoule|iv|im)\\.?\\b") to MedicineForm.INJECTION,
        Regex("(?i)\\b(drops?|eye drops|ear drops)\\b") to MedicineForm.DROPS,
        Regex("(?i)\\b(cream|gel|lotion|ointment|oint)\\.?\\b") to MedicineForm.CREAM,
        Regex("(?i)\\b(inhaler|rotacap|respule|nebuli[sz]er)\\b") to MedicineForm.INHALER,
        Regex("(?i)\\b(powder|sachet|granules)\\b") to MedicineForm.POWDER,
    )

    private val prefixForm = Regex("(?i)^\\s*(?:\\d{1,3}[.)\\-]?\\s*)?((?:tab|cap|syr|syp|inj|susp|oint|cream|drops?|inhaler)\\.)\\s*(.+)$")
    private val strengthPattern = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(mg|mcg|gm|g|ml|iu|units?|%)\\b")
    private val schedulePattern = Regex("\\b(\\d)\\s*-\\s*(\\d)\\s*-\\s*(\\d)(?:\\s*-\\s*(\\d))?\\b")
    private val codePattern = Regex("(?i)\\b(od|bd|tds|tid|qid|qds|hs|sos|prn|once daily|twice daily|thrice daily|once a day|twice a day|alt day|weekly|every \\d+ (?:hours|hrs)|1-0-1)\\b")
    private val durationPattern = Regex("(?i)(?:(?:x|for|\\*)\\s*)?(\\d{1,3})\\s*(days?|d|weeks?|wks?|wks|months?|months|mth|m)\\b")
    private val quantityPattern = Regex("(?i)\\b(?:qty|quantity|total)\\s*[:=]?\\s*(\\d{1,3})\\b")

    private val noise = listOf(
        "mrp", "rate", "amount", "disc", "discount", "tax", "gst", "hsn", "batch", "b.no", "bno",
        "exp", "expiry", "expires", "mfg", "manufactur", "invoice", "bill no", "net payable", "total",
        "subtotal", "cgst", "sgst", "igst", "round off", "payment", "cash", "card", "upi", "balance",
        "pharmacist", "chemist", "drug licence", "licence no", "lic no", "store", "address", "phone",
        "thank you", "visit again", "prescribed by", "patient name", "date", "time", "signature",
    )

    fun parse(text: String): List<ParsedMedicineRow> {
        if (text.isBlank()) return emptyList()
        val rows = mutableListOf<ParsedMedicineRow>()
        for (raw in text.lines()) {
            val line = raw.replace('\t', ' ').trim()
            if (line.length < 4) continue
            val l = line.lowercase(Locale.ROOT)
            if (noise.any { it in l && !hasFormKeyword(l) }) continue
            val prefix = prefixForm.find(line)
            val hasForm = prefix != null || formKeywords.any { it.first.containsMatchIn(line) }
            val brand = brandLookup(line)
            if (!hasForm && brand == null) continue
            if (listOf("total", "net payable", "grand total").any { it in l }) continue
            rows += buildRow(line, prefix, brand)
        }
        return rows.distinctBy { it.name.lowercase(Locale.ROOT) + "|" + it.strength.orEmpty() }
    }

    private fun hasFormKeyword(lowerLine: String): Boolean =
        formKeywords.any { it.first.containsMatchIn(lowerLine) }

    private fun buildRow(line: String, prefix: MatchResult?, brand: BrandMatch?): ParsedMedicineRow {
        var form = brand?.form ?: MedicineForm.TABLET
        formKeywords.firstOrNull { it.first.containsMatchIn(line) }?.let { form = it.second }

        val name = when {
            prefix != null -> cleanName(prefix.groupValues[2])
            brand != null -> cleanName(line)
            else -> cleanName(line)
        }

        val strengthMatch = strengthPattern.find(line)
        val strength = strengthMatch?.let { m ->
            (Numbers.parse(m.groupValues[1])?.value?.let { trimNumber(it) } ?: m.groupValues[1]) + m.groupValues[2].lowercase(Locale.ROOT)
        }

        val schedule = schedulePattern.find(line)
        val codeMatch = codePattern.find(line)
        val frequencyText = schedule?.value?.trim() ?: codeMatch?.value?.uppercase(Locale.ROOT)
        val timesPerDay = when {
            schedule != null -> {
                val parts = listOf(schedule.groupValues[1], schedule.groupValues[2], schedule.groupValues[3])
                var count = parts.count { (it.toIntOrNull() ?: 0) > 0 }
                if ((schedule.groupValues[4].toIntOrNull() ?: 0) > 0) count += 1
                count
            }
            codeMatch != null -> timesFromCode(codeMatch.value)
            else -> null
        }

        val durationMatch = durationPattern.find(line)
        val durationDays = durationMatch?.let { m ->
            val value = Numbers.parse(m.groupValues[1])?.value?.toInt()
            val unit = m.groupValues[2].lowercase(Locale.ROOT)
            when {
                value == null -> null
                unit.startsWith("w") -> value * 7
                unit.startsWith("m") && (unit.startsWith("mo") || unit.length > 2) -> value * 30
                unit == "m" -> value * 30
                else -> value
            }
        } ?: quantityPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()

        val molecule = brand?.molecule ?: moleculeInParentheses(line)

        val confidence = when {
            brand != null && strength != null && frequencyText != null -> 0.95
            strength != null && frequencyText != null -> 0.85
            strength != null || frequencyText != null -> 0.7
            else -> 0.5
        }

        return ParsedMedicineRow(
            name = name,
            molecule = molecule,
            form = form,
            strength = strength,
            frequencyText = frequencyText,
            timesPerDay = timesPerDay,
            durationDays = durationDays,
            printedLine = line,
            confidence = confidence,
        )
    }

    private fun cleanName(raw: String): String {
        var name = raw.replace(Regex("(?i)\\b(tab|cap|syr|syp|inj|susp|oint|cream|drops?|inhaler)\\.?\\b"), " ")
        name = schedulePattern.replace(name, " ")
        name = codePattern.replace(name, " ")
        name = strengthPattern.replace(name, " ")
        name = durationPattern.replace(name, " ")
        name = name.replace(Regex("(?i)\\b(mrp|rate|amount|batch|exp|mfg|qty|hsn|gst)\\b.*$"), " ")
        name = name.replace(Regex("[^A-Za-z0-9 ().+/%-]"), " ")
        name = name.replace(Regex("\\s+"), " ").trim(' ', '-', '.', ':', ',')
        name = name.removePrefix("( ").trim()
        return name.take(60).replaceFirstChar { it.uppercaseChar() }
    }

    private fun timesFromCode(code: String): Int = when (code.lowercase(Locale.ROOT).trim()) {
        "od", "hs", "once daily", "once a day" -> 1
        "bd", "bid", "twice daily", "twice a day" -> 2
        "tds", "tid", "thrice daily" -> 3
        "qid", "qds" -> 4
        "1-0-1" -> 2
        "sos", "prn", "weekly", "alt day" -> 0
        else -> 1
    }

    private fun moleculeInParentheses(line: String): String? =
        Regex("\\(([A-Za-z][A-Za-z &+-]{3,40})\\)").find(line)?.groupValues?.get(1)?.trim()

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    internal data class BrandMatch(val brand: String, val molecule: String, val form: MedicineForm, val matchedText: String)

    private fun brandLookup(line: String): BrandMatch? {
        val l = line.lowercase(Locale.ROOT)
        for ((brand, info) in Brands.table) {
            if (Regex("\\b" + Regex.escape(brand) + "\\b").containsMatchIn(l)) {
                return BrandMatch(brand, info.second, info.first, brand)
            }
        }
        return null
    }
}
