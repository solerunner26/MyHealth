package app.myhealth.domain.analytics

import app.myhealth.domain.analytics.Fx.lab
import app.myhealth.domain.analytics.Fx.today
import app.myhealth.domain.analytics.Fx.vital
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthScoreTest {
    @Test fun insufficientDataGivesNoScoreButExplainsWhy() {
        val s = HealthScoreCalculator.calculate(Fx.inputs())
        assertNull(s.score); assertEquals(0.0, s.coverage, 0.0)
        assertEquals(listOf(MissingReason.NoKeyMarkers, MissingReason.NoRecentVitals, MissingReason.NothingToMonitor, MissingReason.NoActivityData), s.components.map { it.missing })
        // vitals alone (30 %) is still below the 40 % coverage threshold
        assertNull(HealthScoreCalculator.calculate(Fx.inputs(vitals = listOf(vital(VitalType.BloodPressure, today.minusDays(1), 118.0, 76.0)))).score)
    }

    @Test fun weightedAndRenormalisedOverAvailableComponents() {
        val r = listOf(lab("ldl", today.minusDays(10), 130.0, null, 100.0), lab("hba1c", today.minusDays(10), 5.4, 4.0, 5.6))
        val v = listOf(vital(VitalType.BloodPressure, today.minusDays(1), 118.0, 76.0))
        val s = HealthScoreCalculator.calculate(Fx.inputs(r, v))
        val labs = s.components.first { it.kind == ScoreComponentKind.LabMarkers }
        assertEquals(mapOf("ldl" to 25, "hba1c" to 100), labs.inputs.associate { it.subject to it.points }) // LDL 30 % over → 100 − 75
        assertEquals(63, labs.score) // mean 62.5 → 63
        assertEquals(100, s.components.first { it.kind == ScoreComponentKind.Vitals }.score)
        assertEquals(100, s.components.first { it.kind == ScoreComponentKind.Monitoring }.score)
        assertEquals(0.85, s.coverage, 1e-9) // activity missing
        // (63*40 + 100*30 + 100*15) / 85 = 82.6 → 83
        assertEquals(83, s.score)
        assertEquals(83.0, s.contributions().values.sum(), 0.6)
    }

    @Test fun activityAndScoringCurves() {
        val acts = (1..7).map { DailyActivity("m", today.minusDays(it.toLong()), steps = 4000) }
        val s = HealthScoreCalculator.calculate(Fx.inputs().copy(activity = acts))
        assertEquals(50, s.components.first { it.kind == ScoreComponentKind.Activity }.score)
        assertEquals(0, HealthScoreCalculator.markerPoints(RangeStatus.CriticalHigh, 0.1))
        assertEquals(0, HealthScoreCalculator.bpPoints(185.0, 100.0)); assertEquals(40, HealthScoreCalculator.bpPoints(145.0, 85.0))
        assertEquals(100, HealthScoreCalculator.bmiPoints(22.0)); assertEquals(70, HealthScoreCalculator.bmiPoints(27.0))
        assertEquals(31.0, VitalRules.bmi(95.0, 175.0)!!, 0.05)
    }
}
