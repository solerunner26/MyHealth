package com.solerunner.myhealth.domain.model

/** Which kind of document a [Report] holds. */
enum class ReportKind { LAB, IMAGING, PRESCRIPTION, PHARMACY_BILL, DISCHARGE, CONSULTATION, OTHER }

/** Where a record came from. Drives the small provenance badge shown in tables. */
enum class DataSource { MANUAL, REPORT_IMPORT, DEVICE_SYNC }

/** How a user authenticates (or not) — the app works anonymously, by design. */
enum class AuthProvider { NONE, GOOGLE }

enum class Sex { FEMALE, MALE, OTHER, UNSPECIFIED }

/** Interpretation of one measured value against its reference range. */
enum class ValueStatus { VERY_LOW, LOW, IN_RANGE, HIGH, VERY_HIGH, UNKNOWN }

/** Numeric vitals the user can log by hand or that we can read out of a report. */
enum class MeasurementKind(
    val key: String,
    /** true when the reading needs two numbers, e.g. 120/80 blood pressure. */
    val isPaired: Boolean = false,
) {
    BLOOD_PRESSURE("blood_pressure", isPaired = true),
    PULSE("pulse"),
    SPO2("spo2"),
    BLOOD_GLUCOSE("blood_glucose"),
    WEIGHT("weight"),
    HEIGHT("height"),
    TEMPERATURE("temperature"),
    RESPIRATORY_RATE("respiratory_rate"),
    WAIST("waist"),
    BMI("bmi"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromKey(key: String): MeasurementKind = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/** Which direction of change is good news for a biomarker. */
enum class DirectionOfGood { LOWER, HIGHER, BALANCED, UNKNOWN }

/** Direction of the observed change over time. */
enum class TrendDirection { RISING, FALLING, STABLE, UNKNOWN }

enum class AnalyteCategory { HEMATOLOGY, BIOCHEMISTRY, LIPIDS, DIABETES, THYROID, LIVER, KIDNEY, ELECTROLYTES, VITAMINS, HORMONES, CARDIAC, INFLAMMATION, URINE, OTHER }

enum class TreatmentKind { SURGERY, PROCEDURE, VACCINATION, THERAPY, DENTAL, PHYSIOTHERAPY, HOSPITALISATION, OTHER }

enum class MedicineForm { TABLET, CAPSULE, SYRUP, INJECTION, DROPS, CREAM, INHALER, OINTMENT, POWDER, OTHER }

/** How serious a focus item is. There is no colour attached: severity is shown by icon + wording. */
enum class Severity { ATTENTION, WATCH, INFO }
