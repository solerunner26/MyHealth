package com.solerunner.myhealth.ui.medicines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.MedicineForm
import java.time.Instant
import java.time.LocalDate

/**
 * Editable copy of a [Medicine] used by the editor dialog.
 *
 * Kept as plain mutable state (not a `data class` in a StateFlow) because the dialog is short-lived and
 * every keystroke should only recompose the dialog, not the medicine list behind it.
 */
class MedicineDraft(initial: Medicine?) {

    var name by mutableStateOf(initial?.name.orEmpty())
    var molecule by mutableStateOf(initial?.molecule.orEmpty())
    var strength by mutableStateOf(initial?.strength.orEmpty())
    var form by mutableStateOf(initial?.form ?: MedicineForm.TABLET)
    var frequency by mutableStateOf(initial?.frequencyText.orEmpty())
    var timesPerDay by mutableStateOf(initial?.timesPerDay?.toString().orEmpty())
    var durationDays by mutableStateOf(initial?.durationDays?.toString().orEmpty())
    var startDate by mutableStateOf<LocalDate?>(initial?.startDate)
    var endDate by mutableStateOf<LocalDate?>(initial?.endDate)
    var reason by mutableStateOf(initial?.reason.orEmpty())
    var prescribedBy by mutableStateOf(initial?.prescribedBy.orEmpty())
    var notes by mutableStateOf(initial?.notes.orEmpty())
    var reminderEnabled by mutableStateOf(initial?.reminderEnabled ?: false)
    var reminderTimes by mutableStateOf(initial?.reminderTimes?.joinToString(", ").orEmpty())

    fun isValid(): Boolean = name.trim().length >= 2

    fun toMedicine(initial: Medicine?): Medicine = Medicine(
        id = initial?.id ?: 0L,
        name = name.trim(),
        molecule = molecule.trim().takeIf { it.isNotEmpty() },
        form = form,
        strength = strength.trim().takeIf { it.isNotEmpty() },
        frequencyText = frequency.trim().takeIf { it.isNotEmpty() },
        timesPerDay = timesPerDay.toIntOrNull(),
        durationDays = durationDays.toIntOrNull(),
        startDate = startDate,
        endDate = endDate,
        prescribedBy = prescribedBy.trim().takeIf { it.isNotEmpty() },
        reason = reason.trim().takeIf { it.isNotEmpty() },
        reminderEnabled = reminderEnabled,
        reminderTimes = reminderTimes.split(',').map { it.trim() }.filter { it.contains(':') },
        notes = notes.trim(),
        source = initial?.source ?: DataSource.MANUAL,
        createdAt = initial?.createdAt ?: Instant.now(),
    )
}
