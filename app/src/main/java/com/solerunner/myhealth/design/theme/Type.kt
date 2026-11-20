package com.solerunner.myhealth.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val defaultLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * A tighter, slightly more editorial type scale than stock Material 3: display/headline sizes are
 * used for single hero numbers (health score, latest vital), while body sizes stay comfortable for
 * long tables of clinical values.
 */
private val base = Typography()

val MyHealthTypography: Typography = Typography(
    displayLarge = base.displayLarge.copy(
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1).sp,
        lineHeightStyle = defaultLineHeightStyle,
    ),
    displayMedium = base.displayMedium.copy(
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = base.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

/** Numeric values use a monospaced family so digits line up in tables and never "jitter". */
val NumericTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.2).sp,
)
