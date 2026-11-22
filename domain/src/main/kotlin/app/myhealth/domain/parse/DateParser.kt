package app.myhealth.domain.parse

import java.time.LocalDate
import java.time.Month

/**
 * Deterministic report-date detection. Numeric dates are read day-first (Indian convention) unless the first part
 * cannot be a day. Labelled dates are ranked: collection > report > generic; unlabelled dates are a fallback.
 */
object DateParser {
    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private const val MON = "(jan|feb|mar|apr|may|jun|jul|aug|sept|sep|oct|nov|dec)[a-z]*"

    private val iso = Regex("""(?<!\d)(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val dmy = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})(?!\d)""")
    private val dMonY = Regex("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*$MON[\s\-/.,]*(\d{2,4})(?!\d)""", RegexOption.IGNORE_CASE)
    private val monDY = Regex("""(?<![a-z])$MON[\s\-/.]*(\d{1,2})(?:st|nd|rd|th)?[\s,\-/.]+(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)

    private val labels: List<Pair<Regex, Int>> = listOf(
        Regex("(sample )?(collected|collection|drawn|sampling)( date| on| at)?", RegexOption.IGNORE_CASE) to 3,
        Regex("(reported|report date|report on|date of report|reporting date|result date)", RegexOption.IGNORE_CASE) to 2,
        Regex("(registered|registration|received|bill date|invoice date|prescription date|visit date|date)", RegexOption.IGNORE_CASE) to 1,
    )
    private val ignoredLabels = Regex("(dob|date of birth|d\\.o\\.b|birth|expiry|exp\\.?|mfg|valid till|printed)", RegexOption.IGNORE_CASE)

    fun parseFirst(text: String, today: LocalDate? = null): LocalDate? = findAll(text, today).firstOrNull()?.second

    /** All dates in [text] with their start index, in order of appearance. */
    fun findAll(text: String, today: LocalDate? = null): List<Pair<Int, LocalDate>> {
        val out = mutableListOf<Pair<Int, LocalDate>>()
        val taken = BooleanArray(text.length)
        fun add(start: Int, end: Int, d: LocalDate?) {
            if (d == null || (start until end).any { taken[it] }) return
            if (today != null && d.isAfter(today.plusDays(1))) return
            if (d.year < 1950) return
            (start until end).forEach { taken[it] = true }
            out += start to d
        }
        iso.findAll(text).forEach { m -> add(m.range.first, m.range.last + 1, safe(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())) }
        dMonY.findAll(text).forEach { m ->
            add(m.range.first, m.range.last + 1, safe(year(m.groupValues[3]), months.getValue(m.groupValues[2].lowercase()), m.groupValues[1].toInt()))
        }
        monDY.findAll(text).forEach { m ->
            add(m.range.first, m.range.last + 1, safe(m.groupValues[3].toInt(), months.getValue(m.groupValues[1].lowercase()), m.groupValues[2].toInt()))
        }
        dmy.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = year(m.groupValues[3])
            val d = if (a > 12 || b <= 12) safe(y, b, a) else safe(y, a, b) // day-first unless impossible
            add(m.range.first, m.range.last + 1, d)
        }
        return out.sortedBy { it.first }
    }

    /** Pick the report date from document lines using label priority. */
    fun reportDate(lines: List<String>, today: LocalDate? = null): LocalDate? {
        var best: Pair<Int, LocalDate>? = null
        var fallback: LocalDate? = null
        for (line in lines) {
            val dates = findAll(line, today)
            if (dates.isEmpty()) continue
            for ((idx, d) in dates) {
                val prefix = line.substring(0, idx)
                // label must be the nearest text before the date, and not a birth/expiry label
                val lastLabel = labels.mapNotNull { (re, score) -> re.findAll(prefix).lastOrNull()?.let { Triple(it.range.first, score, it) } }
                    .maxByOrNull { it.first }
                val ignored = ignoredLabels.findAll(prefix).lastOrNull()
                if (ignored != null && (lastLabel == null || ignored.range.first > lastLabel.first)) continue
                if (lastLabel != null) {
                    if (best == null || lastLabel.second > best.first) best = lastLabel.second to d
                } else if (fallback == null) fallback = d
            }
        }
        return best?.second ?: fallback
    }

    private fun year(s: String): Int { val y = s.toInt(); return if (s.length == 2) (if (y > 50) 1900 + y else 2000 + y) else y }

    private fun safe(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, Month.of(m), d) }.getOrNull()
}
