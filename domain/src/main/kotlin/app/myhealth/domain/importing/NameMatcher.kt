package app.myhealth.domain.importing

import app.myhealth.domain.model.FamilyMember

enum class NameMatch { Exact, Likely, None }

data class MemberMatch(val member: FamilyMember, val match: NameMatch, val score: Double)

/**
 * Matches the patient name printed on a report to family members. Deterministic and conservative: titles and
 * punctuation are ignored, initials match the same letter, and a "likely" match needs the first name plus at least
 * one more agreeing token or a unique first name. Never auto-assigns on a weak match.
 */
object NameMatcher {
    private val titles = setOf("mr", "mrs", "ms", "miss", "master", "baby", "dr", "smt", "shri", "shree", "sri", "kumari", "km", "kum", "md", "mx", "prof", "late", "ku", "bhai", "ben", "behen")

    fun tokens(name: String): List<String> = name.lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .split(Regex("\\s+")).filter { it.isNotBlank() && it !in titles }

    private fun tokenEq(a: String, b: String) = a == b || (a.length == 1 && b.startsWith(a)) || (b.length == 1 && a.startsWith(b))

    fun score(printed: String, member: FamilyMember): Double {
        val p = tokens(printed); val m = tokens(member.name)
        if (p.isEmpty() || m.isEmpty()) return 0.0
        val matched = p.count { pt -> m.any { tokenEq(pt, it) } }
        val firstMatches = tokenEq(p.first(), m.first()) || m.any { it == p.first() && it.length > 1 }
        return (matched.toDouble() / maxOf(p.size, m.size)) * 0.7 + (if (firstMatches) 0.3 else 0.0)
    }

    /** Members ranked by similarity, best first. */
    fun rank(printed: String, members: List<FamilyMember>): List<MemberMatch> {
        val scored = members.map { it to score(printed, it) }.sortedByDescending { it.second }
        val firstToken = tokens(printed).firstOrNull()
        val sameFirst = members.count { m -> firstToken != null && tokens(m.name).firstOrNull()?.let { tokenEq(it, firstToken) } == true }
        return scored.map { (m, s) ->
            val kind = when {
                s >= 0.99 -> NameMatch.Exact
                s >= 0.65 -> NameMatch.Likely
                s >= 0.3 && sameFirst == 1 && tokens(m.name).firstOrNull()?.let { tokenEq(it, firstToken!!) } == true -> NameMatch.Likely
                else -> NameMatch.None
            }
            MemberMatch(m, kind, s)
        }
    }

    /** The member to pre-select, only if exactly one strong candidate exists. */
    fun best(printed: String?, members: List<FamilyMember>): MemberMatch? {
        if (printed.isNullOrBlank()) return null
        val r = rank(printed, members).filter { it.match != NameMatch.None }
        if (r.isEmpty()) return null
        if (r.size > 1 && r[0].match == r[1].match && r[0].score - r[1].score < 0.15) return null // ambiguous: ask
        return r[0]
    }

    /** Printed name tidied for a new profile: titles removed, words capitalised. */
    fun cleanName(printed: String): String = tokens(printed).joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
}
