package app.myhealth.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class MedicineForm { Tablet, Capsule, Syrup, Injection, Drops, Inhaler, Ointment, Other }

/** Frequency of doses. [dosesPerDay] drives default reminder times and adherence math. */
enum class Frequency(val dosesPerDay: Int, val everyNDays: Int = 1) {
    OnceDaily(1), TwiceDaily(2), ThriceDaily(3), FourTimesDaily(4), AtBedtime(1), EveryOtherDay(1, 2), Weekly(1, 7), AsNeeded(0);

    fun defaultTimes(): List<LocalTime> = when (this) {
        OnceDaily, EveryOtherDay, Weekly -> listOf(LocalTime.of(9, 0))
        TwiceDaily -> listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))
        ThriceDaily -> listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
        FourTimesDaily -> listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0))
        AtBedtime -> listOf(LocalTime.of(22, 0))
        AsNeeded -> emptyList()
    }
}

data class Medicine(
    val id: String,
    val memberId: String,
    val name: String,
    val strength: String = "",
    val form: MedicineForm = MedicineForm.Tablet,
    val frequency: Frequency = Frequency.OnceDaily,
    val dose: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val reminderTimes: List<LocalTime> = emptyList(),
    val remindersEnabled: Boolean = false,
    val instructions: String = "",
    val prescribedBy: String = "",
    val reportId: String? = null,
    val stopped: Boolean = false,
) {
    /** Active = not stopped, started, and not past its end date. */
    fun isActiveOn(day: LocalDate): Boolean = !stopped && !startDate.isAfter(day) && (endDate == null || !endDate.isBefore(day))

    val durationDays: Long? get() = endDate?.let { java.time.temporal.ChronoUnit.DAYS.between(startDate, it) + 1 }
}

enum class DoseStatus { Taken, Skipped, Snoozed }

data class DoseLog(
    val id: String,
    val medicineId: String,
    val scheduledAt: Instant,
    val status: DoseStatus,
    val recordedAt: Instant,
)

enum class TreatmentKind { Procedure, Surgery, Therapy, Vaccination, Consultation, Other }

data class Treatment(
    val id: String,
    val memberId: String,
    val name: String,
    val kind: TreatmentKind = TreatmentKind.Procedure,
    val date: LocalDate,
    val provider: String = "",
    val followUpDate: LocalDate? = null,
    val notes: String = "",
    val followUpDone: Boolean = false,
)
