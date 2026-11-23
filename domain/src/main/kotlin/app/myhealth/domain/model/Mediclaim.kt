package app.myhealth.domain.model

import java.time.LocalDate

enum class PolicyType { Individual, FamilyFloater, TopUp, SuperTopUp, Group, Government, CriticalIllness }

/**
 * Coverage terms of a health policy, as printed in its schedule / policy wording (all optional: the user fills what
 * they know). Units: days, months, percent, rupees.
 */
data class PolicyFeatures(
    val roomRent: String = "",
    val coPayPercent: Int? = null,
    val deductible: Double? = null,
    val initialWaitingDays: Int? = null,
    val pedWaitingMonths: Int? = null,
    val specificWaitingMonths: Int? = null,
    val preHospitalisationDays: Int? = null,
    val postHospitalisationDays: Int? = null,
    val noClaimBonusPercent: Int? = null,
    val ambulanceLimit: Double? = null,
    val dayCare: Boolean = false,
    val restoration: Boolean = false,
    val ayush: Boolean = false,
    val maternity: Boolean = false,
    val opd: Boolean = false,
    val healthCheckup: Boolean = false,
    val domiciliary: Boolean = false,
    val organDonor: Boolean = false,
    val other: String = "",
)

data class MediclaimPolicy(
    val id: String,
    /** The member who holds the policy (owner of the record). */
    val memberId: String,
    val insurer: String,
    val planName: String = "",
    val policyNumber: String = "",
    val type: PolicyType = PolicyType.Individual,
    val sumInsured: Double = 0.0,
    /** Cumulative bonus (no-claim bonus / loyalty) added to the cover. */
    val bonus: Double = 0.0,
    val premium: Double? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val coveredMemberIds: List<String> = emptyList(),
    val tpaName: String = "",
    val tpaPhone: String = "",
    val helpline: String = "",
    val agentName: String = "",
    val agentPhone: String = "",
    val features: PolicyFeatures = PolicyFeatures(),
    val notes: String = "",
    val createdAtEpochMs: Long = 0,
) {
    val totalCover get() = sumInsured + bonus
    fun daysToExpiry(today: LocalDate): Long? = endDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
}

enum class ClaimKind { Cashless, Reimbursement }
enum class ClaimStatus { Intimated, Submitted, Approved, PartiallyApproved, Rejected, Settled }

data class MediclaimClaim(
    val id: String,
    val policyId: String,
    val memberId: String,
    val hospital: String = "",
    val admittedOn: LocalDate? = null,
    val dischargedOn: LocalDate? = null,
    val kind: ClaimKind = ClaimKind.Cashless,
    val claimedAmount: Double = 0.0,
    val approvedAmount: Double? = null,
    val status: ClaimStatus = ClaimStatus.Intimated,
    val claimNumber: String = "",
    val notes: String = "",
    val createdAtEpochMs: Long = 0,
) {
    /** What the claim used up from the cover (approved, partly approved or settled). */
    val used get() = if (status in setOf(ClaimStatus.Approved, ClaimStatus.PartiallyApproved, ClaimStatus.Settled)) approvedAmount ?: claimedAmount else 0.0
}

data class NetworkHospital(
    val id: String,
    val policyId: String,
    val name: String,
    val address: String = "",
    val city: String = "",
    val pincode: String = "",
    val phone: String = "",
)
