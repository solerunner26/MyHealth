package app.myhealth.domain.family

import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Sex
import app.myhealth.domain.model.Vaccination
import app.myhealth.domain.util.IdGenerator
import java.time.LocalDate
import java.time.Period

/**
 * A SUGGESTED schedule (India's Universal Immunization Programme for children, common adult boosters). It only
 * pre-fills due dates the user reviews; it is not medical advice and the app says to confirm with a doctor.
 */
object VaccineSchedule {
    data class Entry(val name: String, val dose: String, val at: Period, val sex: Sex? = null)

    val child: List<Entry> = listOf(
        Entry("BCG", "Birth", Period.ZERO), Entry("OPV", "0 (birth)", Period.ZERO), Entry("Hepatitis B", "Birth", Period.ZERO),
        Entry("Pentavalent", "1", Period.ofWeeks(6)), Entry("OPV", "1", Period.ofWeeks(6)), Entry("Rotavirus", "1", Period.ofWeeks(6)),
        Entry("fIPV", "1", Period.ofWeeks(6)), Entry("PCV", "1", Period.ofWeeks(6)),
        Entry("Pentavalent", "2", Period.ofWeeks(10)), Entry("OPV", "2", Period.ofWeeks(10)), Entry("Rotavirus", "2", Period.ofWeeks(10)),
        Entry("Pentavalent", "3", Period.ofWeeks(14)), Entry("OPV", "3", Period.ofWeeks(14)), Entry("Rotavirus", "3", Period.ofWeeks(14)),
        Entry("fIPV", "2", Period.ofWeeks(14)), Entry("PCV", "2", Period.ofWeeks(14)),
        Entry("MR", "1", Period.ofMonths(9)), Entry("PCV", "Booster", Period.ofMonths(9)), Entry("Vitamin A", "1", Period.ofMonths(9)),
        Entry("MR", "2", Period.ofMonths(16)), Entry("DPT", "Booster 1", Period.ofMonths(16)), Entry("OPV", "Booster", Period.ofMonths(16)),
        Entry("DPT", "Booster 2", Period.ofYears(5)), Entry("Td", "10 years", Period.ofYears(10)), Entry("Td", "16 years", Period.ofYears(16)),
        Entry("HPV", "9–14 years", Period.ofYears(9), Sex.Female),
    )

    /** Entries for this member that are not already recorded (same name and dose), with due dates from the date of birth. */
    fun suggest(member: FamilyMember, existing: List<Vaccination>, ids: IdGenerator, today: LocalDate, nowMs: Long): List<Vaccination> {
        val dob = member.dateOfBirth ?: return emptyList()
        val have = existing.map { it.name.lowercase() to it.dose.lowercase() }.toSet()
        val childList = child.filter { it.sex == null || it.sex == member.sex }.map { e -> e to dob.plus(e.at) }
        val adult = buildList {
            val age = Period.between(dob, today).years
            if (age >= 18) {
                // Td booster every 10 years, yearly influenza from 50, pneumococcal at 65
                add(Entry("Td", "Booster", Period.ZERO) to today.plusMonths(1))
                if (age >= 50) add(Entry("Influenza", today.year.toString(), Period.ZERO) to today.plusMonths(1))
                if (age >= 65) add(Entry("Pneumococcal", "1", Period.ZERO) to today.plusMonths(1))
            }
        }
        // children: the whole programme (past doses still appear so they can be ticked off); adults: boosters only
        val picked = if (Period.between(dob, today).years < 18) childList else adult
        return picked.filter { (e, _) -> (e.name.lowercase() to e.dose.lowercase()) !in have }
            .map { (e, due) -> Vaccination(ids.next(), member.id, e.name, e.dose, givenDate = null, dueDate = due, createdAtEpochMs = nowMs) }
    }
}

/** Family history map: which conditions appear in whom, and which "run in the family" (2+ relatives). */
object FamilyHistory {
    data class Carrier(val label: String, val relation: String, val isMember: Boolean)
    data class Row(val condition: String, val carriers: List<Carrier>) { val runsInFamily get() = carriers.size >= 2 }

    fun map(members: List<FamilyMember>, relatives: List<app.myhealth.domain.model.RelativeCondition>, relationLabel: (FamilyMember) -> String): List<Row> {
        val byKey = linkedMapOf<String, Pair<String, MutableList<Carrier>>>()
        fun add(condition: String, c: Carrier) {
            val clean = condition.trim(); if (clean.isEmpty()) return
            val key = normalise(clean)
            byKey.getOrPut(key) { clean to mutableListOf() }.second += c
        }
        members.forEach { m -> m.conditions.forEach { add(it, Carrier(m.name, relationLabel(m), true)) } }
        relatives.forEach { r -> add(r.condition, Carrier(r.relation, r.relation, false)) }
        return byKey.values.map { (name, cs) -> Row(name, cs.distinctBy { it.label to it.relation }) }
            .sortedWith(compareByDescending<Row> { it.carriers.size }.thenBy { it.condition.lowercase() })
    }

    /** "Type 2 diabetes", "diabetes type-2" and "DIABETES" group together; spelling variants of common terms too. */
    fun normalise(s: String): String {
        val t = s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u0aff ]"), " ").replace(Regex("\\s+"), " ").trim()
        return when {
            "diabet" in t || t == "dm" || "sugar" in t -> "diabetes"
            "hypertension" in t || t == "bp" || "high blood pressure" in t -> "hypertension"
            "thyroid" in t -> "thyroid"
            "cholesterol" in t || "lipid" in t -> "cholesterol"
            "asthma" in t -> "asthma"
            "heart" in t || "cardiac" in t || "coronary" in t -> "heart disease"
            else -> t
        }
    }
}
