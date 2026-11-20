package com.solerunner.myhealth.domain.vitals

import com.solerunner.myhealth.core.util.Numbers
import com.solerunner.myhealth.domain.lab.ParsedVitalRow
import com.solerunner.myhealth.domain.model.MeasurementKind
import java.util.Locale

/**
 * Reads the "vitals" block that most Indian labs and hospitals print above the analyte table:
 * blood pressure, pulse, SpO2, weight, height, BMI, temperature, respiratory rate.
 *
 * Blood sugar is intentionally NOT handled here: it is an analyte and is parsed by the lab parser, so
 * handling it in both places would create duplicate rows in the timeline.
 */
object VitalExtractor {

    private val bpPattern = Regex("(?<!\\d)(\\d{2,3})\\s*[/\\]\\s*(\\d{2,3})(?!\\d)")
    private val pulsePattern = Regex("(?i)\\b(?:pulse|heart\\s*rate|hr|pr)\\b[^0-9]{0,14}(\\d{2,3})")
    private val spo2Pattern = Regex("(?i)\\b(?:spo2|sp\\s*o2|oxygen\\s*saturation|o2\\s*sat|saturation)\\b[^0-9]{0,14}(\\d{2,3})\\s*%?")
    private val weightPattern = Regex("(?i)\\b(?:weight|wt)\\b[^0-9]{0,14}(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*(kg|kgs|kilogram|lbs|lb|pound)?")
    private val heightPattern = Regex("(?i)\\b(?:height|ht)\\b[^0-9]{0,14}(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*(cm|cms|m|ft|feet|inch|in)?")
    private val bmiPattern = Regex("(?i)\\bbmi\\b[^0-9]{0,14}(\\d{1,2}(?:[.,]\\d{1,2})?)")
    private val tempPattern = Regex("(?i)\\b(?:temp|temperature)\\b[^0-9]{0,14}(\\d{2,3}(?:[.,]\\d{1,2})?)\\s*(°?\\s*[cf])?")
    private val rrPattern = Regex("(?i)\\b(?:respiratory\\s*rate|resp\\s*rate|rr)\\b[^0-9]{0,14}(\\d{1,3})")
    private val waistPattern = Regex("(?i)\\b(?:waist|abdomen)\\b[^0-9]{0,14}(\\d{2,3}(?:[.,]\\d{1,2})?)\\s*(cm|inch|in)?")

    fun extract(text: String): List<ParsedVitalRow> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<ParsedVitalRow>()
        val seen = mutableSetOf<MeasurementKind>()

        fun add(row: ParsedVitalRow?) {
            if (row != null && seen.add(row.kind)) out += row
        }

        // Blood pressure appears as "BP 120/80", "Blood Pressure : 120 / 80 mmHg" or as two lines
        // "Systolic 120" / "Diastolic 80" (handled by the generic numeric fallback below).
        for (line in text.lines()) {
            val l = line.lowercase(Locale.ROOT)
            val labelledBp = listOf("bp", "blood pressure", "b.p", "systolic").any { it in l }
            if (labelledBp) {
                bpPattern.find(line)?.let { m ->
                    val sys = Numbers.parse(m.groupValues[1])?.value
                    val dia = Numbers.parse(m.groupValues[2])?.value
                    if (sys != null && dia != null && sys > dia && sys in 60.0..260.0 && dia in 30.0..160.0) {
                        add(ParsedVitalRow(MeasurementKind.BLOOD_PRESSURE, sys, dia, "mmHg", line.trim()))
                    }
                }
            }
        }

        pulsePattern.find(text)?.let { m ->
            Numbers.parse(m.groupValues[1])?.value?.takeIf { it in 30.0..250.0 }?.let {
                add(ParsedVitalRow(MeasurementKind.PULSE, it, null, "bpm", m.value.trim()))
            }
        }
        spo2Pattern.find(text)?.let { m ->
            Numbers.parse(m.groupValues[1])?.value?.takeIf { it in 60.0..100.0 }?.let {
                add(ParsedVitalRow(MeasurementKind.SPO2, it, null, "%", m.value.trim()))
            }
        }
        weightPattern.find(text)?.let { m ->
            val value = Numbers.parse(m.groupValues[1])?.value
            val unit = m.groupValues[2].lowercase(Locale.ROOT)
            if (value != null) {
                val kg = if (unit.startsWith("lb") || unit.startsWith("pound")) value * 0.4536 else value
                if (kg in 2.0..400.0) add(ParsedVitalRow(MeasurementKind.WEIGHT, kg, null, "kg", m.value.trim()))
            }
        }
        heightPattern.find(text)?.let { m ->
            val value = Numbers.parse(m.groupValues[1])?.value
            val unit = m.groupValues[2].lowercase(Locale.ROOT)
            if (value != null) {
                val cm = when {
                    unit.startsWith("ft") || unit.startsWith("feet") -> value * 30.48
                    unit.startsWith("in") -> value * 2.54
                    unit == "m" -> value * 100
                    else -> value
                }
                if (cm in 30.0..250.0) add(ParsedVitalRow(MeasurementKind.HEIGHT, cm, null, "cm", m.value.trim()))
            }
        }
        bmiPattern.find(text)?.let { m ->
            Numbers.parse(m.groupValues[1])?.value?.takeIf { it in 8.0..80.0 }?.let {
                add(ParsedVitalRow(MeasurementKind.BMI, it, null, "kg/m2", m.value.trim()))
            }
        }
        tempPattern.find(text)?.let { m ->
            val value = Numbers.parse(m.groupValues[1])?.value
            val unit = m.groupValues[2].replace(" ", "").lowercase(Locale.ROOT)
            if (value != null) {
                val celsius = if (unit.contains("f")) (value - 32) * 5 / 9 else value
                if (celsius in 30.0..45.0) add(ParsedVitalRow(MeasurementKind.TEMPERATURE, celsius, null, "°C", m.value.trim()))
            }
        }
        rrPattern.find(text)?.let { m ->
            Numbers.parse(m.groupValues[1])?.value?.takeIf { it in 6.0..60.0 }?.let {
                add(ParsedVitalRow(MeasurementKind.RESPIRATORY_RATE, it, null, "/min", m.value.trim()))
            }
        }
        waistPattern.find(text)?.let { m ->
            val value = Numbers.parse(m.groupValues[1])?.value
            val unit = m.groupValues[2].lowercase(Locale.ROOT)
            if (value != null) {
                val cm = if (unit.startsWith("in")) value * 2.54 else value
                if (cm in 30.0..250.0) add(ParsedVitalRow(MeasurementKind.WAIST, cm, null, "cm", m.value.trim()))
            }
        }
        return out
    }
}
