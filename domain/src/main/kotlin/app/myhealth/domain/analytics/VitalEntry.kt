package app.myhealth.domain.analytics

import app.myhealth.domain.model.VitalType
import app.myhealth.domain.parse.Numbers

/** Units the user may type a vital in; values are always stored in [VitalType.canonicalUnit]. */
enum class EntryUnit(val symbol: String) { Canonical(""), Pound("lb"), Fahrenheit("°F"), MmolL("mmol/L"), Inch("in") }

enum class VitalEntryError { Missing, NotANumber, OutOfBounds, DiastolicNotBelowSystolic, CustomNameMissing }

data class VitalEntryResult(val value: Double, val value2: Double?, val errors: Map<String, VitalEntryError>) {
    val ok get() = errors.isEmpty()
}

/** Validation and conversion for manual vital entry. Bounds reject typos, not unusual-but-real readings. */
object VitalEntry {
    fun unitsFor(t: VitalType): List<EntryUnit> = when (t) {
        VitalType.Weight -> listOf(EntryUnit.Canonical, EntryUnit.Pound)
        VitalType.Temperature -> listOf(EntryUnit.Canonical, EntryUnit.Fahrenheit)
        VitalType.BloodGlucose -> listOf(EntryUnit.Canonical, EntryUnit.MmolL)
        VitalType.Height -> listOf(EntryUnit.Canonical, EntryUnit.Inch)
        else -> listOf(EntryUnit.Canonical)
    }

    fun bounds(t: VitalType): ClosedFloatingPointRange<Double> = when (t) {
        VitalType.BloodPressure -> 50.0..280.0
        VitalType.Pulse -> 20.0..250.0
        VitalType.SpO2 -> 50.0..100.0
        VitalType.Weight -> 0.5..350.0
        VitalType.Height -> 30.0..250.0
        VitalType.Bmi -> 8.0..80.0
        VitalType.Temperature -> 30.0..45.0
        VitalType.RespiratoryRate -> 4.0..70.0
        VitalType.BloodGlucose -> 10.0..900.0
        VitalType.Custom -> 0.0..1_000_000.0
    }

    fun toCanonical(t: VitalType, v: Double, u: EntryUnit): Double = when (u) {
        EntryUnit.Canonical -> v
        EntryUnit.Pound -> v * 0.45359237
        EntryUnit.Fahrenheit -> (v - 32) * 5 / 9
        EntryUnit.MmolL -> v * 18.016
        EntryUnit.Inch -> v * 2.54
    }

    fun parse(t: VitalType, text: String, text2: String, unit: EntryUnit, customName: String = ""): VitalEntryResult {
        val errors = mutableMapOf<String, VitalEntryError>()
        fun read(key: String, s: String): Double? {
            if (s.isBlank()) { errors[key] = VitalEntryError.Missing; return null }
            val n = Numbers.parse(s) ?: run { errors[key] = VitalEntryError.NotANumber; return null }
            val c = toCanonical(t, n, unit)
            val b = if (key == "value2") 25.0..180.0 else bounds(t)
            if (c !in b) { errors[key] = VitalEntryError.OutOfBounds; return null }
            return c
        }
        val v = read("value", text)
        val v2 = if (t.paired) read("value2", text2) else null
        if (t.paired && v != null && v2 != null && v2 >= v) errors["value2"] = VitalEntryError.DiastolicNotBelowSystolic
        if (t == VitalType.Custom && customName.isBlank()) errors["name"] = VitalEntryError.CustomNameMissing
        return VitalEntryResult(v?.let { round(t, it) } ?: 0.0, v2?.let { round(t, it) }, errors)
    }

    private fun round(t: VitalType, v: Double): Double = when (t) {
        VitalType.BloodPressure, VitalType.Pulse, VitalType.SpO2, VitalType.RespiratoryRate, VitalType.BloodGlucose -> Math.round(v).toDouble()
        else -> Math.round(v * 10) / 10.0
    }
}
