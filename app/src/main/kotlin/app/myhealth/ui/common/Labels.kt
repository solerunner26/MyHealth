package app.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.myhealth.R
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.importing.DraftError
import app.myhealth.domain.model.BloodGroup
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.MedicineForm
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Sex
import app.myhealth.domain.model.TreatmentKind
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.parse.Confidence
import app.myhealth.domain.parse.ParseWarning
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable @ReadOnlyComposable
fun analyteName(id: String): String = AnalyteNames.res(id)?.let { stringResource(it) } ?: AnalyteCatalogue.byId(id)?.name ?: id

@Composable @ReadOnlyComposable
fun label(s: Sex) = stringResource(when (s) { Sex.Female -> R.string.sex_female; Sex.Male -> R.string.sex_male; Sex.Other -> R.string.sex_other; Sex.Unspecified -> R.string.sex_unspecified })

@Composable @ReadOnlyComposable
fun label(r: Relationship) = stringResource(when (r) {
    Relationship.Self -> R.string.rel_self; Relationship.Spouse -> R.string.rel_spouse; Relationship.Child -> R.string.rel_child
    Relationship.Parent -> R.string.rel_parent; Relationship.Sibling -> R.string.rel_sibling; Relationship.Grandparent -> R.string.rel_grandparent
    Relationship.Other -> R.string.rel_other
})

@Composable @ReadOnlyComposable
fun label(b: BloodGroup) = if (b == BloodGroup.Unknown) stringResource(R.string.blood_unknown) else b.label

@Composable @ReadOnlyComposable
fun label(t: ReportType) = stringResource(when (t) {
    ReportType.LabReport -> R.string.rtype_lab; ReportType.Prescription -> R.string.rtype_prescription; ReportType.PharmacyBill -> R.string.rtype_bill
    ReportType.Imaging -> R.string.rtype_imaging; ReportType.Discharge -> R.string.rtype_discharge; ReportType.Hospital -> R.string.rtype_hospital; ReportType.Other -> R.string.rtype_other
})

@Composable @ReadOnlyComposable
fun label(v: VitalType) = stringResource(when (v) {
    VitalType.BloodPressure -> R.string.vital_bp; VitalType.Pulse -> R.string.vital_pulse; VitalType.SpO2 -> R.string.vital_spo2
    VitalType.Weight -> R.string.vital_weight; VitalType.Height -> R.string.vital_height; VitalType.Bmi -> R.string.vital_bmi
    VitalType.Temperature -> R.string.vital_temp; VitalType.RespiratoryRate -> R.string.vital_rr; VitalType.BloodGlucose -> R.string.vital_glucose
    VitalType.Custom -> R.string.vital_custom
})

@Composable @ReadOnlyComposable
fun label(f: Frequency) = stringResource(when (f) {
    Frequency.OnceDaily -> R.string.freq_once; Frequency.TwiceDaily -> R.string.freq_twice; Frequency.ThriceDaily -> R.string.freq_thrice
    Frequency.FourTimesDaily -> R.string.freq_four; Frequency.AtBedtime -> R.string.freq_bedtime; Frequency.EveryOtherDay -> R.string.freq_alternate
    Frequency.Weekly -> R.string.freq_weekly; Frequency.AsNeeded -> R.string.freq_asneeded
})

@Composable @ReadOnlyComposable
fun label(f: MedicineForm) = stringResource(when (f) {
    MedicineForm.Tablet -> R.string.form_tablet; MedicineForm.Capsule -> R.string.form_capsule; MedicineForm.Syrup -> R.string.form_syrup
    MedicineForm.Injection -> R.string.form_injection; MedicineForm.Drops -> R.string.form_drops; MedicineForm.Inhaler -> R.string.form_inhaler
    MedicineForm.Ointment -> R.string.form_ointment; MedicineForm.Other -> R.string.form_other
})

@Composable @ReadOnlyComposable
fun label(k: TreatmentKind) = stringResource(when (k) {
    TreatmentKind.Procedure -> R.string.treat_procedure; TreatmentKind.Surgery -> R.string.treat_surgery; TreatmentKind.Therapy -> R.string.treat_therapy
    TreatmentKind.Vaccination -> R.string.treat_vaccination; TreatmentKind.Consultation -> R.string.treat_consultation; TreatmentKind.Other -> R.string.treat_other
})

@Composable @ReadOnlyComposable
fun label(w: ParseWarning) = stringResource(when (w) {
    ParseWarning.UnitInferred -> R.string.warn_unit_inferred; ParseWarning.UnitUnknown -> R.string.warn_unit_unknown
    ParseWarning.RangeFromCatalogue -> R.string.warn_range_catalogue; ParseWarning.NoRange -> R.string.warn_no_range
    ParseWarning.UnknownAnalyte -> R.string.warn_unknown; ParseWarning.ImplausibleValue -> R.string.warn_implausible
    ParseWarning.ValueOnNextLine -> R.string.warn_next_line; ParseWarning.ComparatorValue -> R.string.warn_comparator
})

@Composable @ReadOnlyComposable
fun label(c: Confidence) = stringResource(when (c) { Confidence.High -> R.string.conf_high; Confidence.Medium -> R.string.conf_medium; Confidence.Low -> R.string.conf_low })

@Composable @ReadOnlyComposable
fun label(e: DraftError) = stringResource(when (e) {
    DraftError.MissingDate -> R.string.err_missing_date; DraftError.FutureDate -> R.string.err_future_date;
    DraftError.InvalidValue -> R.string.err_invalid_value; DraftError.UnknownAnalyte -> R.string.err_unknown_analyte; DraftError.UnitNotConvertible -> R.string.err_unit
    DraftError.RangeInverted -> R.string.err_range; DraftError.InvalidVital -> R.string.err_vital; DraftError.MissingMedicineName -> R.string.err_med_name
    DraftError.DuplicateAnalyte -> R.string.err_duplicate
})

/** Dates in the user's current UI locale (month names translate; digits stay Latin per D-010). */
@Composable
fun formatDate(d: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withDecimalStyle(java.time.format.DecimalStyle.STANDARD))
}

@Composable
fun formatTime(t: LocalTime): String {
    val locale = LocalConfiguration.current.locales[0]
    return t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withDecimalStyle(java.time.format.DecimalStyle.STANDARD))
}
