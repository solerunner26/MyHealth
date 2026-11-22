package app.myhealth.domain.model

import java.time.LocalDate
import java.time.Period

enum class Sex { Female, Male, Other, Unspecified }

enum class Relationship { Self, Spouse, Child, Parent, Sibling, Grandparent, Other }

enum class BloodGroup(val label: String) {
    APos("A+"), ANeg("A−"), BPos("B+"), BNeg("B−"), ABPos("AB+"), ABNeg("AB−"), OPos("O+"), ONeg("O−"), Unknown("?");
}

data class EmergencyContact(val name: String = "", val phone: String = "", val relation: String = "") {
    val isEmpty get() = name.isBlank() && phone.isBlank()
}

/** A person whose records are kept. Exactly one member is [Relationship.Self] (the device owner). */
data class FamilyMember(
    val id: String,
    val name: String,
    val relationship: Relationship,
    val dateOfBirth: LocalDate? = null,
    val sex: Sex = Sex.Unspecified,
    val heightCm: Double? = null,
    val bloodGroup: BloodGroup = BloodGroup.Unknown,
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val emergencyContact: EmergencyContact = EmergencyContact(),
    val createdAtEpochMs: Long = 0,
) {
    fun ageYears(on: LocalDate): Int? = dateOfBirth?.let { if (it.isAfter(on)) null else Period.between(it, on).years }

    val initials: String get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
