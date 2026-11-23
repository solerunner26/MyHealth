package app.myhealth.domain.analytics

import app.myhealth.domain.catalogue.Panel
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType

enum class VitalStatus { NORMAL, HIGH, LOW }

/** Which line-art icon a tile shows. */
enum class TileIcon { Blood, Sugar, Heart, Kidney, Liver, Thyroid, Vitamin, Lungs, Temperature, Weight, Pulse, Oxygen, Lab }

/**
 * One tile of the Home/Vitals grid (owner's VitalTile spec): a test or vital with its status computed from the
 * biological reference interval printed on the report (or the catalogue / clinical range when none was printed).
 */
data class VitalTileData(
    val key: String,
    val value: Double,
    val value2: Double? = null,
    val decimals: Int,
    val unit: String,
    val status: VitalStatus,
    val refLow: Double?,
    val refHigh: Double?,
    val icon: TileIcon,
    /** Normalised marker position on the range track, clamped to 0.05..0.95 so out-of-range values pin to an edge. */
    val position: Float,
    val analyteId: String? = null,
    val vitalType: VitalType? = null,
) { val attention get() = status != VitalStatus.NORMAL }

object VitalTiles {
    fun status(v: Double, low: Double?, high: Double?): VitalStatus = when {
        low != null && v < low -> VitalStatus.LOW
        high != null && v > high -> VitalStatus.HIGH
        else -> VitalStatus.NORMAL
    }

    /** Reference interval occupies the middle half of the track (0.25..0.75); outside values run to the clamped edges. */
    fun position(v: Double, low: Double?, high: Double?): Float {
        val f = when {
            low != null && high != null && high > low -> 0.25 + (v - low) / (high - low) * 0.5
            high != null && high > 0 -> v / high * 0.75            // "below X": 0..X is the green part
            low != null && low > 0 -> 0.25 + (v - low) / low * 0.5  // "above X"
            else -> 0.5
        }
        return f.toFloat().coerceIn(0.05f, 0.95f)
    }

    fun iconFor(panel: Panel, analyteId: String): TileIcon = when {
        analyteId in setOf("hba1c", "fbs", "ppbs", "rbs", "eag") -> TileIcon.Sugar
        panel == Panel.Blood || panel == Panel.Iron -> TileIcon.Blood
        panel == Panel.Lipids -> TileIcon.Heart
        panel == Panel.Kidney || panel == Panel.Electrolytes || panel == Panel.Urine -> TileIcon.Kidney
        panel == Panel.Liver -> TileIcon.Liver
        panel == Panel.Thyroid || panel == Panel.Hormones -> TileIcon.Thyroid
        panel == Panel.Vitamins -> TileIcon.Vitamin
        else -> TileIcon.Lab
    }

    /** Every parsed lab test → a tile, using the latest result and the reference interval printed with it. */
    fun fromTrend(t: AnalyteTrend): VitalTileData {
        val lo = t.latest.refLow ?: t.bandLow; val hi = t.latest.refHigh ?: t.bandHigh
        val v = t.latest.value
        return VitalTileData("lab:" + t.analyte.id, v, null, t.analyte.decimals, t.analyte.canonicalUnit.takeIf { it != "ratio" }.orEmpty(),
            status(v, lo, hi), lo, hi, iconFor(t.analyte.panel, t.analyte.id), position(v, lo, hi), analyteId = t.analyte.id)
    }

    fun fromVital(v: Vital): VitalTileData {
        val r = VitalRules.normalRange(v.type)
        val r2 = VitalRules.normalRange2(v.type)
        // blood pressure: the worse of systolic / diastolic decides
        val s1 = status(v.value, r?.first, r?.second)
        val s2 = if (v.value2 != null && r2 != null) status(v.value2, r2.first, r2.second) else VitalStatus.NORMAL
        val st = if (s1 != VitalStatus.NORMAL) s1 else s2
        val icon = when (v.type) {
            VitalType.BloodPressure, VitalType.Pulse -> TileIcon.Pulse; VitalType.SpO2 -> TileIcon.Oxygen; VitalType.RespiratoryRate -> TileIcon.Lungs
            VitalType.Temperature -> TileIcon.Temperature; VitalType.Weight, VitalType.Bmi, VitalType.Height -> TileIcon.Weight
            VitalType.BloodGlucose -> TileIcon.Sugar; VitalType.Custom -> TileIcon.Lab
        }
        val dec = if (v.type in setOf(VitalType.Temperature, VitalType.Weight, VitalType.Bmi)) 1 else 0
        return VitalTileData("vital:" + v.type.name, v.value, v.value2, dec, v.unit, st, r?.first, r?.second, icon, position(v.value, r?.first, r?.second), vitalType = v.type)
    }

    /** Header triage pill: "X Attention • Y Normal". */
    fun triage(tiles: List<VitalTileData>): Pair<Int, Int> = tiles.count { it.attention } to tiles.count { !it.attention }

    /** Doctor View: out-of-range first (stable within each group). */
    fun doctorOrder(tiles: List<VitalTileData>): List<VitalTileData> = tiles.sortedBy { if (it.attention) 0 else 1 }
}
