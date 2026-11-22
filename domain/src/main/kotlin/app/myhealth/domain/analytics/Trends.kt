package app.myhealth.domain.analytics

import app.myhealth.domain.catalogue.Analyte
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.catalogue.Direction
import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Sex
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class RangeStatus { Normal, Low, High, CriticalLow, CriticalHigh, Unknown;
    val isAbnormal get() = this != Normal && this != Unknown
    val isCritical get() = this == CriticalLow || this == CriticalHigh
}

/** Movement relative to the analyte's direction of improvement (not just up/down). */
enum class TrendDirection { Improving, Worsening, Stable, Insufficient }

data class TrendPoint(val date: LocalDate, val value: Double, val comparator: Comparator, val refLow: Double?, val refHigh: Double?, val reportId: String?, val status: RangeStatus)

data class AnalyteTrend(
    val analyte: Analyte,
    val points: List<TrendPoint>,
    val latest: TrendPoint,
    val previous: TrendPoint?,
    val absoluteChange: Double?,
    val percentChange: Double?,
    /** Canonical units per month between previous and latest. */
    val ratePerMonth: Double?,
    val percentPerMonth: Double?,
    val daysBetween: Long?,
    val direction: TrendDirection,
    /** Reference band used for the chart: latest printed range, else catalogue. */
    val bandLow: Double?,
    val bandHigh: Double?,
)

object TrendEngine {
    const val DAYS_PER_MONTH = 30.4375
    private const val STABLE_PCT = 2.0

    fun status(a: Analyte, value: Double, low: Double?, high: Double?): RangeStatus {
        if (a.criticalLow != null && value < a.criticalLow) return RangeStatus.CriticalLow
        if (a.criticalHigh != null && value > a.criticalHigh) return RangeStatus.CriticalHigh
        if (low == null && high == null) return RangeStatus.Unknown
        if (low != null && value < low) return RangeStatus.Low
        if (high != null && value > high) return RangeStatus.High
        return RangeStatus.Normal
    }

    /** Distance outside the band, relative to the bound crossed (0 when inside). */
    fun relativeExcess(value: Double, low: Double?, high: Double?): Double = when {
        low != null && value < low -> if (low == 0.0) 1.0 else (low - value) / low
        high != null && value > high -> if (high == 0.0) 1.0 else (value - high) / high
        else -> 0.0
    }

    fun windowStart(today: LocalDate, historyYears: Int): LocalDate = today.minusYears(historyYears.toLong())

    fun trend(results: List<LabResult>, analyteId: String, today: LocalDate, historyYears: Int, sex: Sex? = null, age: Int? = null): AnalyteTrend? {
        val a = AnalyteCatalogue.byId(analyteId) ?: return null
        val from = windowStart(today, historyYears)
        val catalogue = a.rangeFor(sex, age)
        val pts = results.asSequence().filter { it.analyteId == analyteId && !it.date.isBefore(from) && !it.date.isAfter(today) }
            .sortedBy { it.date }
            .map { r ->
                val lo = r.refLow ?: if (r.refHigh == null) catalogue?.low else null
                val hi = r.refHigh ?: if (r.refLow == null) catalogue?.high else null
                TrendPoint(r.date, r.value, r.comparator, lo, hi, r.reportId, status(a, r.value, lo, hi))
            }.toList()
        if (pts.isEmpty()) return null
        val latest = pts.last(); val prev = pts.getOrNull(pts.size - 2)
        val abs = prev?.let { latest.value - it.value }
        val pct = prev?.takeIf { it.value != 0.0 }?.let { (latest.value - it.value) / abs(it.value) * 100 }
        val days = prev?.let { ChronoUnit.DAYS.between(it.date, latest.date) }
        val months = days?.takeIf { it > 0 }?.let { it / DAYS_PER_MONTH }
        return AnalyteTrend(
            analyte = a, points = pts, latest = latest, previous = prev, absoluteChange = abs, percentChange = pct,
            ratePerMonth = if (abs != null && months != null) abs / months else null,
            percentPerMonth = if (pct != null && months != null) pct / months else null,
            daysBetween = days, direction = direction(a, prev, latest, pct),
            bandLow = latest.refLow, bandHigh = latest.refHigh,
        )
    }

    fun all(results: List<LabResult>, today: LocalDate, historyYears: Int, sex: Sex? = null, age: Int? = null): List<AnalyteTrend> =
        results.map { it.analyteId }.distinct().mapNotNull { trend(results, it, today, historyYears, sex, age) }
            .sortedWith(compareBy<AnalyteTrend> { it.analyte.panel.ordinal }.thenBy { AnalyteCatalogue.all.indexOf(it.analyte) })

    fun direction(a: Analyte, prev: TrendPoint?, latest: TrendPoint, pct: Double?): TrendDirection {
        if (prev == null || pct == null) return TrendDirection.Insufficient
        if (abs(pct) < STABLE_PCT) return TrendDirection.Stable
        val beforeOut = relativeExcess(prev.value, prev.refLow, prev.refHigh)
        val nowOut = relativeExcess(latest.value, latest.refLow, latest.refHigh)
        // Moving into / out of the band always decides, whatever the analyte's preferred direction.
        if (beforeOut > 0 || nowOut > 0) return when {
            nowOut < beforeOut -> TrendDirection.Improving
            nowOut > beforeOut -> TrendDirection.Worsening
            else -> TrendDirection.Stable
        }
        // Both in range: only "better-is-lower/higher" analytes have a meaningful direction, and only for clear moves.
        if (abs(pct) < 10.0) return TrendDirection.Stable
        return when (a.direction) {
            Direction.LowerIsBetter -> if (pct < 0) TrendDirection.Improving else TrendDirection.Worsening
            Direction.HigherIsBetter -> if (pct > 0) TrendDirection.Improving else TrendDirection.Worsening
            Direction.InRange -> TrendDirection.Stable
        }
    }
}

/** RFC 4180 CSV writer: quotes fields containing comma, quote or newline; neutralises spreadsheet formula injection. */
object Csv {
    fun row(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    fun escape(raw: String): String {
        // A leading = + - @ makes spreadsheets execute the cell as a formula (CSV injection). Prefix with a quote.
        val v = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r" && raw.toDoubleOrNull() == null) "'$raw" else raw
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun document(header: List<String>, rows: List<List<String>>): String = buildString {
        append(row(header)).append("\r\n")
        rows.forEach { append(row(it)).append("\r\n") }
    }
}

object TrendCsv {
    /** One line per measurement. [name] gives the localised analyte name; [header] the localised column titles. */
    fun history(trends: List<AnalyteTrend>, name: (Analyte) -> String, status: (RangeStatus) -> String, header: List<String>): String =
        Csv.document(header, trends.flatMap { t ->
            t.points.map { p ->
                listOf(p.date.toString(), name(t.analyte), p.comparator.symbol + app.myhealth.domain.format.ValueFormat.number(p.value, t.analyte.decimals),
                    t.analyte.canonicalUnit, p.refLow?.let { app.myhealth.domain.format.ValueFormat.number(it, t.analyte.decimals) } ?: "",
                    p.refHigh?.let { app.myhealth.domain.format.ValueFormat.number(it, t.analyte.decimals) } ?: "", status(p.status))
            }
        })
}
