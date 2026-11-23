package app.myhealth.domain.insurance

import app.myhealth.domain.model.MediclaimClaim
import app.myhealth.domain.model.MediclaimPolicy
import java.time.LocalDate

/** Balance and renewal arithmetic for a policy. */
object Mediclaims {
    /** Claims that fall in the current policy period (cover resets on renewal). */
    fun periodClaims(p: MediclaimPolicy, claims: List<MediclaimClaim>): List<MediclaimClaim> = claims.filter { c ->
        c.policyId == p.id && (p.startDate == null || c.admittedOn == null || !c.admittedOn.isBefore(p.startDate))
    }

    fun used(p: MediclaimPolicy, claims: List<MediclaimClaim>): Double = periodClaims(p, claims).sumOf { it.used }

    fun available(p: MediclaimPolicy, claims: List<MediclaimClaim>): Double = (p.totalCover - used(p, claims)).coerceAtLeast(0.0)

    /** Days before the end date on which a renewal reminder fires. */
    val REMINDER_DAYS = listOf(30, 7, 1, 0)

    fun reminderDates(p: MediclaimPolicy, today: LocalDate): List<Pair<Int, LocalDate>> {
        val end = p.endDate ?: return emptyList()
        return REMINDER_DAYS.map { it to end.minusDays(it.toLong()) }.filter { !it.second.isBefore(today) }
    }

    enum class Expiry { Ok, Soon, Expired, Unknown }
    fun expiry(p: MediclaimPolicy, today: LocalDate): Expiry {
        val d = p.daysToExpiry(today) ?: return Expiry.Unknown
        return when { d < 0 -> Expiry.Expired; d <= 30 -> Expiry.Soon; else -> Expiry.Ok }
    }
}

/**
 * Reads a network-hospital list from the text of a policy PDF / photo (tables like "Sr | Hospital | Address | City |
 * Pincode | Phone"). Heuristic and offline; every row is shown to the user, who can delete or edit it.
 */
object HospitalListParser {
    data class Parsed(val name: String, val address: String, val city: String, val pincode: String, val phone: String)

    private val keyword = Regex("""\b(hospital|hospitals|nursing\s*home|clinic|medical\s*(centre|center|college)|health\s*care|healthcare|institute|multi\s*-?speciality|super\s*-?speciality|maternity|eye\s*care|heart|cancer\s*centre|surgical|polyclinic|sanatorium|medicare|life\s*care)\b""", RegexOption.IGNORE_CASE)
    private val phoneRe = Regex("""(?:\+91[\s-]?)?(?:[6-9]\d{4}[\s-]?\d{5}|0\d{2,4}[\s-]?\d{6,8})""")
    private val pinRe = Regex("""\b[1-9]\d{5}\b""")
    private val serial = Regex("""^\s*(?:\d{1,5}[.)\s]+)""")
    private val header = Regex("""\b(sr\.?\s*no|s\.?\s*no|hospital\s*name|name\s*of\s*(the\s*)?hospital|hospital\s*list|list\s*of|network\s*hospitals?\s*(list|directory))\b""", RegexOption.IGNORE_CASE)

    fun parse(lines: List<String>, max: Int = 3000): List<Parsed> {
        val out = linkedMapOf<String, Parsed>()
        var i = 0
        while (i < lines.size && out.size < max) {
            val line = lines[i].trim()
            i++
            if (line.isBlank() || header.containsMatchIn(line) || !keyword.containsMatchIn(line)) continue
            val cells = line.split(Regex("""\s{2,}|\t|\|""")).map { it.trim() }.filter { it.isNotEmpty() }
            val nameIdx = cells.indexOfFirst { keyword.containsMatchIn(it) }.coerceAtLeast(0)
            val name = cells.getOrElse(nameIdx) { line }.replace(serial, "").trim().trimEnd(',', '-')
            if (name.length < 4 || name.length > 120) continue
            var rest = cells.drop(nameIdx + 1).joinToString(", ")
            // the address/phone often continues on the next line (a line without a hospital keyword)
            if (i < lines.size) {
                val next = lines[i].trim()
                if (next.isNotBlank() && !keyword.containsMatchIn(next) && (pinRe.containsMatchIn(next) || phoneRe.containsMatchIn(next) || next.contains(','))) { rest = listOf(rest, next).filter { it.isNotBlank() }.joinToString(", "); i++ }
            }
            val phone = phoneRe.findAll(rest).map { it.value.replace(Regex("""[\s-]"""), "") }.distinct().joinToString(", ")
            val pin = pinRe.findAll(rest).map { it.value }.firstOrNull { p -> phone.isEmpty() || !phone.contains(p) }.orEmpty()
            val address = rest.replace(phoneRe, "").replace(Regex("""\s*,\s*(,\s*)+"""), ", ").trim(' ', ',', '-')
            val city = address.split(',').map { it.trim() }.lastOrNull { it.isNotEmpty() && it.none(Char::isDigit) && it.length in 3..30 }.orEmpty()
            val key = name.lowercase().replace(Regex("""\W+"""), "") + pin
            out.putIfAbsent(key, Parsed(name, address.take(300), city, pin, phone.take(60)))
        }
        return out.values.toList()
    }
}
