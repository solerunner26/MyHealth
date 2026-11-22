package com.solerunner.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.PillTone
import com.solerunner.myhealth.domain.model.FocusCode
import com.solerunner.myhealth.domain.model.FocusItem
import com.solerunner.myhealth.domain.model.Severity

/**
 * Maps the structured [FocusItem] produced by the pure-Kotlin engine to localized copy.
 *
 * The domain layer never builds sentences: it emits a code plus named arguments, so every rule can be
 * translated (en/hi/gu) and tested without Android. This file is the only place where those codes
 * become words.
 */
internal fun FocusItem.arg(key: String): String = args[key] ?: ""

@Composable
fun FocusItem.titleText(): String {
    val name = arg("analyte").ifBlank { analyteName.orEmpty() }
    return when (code) {
        FocusCode.VALUE_HIGH -> stringResource(R.string.focus_value_high_title, name)
        FocusCode.VALUE_LOW -> stringResource(R.string.focus_value_low_title, name)
        FocusCode.VALUE_CRITICAL_HIGH -> stringResource(R.string.focus_value_critical_high_title, name)
        FocusCode.VALUE_CRITICAL_LOW -> stringResource(R.string.focus_value_critical_low_title, name)
        FocusCode.VALUE_BACK_IN_RANGE -> stringResource(R.string.focus_back_in_range_title, name)
        FocusCode.TREND_RISING -> stringResource(R.string.focus_trend_rising_title, name)
        FocusCode.TREND_FALLING -> stringResource(R.string.focus_trend_falling_title, name)
        FocusCode.RECHECK_OVERDUE -> stringResource(R.string.focus_recheck_overdue_title, name)
        FocusCode.NO_RECENT_REPORT -> stringResource(R.string.focus_no_recent_report_title)
        FocusCode.DATA_GAP -> stringResource(R.string.focus_data_gap_title)
        FocusCode.BP_ELEVATED -> stringResource(R.string.focus_bp_elevated_title)
        FocusCode.BP_LOW -> stringResource(R.string.focus_bp_low_title)
        FocusCode.WEIGHT_TRENDING_UP -> stringResource(R.string.focus_weight_up_title)
        FocusCode.WEIGHT_TRENDING_DOWN -> stringResource(R.string.focus_weight_down_title)
        FocusCode.GLUCOSE_HIGH -> stringResource(R.string.focus_glucose_high_title)
        FocusCode.HBA1C_HIGH -> stringResource(R.string.focus_hba1c_high_title)
        FocusCode.LOW_SLEEP -> stringResource(R.string.focus_low_sleep_title)
        FocusCode.LOW_STEPS -> stringResource(R.string.focus_low_steps_title)
        FocusCode.HIGH_RESTING_HEART_RATE -> stringResource(R.string.focus_high_rhr_title)
        FocusCode.LOW_SPO2 -> stringResource(R.string.focus_low_spo2_title)
        FocusCode.LOW_HRV -> stringResource(R.string.focus_low_hrv_title)
        FocusCode.NO_DEVICE_DATA -> stringResource(R.string.focus_no_device_data_title)
        FocusCode.MEDICINE_COURSE_ENDED -> stringResource(R.string.focus_medicine_ended_title)
        FocusCode.MEDICINE_DUPLICATE_MOLECULE -> stringResource(R.string.focus_medicine_duplicate_title)
        FocusCode.MEDICINE_MANY_ACTIVE -> stringResource(R.string.focus_medicine_many_title)
        FocusCode.MEDICINE_NO_END_DATE -> stringResource(R.string.focus_medicine_no_end_title, arg("name"))
        FocusCode.TREATMENT_FOLLOW_UP_DUE -> stringResource(R.string.focus_treatment_follow_up_title)
        FocusCode.MISSING_PROFILE_INFO -> stringResource(R.string.focus_missing_profile_title)
        FocusCode.NO_VITALS_LOGGED -> stringResource(R.string.focus_no_vitals_title)
        FocusCode.LOW_CONFIDENCE_EXTRACTION -> stringResource(R.string.focus_low_confidence_title)
        FocusCode.GOOD_CONSISTENCY -> stringResource(R.string.focus_good_consistency_title)
    }
}

@Composable
fun Severity.labelText(): String = when (this) {
    Severity.ATTENTION -> stringResource(R.string.insight_severity_attention)
    Severity.WATCH -> stringResource(R.string.insight_severity_watch)
    Severity.INFO -> stringResource(R.string.insight_severity_info)
}

fun Severity.tone(): PillTone = when (this) {
    Severity.ATTENTION -> PillTone.ALERT
    Severity.WATCH -> PillTone.WARN
    Severity.INFO -> PillTone.NEUTRAL
}
