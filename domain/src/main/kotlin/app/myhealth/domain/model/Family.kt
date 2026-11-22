package app.myhealth.domain.model

import java.time.LocalDate

/** A vaccine dose: scheduled ([givenDate] null, [dueDate] set) or given. */
data class Vaccination(
    val id: String,
    val memberId: String,
    val name: String,
    val dose: String = "",
    val givenDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val provider: String = "",
    val notes: String = "",
    val createdAtEpochMs: Long = 0,
) {
    val given get() = givenDate != null
    fun isDue(today: LocalDate) = !given && dueDate != null && !dueDate.isAfter(today)
}

/** Government or insurance health card kept beside the record (images live encrypted in the vault). */
enum class HealthCardKind { Abha, PmJay, Cghs, Esic, StateScheme, Insurance, Other }

data class HealthCard(
    val id: String,
    val memberId: String,
    val kind: HealthCardKind,
    val title: String = "",
    val number: String = "",
    val holderName: String = "",
    val issuer: String = "",
    val validUntil: LocalDate? = null,
    val notes: String = "",
    val createdAtEpochMs: Long = 0,
)

/** A condition in a relative who is not a member of the app (e.g. a grandparent), for the family history map. */
data class RelativeCondition(
    val id: String,
    val relation: String,
    val condition: String,
    val onsetAge: Int? = null,
    val notes: String = "",
    val createdAtEpochMs: Long = 0,
)

/** A question the user wants to ask at the next doctor visit. */
data class VisitQuestion(val id: String, val memberId: String, val text: String, val done: Boolean = false, val createdAtEpochMs: Long = 0)

/** An encrypted image attached to a vaccination or health card. */
enum class MediaKind { VaccineCertificate, CardFront, CardBack }

data class Media(val id: String, val memberId: String, val ownerId: String, val kind: MediaKind, val createdAtEpochMs: Long = 0)
