package app.myhealth.domain.parse

import app.myhealth.domain.model.VitalType

/** Extracts vital signs from free text with plausibility bounds; Fahrenheit and pounds are converted. */
object VitalsParser {
    private val o = RegexOption.IGNORE_CASE
    private const val SEP = """\s*[:\-=]?\s*"""
    private val bp = Regex("""(?<![a-z])(?:bp|b\.p\.?|blood\s*pressure)$SEP(\d{2,3})\s*/\s*(\d{2,3})""", o)
    private val pulse = Regex("""(?<![a-z])(?:pulse(?:\s*rate)?|heart\s*rate|hr|p\.r\.?)(?![a-z])$SEP(\d{2,3})""", o)
    private val spo2 = Regex("""(?<![a-z])(?:spo2|sp02|sp\s*o2|o2\s*sat(?:uration)?|oxygen\s*saturation|saturation)$SEP(\d{2,3})\s*%?""", o)
    private val weight = Regex("""(?<![a-z])(?:body\s*weight|weight|wt\.?)$SEP(\d{1,3}(?:\.\d+)?)\s*(kgs?|lbs?|pounds)?""", o)
    private val height = Regex("""(?<![a-z])(?:height|ht\.?)$SEP(\d{1,3}(?:\.\d+)?)\s*(cms?|m|in|inch|inches)?(?![a-z])""", o)
    private val temp = Regex("""(?<![a-z])(?:temp(?:erature)?\.?)$SEP(\d{2,3}(?:\.\d+)?)\s*°?\s*([cf])?(?![a-z])""", o)
    private val rr = Regex("""(?<![a-z])(?:resp(?:iratory)?\.?\s*rate|rr|r\.r\.?)(?![a-z])$SEP(\d{1,2})""", o)
    private val bmi = Regex("""(?<![a-z])bmi$SEP(\d{1,2}(?:\.\d+)?)""", o)

    fun parse(lines: List<String>): List<ParsedVital> {
        val out = mutableListOf<ParsedVital>()
        val seen = mutableSetOf<VitalType>()
        fun add(v: ParsedVital?) { if (v != null && seen.add(v.type)) out += v }
        for (line in lines) {
            bp.find(line)?.let { m ->
                val s = m.groupValues[1].toDouble(); val d = m.groupValues[2].toDouble()
                if (s in 60.0..260.0 && d in 30.0..160.0 && s > d) add(ParsedVital(VitalType.BloodPressure, s, d, rawLine = line))
            }
            pulse.find(line)?.let { m -> m.groupValues[1].toDouble().takeIf { it in 25.0..250.0 }?.let { add(ParsedVital(VitalType.Pulse, it, rawLine = line)) } }
            spo2.find(line)?.let { m -> m.groupValues[1].toDouble().takeIf { it in 50.0..100.0 }?.let { add(ParsedVital(VitalType.SpO2, it, rawLine = line)) } }
            weight.find(line)?.let { m ->
                var w = m.groupValues[1].toDouble()
                if (m.groupValues[2].lowercase().startsWith("lb") || m.groupValues[2].lowercase().startsWith("pound")) w *= 0.45359237
                if (w in 1.0..350.0) add(ParsedVital(VitalType.Weight, round1(w), rawLine = line))
            }
            height.find(line)?.let { m ->
                var h = m.groupValues[1].toDouble()
                when (m.groupValues[2].lowercase()) { "m" -> h *= 100; "in", "inch", "inches" -> h *= 2.54 }
                if (h in 30.0..250.0) add(ParsedVital(VitalType.Height, round1(h), rawLine = line))
            }
            temp.find(line)?.let { m ->
                var t = m.groupValues[1].toDouble()
                if (m.groupValues[2].equals("f", true) || t > 45) t = (t - 32) * 5 / 9
                if (t in 30.0..45.0) add(ParsedVital(VitalType.Temperature, round1(t), rawLine = line))
            }
            rr.find(line)?.let { m -> m.groupValues[1].toDouble().takeIf { it in 4.0..60.0 }?.let { add(ParsedVital(VitalType.RespiratoryRate, it, rawLine = line)) } }
            bmi.find(line)?.let { m -> m.groupValues[1].toDouble().takeIf { it in 8.0..80.0 }?.let { add(ParsedVital(VitalType.Bmi, it, rawLine = line)) } }
        }
        return out
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
