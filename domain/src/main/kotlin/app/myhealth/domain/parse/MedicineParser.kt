package app.myhealth.domain.parse

import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.MedicineForm

/** Reads prescription lines like "1. Tab Metformin 500 mg 1-0-1 x 30 days" or "Cap. Omez 20mg OD before food 2 weeks". */
object MedicineParser {
    private val o = RegexOption.IGNORE_CASE
    private val head = Regex("""^\s*(?:\d{1,2}\s*[.)]\s*)?(?:rx\s*)?(tab(?:let)?s?|cap(?:sule)?s?|syp|syrup|susp(?:ension)?|inj(?:ection)?|drops?|oint(?:ment)?|cream|gel|inhaler|puff|sachet)\b\.?\s*(.+)$""", o)
    private val strength = Regex("""(\d+(?:\.\d+)?\s*(?:mg|mcg|µg|ug|g|ml|iu|units?|%)(?:\s*/\s*\d+(?:\.\d+)?\s*(?:mg|mcg|ml|g))?)(?![a-z])""", o)
    private val pattern = Regex("""(?<!\d)([01½]|0\.5)\s*-\s*([01½]|0\.5)\s*-\s*([01½]|0\.5)(?:\s*-\s*([01½]|0\.5))?(?!\d)""")
    private val duration = Regex("""(?:x|×|for)?\s*(\d{1,3})\s*(days?|d|weeks?|wks?|w|months?|mon|mo)(?![a-z])""", o)

    fun parse(lines: List<String>): List<ParsedMedicine> = lines.mapNotNull(::parseLine).distinctBy { it.name.lowercase() + it.strength.lowercase() }

    fun parseLine(line: String): ParsedMedicine? {
        val m = head.find(line) ?: return null
        val form = when (m.groupValues[1].lowercase().take(3)) {
            "tab" -> MedicineForm.Tablet; "cap" -> MedicineForm.Capsule; "syp", "syr", "sus" -> MedicineForm.Syrup
            "inj" -> MedicineForm.Injection; "dro" -> MedicineForm.Drops; "oin", "cre", "gel" -> MedicineForm.Ointment
            "inh", "puf" -> MedicineForm.Inhaler; else -> MedicineForm.Other
        }
        val rest = m.groupValues[2]
        val str = strength.find(rest)
        val freqMatch = pattern.find(rest)
        val nameEnd = listOfNotNull(str?.range?.first, freqMatch?.range?.first, firstKeyword(rest)).minOrNull() ?: rest.length
        val name = rest.substring(0, nameEnd).trim().trim('-', ',', '.').replace(Regex("\\s+"), " ")
        if (name.length < 2 || !name.first().isLetter()) return null
        val freq = frequency(rest, freqMatch)
        val days = duration.findAll(rest).lastOrNull()?.let { d ->
            val n = d.groupValues[1].toInt()
            when (d.groupValues[2].lowercase().first()) { 'w' -> n * 7; 'm' -> n * 30; else -> n }
        }?.takeIf { it in 1..730 }
        // A sentence that merely starts with "Tablets ..." is not a prescription line: require some dosing detail.
        if (str == null && freq == null && days == null) return null
        return ParsedMedicine(
            name = name.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } },
            strength = str?.value?.replace(Regex("\\s+"), "") ?: "",
            form = form,
            frequency = freq,
            durationDays = days,
            rawLine = line,
        )
    }

    private val keywords = Regex("""(?<![a-z])(od|bd|bid|tds|tid|qid|hs|sos|prn|once|twice|thrice|daily|before|after|morning|night|x\s*\d|for\s*\d|\d+\s*days?)(?![a-z])""", o)
    private fun firstKeyword(s: String) = keywords.find(s)?.range?.first

    fun frequency(text: String, patternMatch: MatchResult? = pattern.find(text)): Frequency? {
        if (patternMatch != null) {
            val n = patternMatch.groupValues.drop(1).filter { it.isNotEmpty() }.count { it != "0" }
            val nightOnly = patternMatch.groupValues.drop(1).filter { it.isNotEmpty() }.let { it.size == 3 && it[0] == "0" && it[1] == "0" && it[2] != "0" }
            return when {
                nightOnly -> Frequency.AtBedtime
                n == 1 -> Frequency.OnceDaily; n == 2 -> Frequency.TwiceDaily; n == 3 -> Frequency.ThriceDaily; n >= 4 -> Frequency.FourTimesDaily
                else -> null
            }
        }
        val t = " " + text.lowercase() + " "
        fun has(vararg w: String) = w.any { Regex("(?<![a-z])" + Regex.escape(it) + "(?![a-z])").containsMatchIn(t) }
        return when {
            has("qid", "four times") -> Frequency.FourTimesDaily
            has("tds", "tid", "thrice", "three times") -> Frequency.ThriceDaily
            has("bd", "bid", "twice", "two times") -> Frequency.TwiceDaily
            has("hs", "at bedtime", "at night") -> Frequency.AtBedtime
            has("alternate day", "alternate days", "every other day") -> Frequency.EveryOtherDay
            has("weekly", "once a week", "once weekly") -> Frequency.Weekly
            has("sos", "prn", "as needed", "when required") -> Frequency.AsNeeded
            has("od", "qd", "once daily", "once a day", "daily") -> Frequency.OnceDaily
            else -> null
        }
    }
}
