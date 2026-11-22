package com.solerunner.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.PillTone
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.MedicineForm
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.TreatmentKind
import com.solerunner.myhealth.domain.model.ValueStatus
import java.util.Locale

@Composable
fun MeasurementKind.label(): String = when (this) {
    MeasurementKind.BLOOD_PRESSURE -> stringResource(R.string.vitals_kind_blood_pressure)
    MeasurementKind.PULSE -> stringResource(R.string.vitals_kind_pulse)
    MeasurementKind.SPO2 -> stringResource(R.string.vitals_kind_spo2)
    MeasurementKind.BLOOD_GLUCOSE -> stringResource(R.string.vitals_kind_blood_glucose)
    MeasurementKind.WEIGHT -> stringResource(R.string.vitals_kind_weight)
    MeasurementKind.HEIGHT -> stringResource(R.string.vitals_kind_height)
    MeasurementKind.TEMPERATURE -> stringResource(R.string.vitals_kind_temperature)
    MeasurementKind.RESPIRATORY_RATE -> stringResource(R.string.vitals_kind_respiratory_rate)
    MeasurementKind.WAIST -> stringResource(R.string.vitals_kind_waist)
    MeasurementKind.BMI -> stringResource(R.string.vitals_kind_bmi)
    MeasurementKind.CUSTOM -> stringResource(R.string.vitals_kind_custom)
}

@Composable
fun ValueStatus.label(): String = when (this) {
    ValueStatus.VERY_LOW, ValueStatus.LOW -> stringResource(R.string.records_flag_low)
    ValueStatus.VERY_HIGH, ValueStatus.HIGH -> stringResource(R.string.records_flag_high)
    ValueStatus.IN_RANGE -> stringResource(R.string.records_flag_normal)
    ValueStatus.UNKNOWN -> stringResource(R.string.records_flag_unknown)
}

fun ValueStatus.tone(): PillTone = when (this) {
    ValueStatus.VERY_HIGH, ValueStatus.VERY_LOW -> PillTone.ALERT
    ValueStatus.HIGH, ValueStatus.LOW -> PillTone.WARN
    ValueStatus.IN_RANGE -> PillTone.GOOD
    ValueStatus.UNKNOWN -> PillTone.NEUTRAL
}

@Composable
fun ReportKind.label(): String = when (this) {
    ReportKind.LAB -> stringResource(R.string.records_kind_lab)
    ReportKind.IMAGING -> stringResource(R.string.records_kind_imaging)
    ReportKind.PRESCRIPTION -> stringResource(R.string.records_kind_prescription)
    ReportKind.PHARMACY_BILL -> stringResource(R.string.records_kind_bill)
    ReportKind.DISCHARGE -> stringResource(R.string.records_kind_discharge)
    ReportKind.CONSULTATION -> stringResource(R.string.records_kind_consultation)
    ReportKind.OTHER -> stringResource(R.string.records_kind_other)
}

@Composable
fun MedicineForm.label(): String = when (this) {
    MedicineForm.TABLET -> stringResource(R.string.meds_form_tablet)
    MedicineForm.CAPSULE -> stringResource(R.string.meds_form_capsule)
    MedicineForm.SYRUP -> stringResource(R.string.meds_form_syrup)
    MedicineForm.INJECTION -> stringResource(R.string.meds_form_injection)
    MedicineForm.DROPS -> stringResource(R.string.meds_form_drops)
    MedicineForm.CREAM -> stringResource(R.string.meds_form_cream)
    MedicineForm.INHALER -> stringResource(R.string.meds_form_inhaler)
    MedicineForm.OINTMENT -> stringResource(R.string.meds_form_ointment)
    MedicineForm.POWDER -> stringResource(R.string.meds_form_powder)
    MedicineForm.OTHER -> stringResource(R.string.meds_form_other)
}

@Composable
fun TreatmentKind.label(): String = when (this) {
    TreatmentKind.SURGERY -> stringResource(R.string.treatments_kind_surgery)
    TreatmentKind.PROCEDURE -> stringResource(R.string.treatments_kind_procedure)
    TreatmentKind.VACCINATION -> stringResource(R.string.treatments_kind_vaccination)
    TreatmentKind.THERAPY -> stringResource(R.string.treatments_kind_therapy)
    TreatmentKind.DENTAL -> stringResource(R.string.treatments_kind_dental)
    TreatmentKind.PHYSIOTHERAPY -> stringResource(R.string.treatments_kind_physiotherapy)
    TreatmentKind.HOSPITALISATION -> stringResource(R.string.treatments_kind_hospitalisation)
    TreatmentKind.OTHER -> stringResource(R.string.treatments_kind_other)
}

@Composable
fun Sex.label(): String = when (this) {
    Sex.FEMALE -> stringResource(R.string.sex_female)
    Sex.MALE -> stringResource(R.string.sex_male)
    Sex.OTHER -> stringResource(R.string.sex_other)
    Sex.UNSPECIFIED -> stringResource(R.string.sex_unspecified)
}

/** Default display unit per kind; stored measurements may override it. */
fun MeasurementKind.defaultUnit(): String? = when (this) {
    MeasurementKind.BLOOD_PRESSURE -> "mmHg"
    MeasurementKind.PULSE -> "bpm"
    MeasurementKind.SPO2 -> "%"
    MeasurementKind.BLOOD_GLUCOSE -> "mg/dL"
    MeasurementKind.WEIGHT -> "kg"
    MeasurementKind.HEIGHT -> "cm"
    MeasurementKind.TEMPERATURE -> "°C"
    MeasurementKind.RESPIRATORY_RATE -> "/min"
    MeasurementKind.WAIST -> "cm"
    MeasurementKind.BMI -> "kg/m²"
    MeasurementKind.CUSTOM -> null
}

/** "120 / 80", "72", "5.2" — never localized decimals beyond what the record stored. */
fun formatNumber(value: Double, decimals: Int = 1): String {
    val text = String.format(Locale.getDefault(), "%.${decimals}f", value)
    val trimmed = text.trimEnd('0').trimEnd('.')
    return trimmed.ifEmpty { "0" }
}

fun Measurement.displayValue(): String =
    if (secondaryValue != null) "${formatNumber(primaryValue, 0)}/${formatNumber(secondaryValue, 0)}"
    else formatNumber(primaryValue)

fun Measurement.displayWithUnit(): String {
    val unit = unit ?: kind.defaultUnit()
    return if (unit != null) "${displayValue()} $unit" else displayValue()
}
