package com.solerunner.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.domain.model.FocusCode
import com.solerunner.myhealth.domain.model.FocusItem

/** The explanatory sentence of a focus card. Arguments come from [FocusItem.args], never formatted here. */
@Composable
fun FocusItem.bodyText(): String {
    val name = arg("analyte").ifBlank { analyteName.orEmpty() }
    return when (code) {
        FocusCode.VALUE_HIGH -> stringResource(R.string.focus_value_high_body, name, arg("value"), arg("unit"), arg("range"))
        FocusCode.VALUE_LOW -> stringResource(R.string.focus_value_low_body, name, arg("value"), arg("unit"), arg("range"))
        FocusCode.VALUE_CRITICAL_HIGH ->
            stringResource(R.string.focus_value_critical_high_body, name, arg("value"), arg("unit"), arg("range"))
        FocusCode.VALUE_CRITICAL_LOW ->
            stringResource(R.string.focus_value_critical_low_body, name, arg("value"), arg("unit"), arg("range"))
        FocusCode.VALUE_BACK_IN_RANGE -> stringResource(R.string.focus_back_in_range_body, name, arg("value"), arg("unit"))
        FocusCode.TREND_RISING ->
            stringResource(R.string.focus_trend_rising_body, name, arg("from"), arg("to"), arg("months"), arg("percent"))
        FocusCode.TREND_FALLING ->
            stringResource(R.string.focus_trend_falling_body, name, arg("from"), arg("to"), arg("months"), arg("percent"))
        FocusCode.RECHECK_OVERDUE -> stringResource(R.string.focus_recheck_overdue_body, name, arg("months"), arg("due"))
        FocusCode.NO_RECENT_REPORT -> stringResource(R.string.focus_no_recent_report_body, arg("months"))
        FocusCode.DATA_GAP -> stringResource(R.string.focus_data_gap_body, arg("months"))
        FocusCode.BP_ELEVATED -> stringResource(R.string.focus_bp_elevated_body, arg("systolic"), arg("diastolic"), arg("readings"))
        FocusCode.BP_LOW -> stringResource(R.string.focus_bp_low_body, arg("systolic"), arg("diastolic"), arg("readings"))
        FocusCode.WEIGHT_TRENDING_UP -> stringResource(R.string.focus_weight_up_body, arg("from"), arg("to"), arg("perMonth"))
        FocusCode.WEIGHT_TRENDING_DOWN -> stringResource(R.string.focus_weight_down_body, arg("from"), arg("to"), arg("perMonth"))
        FocusCode.GLUCOSE_HIGH -> stringResource(R.string.focus_glucose_high_body, arg("value"), arg("unit"))
        FocusCode.HBA1C_HIGH -> stringResource(R.string.focus_hba1c_high_body, arg("value"), arg("unit"), arg("target"))
        FocusCode.LOW_SLEEP -> stringResource(R.string.focus_low_sleep_body, arg("hours"), arg("nights"), arg("target"))
        FocusCode.LOW_STEPS -> stringResource(R.string.focus_low_steps_body, arg("steps"), arg("target"))
        FocusCode.HIGH_RESTING_HEART_RATE -> stringResource(R.string.focus_high_rhr_body, arg("value"), arg("range"))
        FocusCode.LOW_SPO2 -> stringResource(R.string.focus_low_spo2_body, arg("value"), arg("range"))
        FocusCode.LOW_HRV -> stringResource(R.string.focus_low_hrv_body, arg("value"))
        FocusCode.NO_DEVICE_DATA -> stringResource(R.string.focus_no_device_data_body)
        FocusCode.MEDICINE_COURSE_ENDED -> stringResource(R.string.focus_medicine_ended_body, arg("name"), arg("date"), arg("days"))
        FocusCode.MEDICINE_DUPLICATE_MOLECULE ->
            stringResource(R.string.focus_medicine_duplicate_body, arg("molecule"), arg("count"))
        FocusCode.MEDICINE_MANY_ACTIVE -> stringResource(R.string.focus_medicine_many_body, arg("count"))
        FocusCode.MEDICINE_NO_END_DATE -> stringResource(R.string.focus_medicine_no_end_body)
        FocusCode.TREATMENT_FOLLOW_UP_DUE -> stringResource(R.string.focus_treatment_follow_up_body, arg("name"), arg("date"))
        FocusCode.MISSING_PROFILE_INFO -> stringResource(R.string.focus_missing_profile_body)
        FocusCode.NO_VITALS_LOGGED -> stringResource(R.string.focus_no_vitals_body)
        FocusCode.LOW_CONFIDENCE_EXTRACTION -> stringResource(R.string.focus_low_confidence_body, arg("percent"))
        FocusCode.GOOD_CONSISTENCY -> stringResource(R.string.focus_good_consistency_body, arg("count"))
    }
}
