package app.myhealth.domain.analytics

import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType

enum class VitalBand { Low, Normal, Elevated, High, VeryHigh, Unknown }

/** Adult vital-sign bands (ACC/AHA BP categories; WHO BMI; typical resting pulse, SpO2, temperature). */
object VitalRules {
    fun band(v: Vital): VitalBand = when (v.type) {
        VitalType.BloodPressure -> bp(v.value, v.value2 ?: 0.0)
        VitalType.Pulse -> when { v.value < 50 -> VitalBand.Low; v.value <= 100 -> VitalBand.Normal; v.value <= 120 -> VitalBand.Elevated; else -> VitalBand.High }
        VitalType.SpO2 -> when { v.value >= 95 -> VitalBand.Normal; v.value >= 90 -> VitalBand.Low; else -> VitalBand.VeryHigh /* severe */ }
        VitalType.Bmi -> bmiBand(v.value)
        VitalType.Temperature -> when { v.value < 35.0 -> VitalBand.Low; v.value < 37.5 -> VitalBand.Normal; v.value < 38.0 -> VitalBand.Elevated; v.value < 39.5 -> VitalBand.High; else -> VitalBand.VeryHigh }
        VitalType.RespiratoryRate -> when { v.value < 12 -> VitalBand.Low; v.value <= 20 -> VitalBand.Normal; v.value <= 24 -> VitalBand.Elevated; else -> VitalBand.High }
        VitalType.BloodGlucose -> when { v.value < 70 -> VitalBand.Low; v.value <= 140 -> VitalBand.Normal; v.value < 200 -> VitalBand.Elevated; else -> VitalBand.High }
        VitalType.Weight, VitalType.Height, VitalType.Custom -> VitalBand.Unknown
    }

    fun bp(sys: Double, dia: Double): VitalBand = when {
        sys >= 180 || dia >= 120 -> VitalBand.VeryHigh
        sys >= 140 || dia >= 90 -> VitalBand.High
        sys >= 130 || dia >= 80 -> VitalBand.Elevated
        sys < 90 || dia < 60 -> VitalBand.Low
        else -> VitalBand.Normal
    }

    fun bmiBand(bmi: Double) = when { bmi < 18.5 -> VitalBand.Low; bmi < 25 -> VitalBand.Normal; bmi < 30 -> VitalBand.Elevated; bmi < 35 -> VitalBand.High; else -> VitalBand.VeryHigh }

    /** Typical adult band used to shade vital charts (systolic for BP; diastolic in [normalRange2]). */
    fun normalRange(t: VitalType): Pair<Double, Double>? = when (t) {
        VitalType.BloodPressure -> 90.0 to 129.0
        VitalType.Pulse -> 50.0 to 100.0
        VitalType.SpO2 -> 95.0 to 100.0
        VitalType.Bmi -> 18.5 to 24.9
        VitalType.Temperature -> 36.1 to 37.4
        VitalType.RespiratoryRate -> 12.0 to 20.0
        VitalType.BloodGlucose -> 70.0 to 140.0
        VitalType.Weight, VitalType.Height, VitalType.Custom -> null
    }

    fun normalRange2(t: VitalType): Pair<Double, Double>? = if (t == VitalType.BloodPressure) 60.0 to 79.0 else null

    fun bmi(weightKg: Double, heightCm: Double): Double? = if (heightCm <= 0 || weightKg <= 0) null else
        Math.round(weightKg / ((heightCm / 100) * (heightCm / 100)) * 10) / 10.0
}
