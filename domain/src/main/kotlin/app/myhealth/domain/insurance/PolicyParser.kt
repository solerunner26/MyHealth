package app.myhealth.domain.insurance

import app.myhealth.domain.importing.NameMatcher
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.PolicyFeatures
import app.myhealth.domain.model.PolicyType
import app.myhealth.domain.parse.DateParser
import app.myhealth.domain.parse.Numbers
import java.time.LocalDate

/**
 * Reads a health-insurance policy schedule (PDF text layer or OCR of photos) into editable fields. Offline, rule based,
 * tuned to Indian policy schedules (labels such as "Policy No.", "Sum Insured", "Period of Insurance", "TPA",
 * "Co-payment", "Pre-existing disease waiting period"). Everything it returns is a SUGGESTION: the editor shows it
 * pre-filled and the user corrects, adds or deletes any field before saving. Fields it cannot find stay empty.
 */
object PolicyParser {

    data class Result(
        val insurer: String = "",
        val planName: String = "",
        val policyNumber: String = "",
        val type: PolicyType? = null,
        val sumInsured: Double? = null,
        val bonus: Double? = null,
        val premium: Double? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val tpaName: String = "",
        val tpaPhone: String = "",
        val helpline: String = "",
        val agentName: String = "",
        val agentPhone: String = "",
        val features: PolicyFeatures = PolicyFeatures(),
        /** Family members whose names are printed in the schedule (insured persons). */
        val memberIds: List<String> = emptyList(),
        /** How many fields were found (0 = nothing recognised: tell the user to type it in). */
        val found: Int = 0,
    )

    private val ic = RegexOption.IGNORE_CASE
    private val insurers = listOf(
        "Star Health" to "Star Health and Allied Insurance", "HDFC ERGO" to "HDFC ERGO General Insurance", "ICICI Lombard" to "ICICI Lombard General Insurance",
        "Niva Bupa" to "Niva Bupa Health Insurance", "Max Bupa" to "Niva Bupa Health Insurance", "Care Health" to "Care Health Insurance",
        "Religare" to "Care Health Insurance", "Aditya Birla" to "Aditya Birla Health Insurance", "Bajaj Allianz" to "Bajaj Allianz General Insurance",
        "Tata AIG" to "Tata AIG General Insurance", "New India Assurance" to "The New India Assurance", "United India" to "United India Insurance",
        "National Insurance" to "National Insurance Company", "Oriental Insurance" to "The Oriental Insurance Company", "ManipalCigna" to "ManipalCigna Health Insurance",
        "Manipal Cigna" to "ManipalCigna Health Insurance", "Reliance General" to "Reliance General Insurance", "SBI General" to "SBI General Insurance",
        "Future Generali" to "Future Generali India Insurance", "IFFCO Tokio" to "IFFCO Tokio General Insurance", "IFFCO-Tokio" to "IFFCO Tokio General Insurance",
        "Cholamandalam" to "Cholamandalam MS General Insurance", "Kotak" to "Kotak Mahindra General Insurance", "Go Digit" to "Go Digit General Insurance",
        "Digit Insurance" to "Go Digit General Insurance", "Acko" to "Acko General Insurance", "Navi General" to "Navi General Insurance",
        "Royal Sundaram" to "Royal Sundaram General Insurance", "Universal Sompo" to "Universal Sompo General Insurance", "Liberty General" to "Liberty General Insurance",
        "Magma" to "Magma HDI General Insurance", "Zuno" to "Zuno General Insurance", "Galaxy Health" to "Galaxy Health Insurance", "Narayana Health" to "Narayana Health Insurance",
    )
    private val tpas = listOf("Medi Assist", "Paramount", "MD India", "Health India", "Vidal", "Family Health Plan", "FHPL", "Raksha", "Heritage Health", "Ericson",
        "Good Health", "Safeway", "Park Mediclaim", "Genins", "Vipul Medcorp", "East West Assist", "Anmol Medicare", "Health Insurance TPA of India", "Rothshield", "Volo Health")

    private val phone = Regex("""(?:\+91[\s-]?)?(?:1800[\s-]?\d{2,4}[\s-]?\d{3,4}|1860[\s-]?\d{3}[\s-]?\d{4}|[6-9]\d{4}[\s-]?\d{5}|0\d{2,4}[\s-]?\d{6,8})""")
    private val amountRe = Regex("""(?:rs\.?|inr|₹)?\s*(\d{1,3}(?:,\d{2,3})+(?:\.\d+)?|\d+(?:\.\d+)?)\s*(lakhs?|lacs?|lac|crores?|cr\.?|l\b)?(?:\s*/-)?""", ic)
    private val policyNoLabel = Regex("""\b(policy\s*(no|number|#)\.?|certificate\s*(no|number)\.?)\s*[:\-.]?\s*""", ic)
    private val policyNoValue = Regex("""[A-Z0-9][A-Z0-9/\-]{5,40}""", ic)
    private val planLabel = Regex("""\b(product\s*name|plan\s*name|name\s*of\s*(the\s*)?(product|plan|policy)|product|plan|policy\s*name)\s*[:\-]\s*""", ic)
    private val sumLabel = Regex("""\b(basic\s*)?sum\s*(insured|assured)\b|\bcover(age)?\s*amount\b""", ic)
    private val bonusLabel = Regex("""\b(cumulative|no[\s-]*claim|loyalty)\s*bonus\b""", ic)
    private val premiumLabel = Regex("""\b(total|gross|net|final)?\s*premium\b""", ic)
    private val periodLabel = Regex("""\b(policy\s*period|period\s*of\s*(insurance|cover|policy)|policy\s*duration|cover\s*period|valid(ity)?)\b""", ic)
    private val startLabel = Regex("""\b(start\s*date|commencement|inception|effective\s*(from|date)|risk\s*start|period\s*from|from)\b""", ic)
    private val endLabel = Regex("""\b(end\s*date|expiry(\s*date)?|valid\s*(till|up\s*to|upto)|period\s*to|to|till|until)\b""", ic)

    fun parse(pages: List<String>, members: List<FamilyMember> = emptyList()): Result {
        val lines = pages.flatMap { it.split('\n', '\r') }.map { it.replace('\t', ' ').trim() }.filter { it.isNotEmpty() }
        val all = lines.joinToString("\n")
        var found = 0
        fun <T> count(v: T): T { if (v != null && v != "" && v != false) found++; return v }

        val insurer = count(insurerOf(lines, all))
        val policyNumber = count(policyNumberOf(lines))
        val plan = count(valueAfter(lines, planLabel)?.let(::tidy)?.take(80).orEmpty())
        val sum = count(amountAfter(lines, sumLabel, min = 10_000.0))
        val bonus = count(amountAfter(lines, bonusLabel, min = 1_000.0, notPercent = true))
        val premium = count(amountAfter(lines, Regex("""\b(total|gross|final)\s*premium\b|\bpremium\s*(amount|paid|payable)\b|\bpremium\b""", ic), min = 100.0, exclude = Regex("""waiver|basis|frequency|mode""", ic)))
        val (start, end) = periodOf(lines)
        count(start); count(end)
        val type = count(typeOf(all))
        val tpaLine = lines.firstOrNull { Regex("""\bTPA\b|third\s*party\s*admin""", ic).containsMatchIn(it) }
        val tpaName = count(tpas.firstOrNull { all.contains(it, ignoreCase = true) } ?: tpaLine?.let { valueAfter(listOf(it), Regex("""(TPA\s*(name)?|third\s*party\s*administrator)\s*[:\-]?\s*""", ic)) }?.let { tidy(it.replace(phone, "")) }?.take(80).orEmpty())
        val tpaPhone = count(tpaLine?.let { l -> phone.find(l)?.value } ?: lines.firstOrNull { Regex("""\bTPA\b.*(phone|contact|toll|helpline)|(phone|contact|toll|helpline).*\bTPA\b""", ic).containsMatchIn(it) }?.let { phone.find(it)?.value }.orEmpty())
        val helpline = count(lines.firstOrNull { Regex("""toll[\s-]*free|helpline|customer\s*(care|service)|call\s*us|claims?\s*intimation""", ic).containsMatchIn(it) && phone.containsMatchIn(it) && !Regex("""\bTPA\b""", ic).containsMatchIn(it) }
            ?.let { phone.find(it)?.value }.orEmpty())
        val agentLabel = Regex("""\b(agent|intermediary|broker|advisor|POSP)\s*(name)?\s*[:\-]\s*""", ic)
        val agentLine = lines.firstOrNull { agentLabel.containsMatchIn(it) }
        val agentName = count(agentLine?.let { valueAfter(listOf(it), agentLabel) }?.replace(phone, "")?.replace(Regex("""\b(code|licen[cs]e|no\.?|contact|phone|mobile|tel|email)\b.*""", ic), "")?.replace(Regex("""\b(agent|intermediary|broker|advisor)\s*$""", ic), "")?.let(::tidy)?.take(80).orEmpty())
        val agentPhone = count(lines.firstOrNull { Regex("""\b(agent|intermediary|broker|advisor)\b""", ic).containsMatchIn(it) && phone.containsMatchIn(it) }?.let { phone.find(it)?.value }.orEmpty())
        val features = featuresOf(lines, all)
        found += listOf(features.roomRent.isNotBlank(), features.coPayPercent != null, features.deductible != null, features.initialWaitingDays != null,
            features.pedWaitingMonths != null, features.specificWaitingMonths != null, features.preHospitalisationDays != null, features.postHospitalisationDays != null,
            features.noClaimBonusPercent != null, features.ambulanceLimit != null).count { it }
        val memberIds = members.filter { m -> mentions(lines, m) }.map { it.id }
        return Result(insurer, plan, policyNumber, type, sum, bonus, premium, start, end, tpaName, cleanPhone(tpaPhone), cleanPhone(helpline), agentName, cleanPhone(agentPhone), features, memberIds, found)
    }

    // ---- fields ----

    private fun insurerOf(lines: List<String>, all: String): String {
        insurers.firstOrNull { all.contains(it.first, ignoreCase = true) }?.let { return it.second }
        val company = Regex("""([A-Z][A-Za-z&.\- ]{2,60}(?:General|Health|Assurance)?\s*Insurance\s*(?:Company|Co\.?)?\s*(?:Limited|Ltd\.?))""")
        return lines.firstNotNullOfOrNull { company.find(it)?.groupValues?.get(1) }?.let(::tidy)?.take(80).orEmpty()
    }

    private fun policyNumberOf(lines: List<String>): String {
        for ((i, line) in lines.withIndex()) {
            val m = policyNoLabel.find(line) ?: continue
            val rest = line.substring(m.range.last + 1)
            val v = policyNoValue.findAll(rest).map { it.value.trim('-', '/') }.firstOrNull { it.any(Char::isDigit) && it.length >= 6 }
                ?: lines.getOrNull(i + 1)?.let { n -> policyNoValue.findAll(n).map { it.value }.firstOrNull { it.any(Char::isDigit) && it.length >= 6 && n.length < 50 } }
            if (v != null && !Regex("""^\d{1,2}[-/]\d{1,2}[-/]\d{2,4}$""").matches(v)) return v.take(40)
        }
        return ""
    }

    private fun typeOf(all: String): PolicyType? = when {
        Regex("""super\s*top[\s-]*up""", ic).containsMatchIn(all) -> PolicyType.SuperTopUp
        Regex("""\btop[\s-]*up\b""", ic).containsMatchIn(all) -> PolicyType.TopUp
        Regex("""critical\s*illness""", ic).containsMatchIn(all) && !Regex("""family\s*floater|individual""", ic).containsMatchIn(all) -> PolicyType.CriticalIllness
        Regex("""ayushman|pm[\s-]*jay|cghs|government\s*health""", ic).containsMatchIn(all) -> PolicyType.Government
        Regex("""group\s*(health|mediclaim|insurance|policy)|employer|employee\s*id""", ic).containsMatchIn(all) -> PolicyType.Group
        Regex("""\bfloater\b""", ic).containsMatchIn(all) -> PolicyType.FamilyFloater
        Regex("""\bindividual\b""", ic).containsMatchIn(all) -> PolicyType.Individual
        else -> null
    }

    private fun periodOf(lines: List<String>): Pair<LocalDate?, LocalDate?> {
        // a "period" line usually carries both dates: "From 01/04/2026 To 31/03/2027"
        for ((i, line) in lines.withIndex()) {
            if (!periodLabel.containsMatchIn(line)) continue
            val window = listOfNotNull(line, lines.getOrNull(i + 1), lines.getOrNull(i + 2)).joinToString(" ")
            val ds = DateParser.findAll(window).map { it.second }
            if (ds.size >= 2 && ds[1].isAfter(ds[0])) return ds[0] to ds[1]
        }
        val start = lines.firstNotNullOfOrNull { l -> startLabel.find(l)?.let { m -> DateParser.parseFirst(l.substring(m.range.first)) } }
        val end = lines.firstNotNullOfOrNull { l -> Regex("""\b(end\s*date|expiry(\s*date)?|valid\s*(till|up\s*to|upto)|period\s*to|renewal\s*date|policy\s*end)\b""", ic).find(l)
            ?.let { m -> DateParser.parseFirst(l.substring(m.range.first)) } }
        if (start != null && end == null) return start to start.plusYears(1).minusDays(1)
        if (start == null && end != null) return end.minusYears(1).plusDays(1) to end
        return start to end?.takeIf { start == null || it.isAfter(start) }
    }

    private fun featuresOf(lines: List<String>, all: String): PolicyFeatures {
        fun line(re: Regex) = lines.firstOrNull { re.containsMatchIn(it) }
        fun percent(re: Regex): Int? = line(re)?.let { l -> Regex("""(\d{1,2})\s*%""").find(l.substring(re.find(l)!!.range.first))?.groupValues?.get(1)?.toIntOrNull() }
        fun duration(re: Regex, wantMonths: Boolean): Int? = line(re)?.let { l ->
            val tail = l.substring(re.find(l)!!.range.first)
            val m = Regex("""(\d{1,3})\s*(days?|months?|years?|yrs?)""", ic).find(tail) ?: return@let null
            val n = m.groupValues[1].toInt(); val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("y") -> if (wantMonths) n * 12 else n * 365
                unit.startsWith("m") -> if (wantMonths) n else n * 30
                else -> if (wantMonths) (n / 30).takeIf { it > 0 } else n
            }
        }
        fun covered(re: Regex): Boolean {
            val l = line(re) ?: return false
            return !Regex("""not\s*covered|excluded|not\s*available|not\s*applicable|\bn/?a\b|:\s*no\b|\bnil\b""", ic).containsMatchIn(l)
        }
        val roomRe = Regex("""room\s*rent|room\s*category|room\s*type""", ic)
        val room = line(roomRe)?.let { l -> l.substring(roomRe.find(l)!!.range.last + 1).trim(' ', ':', '-', '.') }?.let(::tidy)?.take(80).orEmpty()
        return PolicyFeatures(
            roomRent = room,
            coPayPercent = percent(Regex("""co[\s-]*pay(ment)?""", ic)),
            deductible = amountAfter(lines, Regex("""\b(aggregate\s*)?deductible\b""", ic), min = 1_000.0),
            initialWaitingDays = duration(Regex("""initial\s*waiting|first\s*30\s*days|cooling\s*period""", ic), wantMonths = false),
            pedWaitingMonths = duration(Regex("""pre[\s-]*existing|\bPED\b""", ic), wantMonths = true),
            specificWaitingMonths = duration(Regex("""specific\s*(disease|illness|ailment)s?|named\s*ailments?|listed\s*(illness|condition)""", ic), wantMonths = true),
            preHospitalisationDays = duration(Regex("""pre[\s-]*hospitali[sz]ation""", ic), wantMonths = false),
            postHospitalisationDays = duration(Regex("""post[\s-]*hospitali[sz]ation""", ic), wantMonths = false),
            noClaimBonusPercent = percent(Regex("""no[\s-]*claim\s*bonus|\bNCB\b|cumulative\s*bonus""", ic)),
            ambulanceLimit = amountAfter(lines, Regex("""(road\s*)?ambulance""", ic), min = 500.0),
            dayCare = covered(Regex("""day[\s-]*care""", ic)),
            restoration = covered(Regex("""restor(e|ation)|recharge|reinstatement|refill\s*benefit""", ic)),
            ayush = covered(Regex("""\bAYUSH\b""", ic)),
            maternity = covered(Regex("""maternity|new[\s-]*born""", ic)),
            opd = covered(Regex("""\bOPD\b|out[\s-]*patient""", ic)),
            healthCheckup = covered(Regex("""health\s*check[\s-]*ups?|preventive\s*check""", ic)),
            domiciliary = covered(Regex("""domiciliary""", ic)),
            organDonor = covered(Regex("""organ\s*donor""", ic)),
        )
    }

    /** True when a line of the schedule carries the member's first AND last name (or a single-word name exactly). */
    private fun mentions(lines: List<String>, m: FamilyMember): Boolean {
        val want = NameMatcher.tokens(m.name).filter { it.length > 1 }
        if (want.isEmpty()) return false
        return lines.any { l ->
            val have = NameMatcher.tokens(l).toSet()
            if (want.size == 1) have.contains(want[0]) && l.length < 60 else want.first() in have && want.last() in have
        }
    }

    // ---- helpers ----

    private fun valueAfter(lines: List<String>, label: Regex): String? {
        for ((i, line) in lines.withIndex()) {
            val m = label.find(line) ?: continue
            val rest = line.substring(m.range.last + 1).trim(' ', ':', '-', '.')
            if (rest.length >= 2) return rest.split(Regex("""\s{3,}""")).first()
            val next = lines.getOrNull(i + 1)?.trim()
            if (next != null && next.length in 2..80 && !next.contains(':')) return next
        }
        return null
    }

    /** First amount (≥ [min]) printed after [label] on its line, or on the next line when the value sits under it. */
    internal fun amountAfter(lines: List<String>, label: Regex, min: Double, notPercent: Boolean = true, exclude: Regex? = null): Double? {
        for ((i, line) in lines.withIndex()) {
            val m = label.find(line) ?: continue
            if (exclude != null && exclude.containsMatchIn(line)) continue
            val candidates = listOfNotNull(line.substring(m.range.last + 1), lines.getOrNull(i + 1)?.takeIf { label.find(it) == null })
            for (text in candidates) {
                val v = amounts(text, notPercent).firstOrNull { it >= min }
                if (v != null) return v
            }
        }
        return null
    }

    internal fun amounts(text: String, notPercent: Boolean = true): List<Double> = amountRe.findAll(text).mapNotNull { m ->
        val after = text.substring(m.range.last + 1).trimStart()
        if (notPercent && after.startsWith("%")) return@mapNotNull null
        // skip dates (01/04/2026) and phone-like long digit runs
        val before = text.getOrNull(m.range.first - 1)
        if (before == '/' || before == '-' || after.startsWith("/") && !after.startsWith("/-") || after.startsWith("-") && !after.startsWith("/-")) return@mapNotNull null
        val raw = m.groupValues[1]
        if (raw.length > 12) return@mapNotNull null
        val n = Numbers.parse(raw) ?: return@mapNotNull null
        val unit = m.groupValues[2].lowercase()
        when {
            unit.startsWith("cr") -> n * 1_00_00_000
            unit.startsWith("l") -> n * 1_00_000
            else -> n
        }
    }.toList()

    private fun tidy(s: String) = s.replace(Regex("""\s+"""), " ").trim(' ', ':', '-', ',', '.', '|')
    private fun cleanPhone(s: String) = s.replace(Regex("""[\s-]"""), "").take(20)
}
