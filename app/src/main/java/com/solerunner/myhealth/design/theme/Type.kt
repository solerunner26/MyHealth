package com.solerunner.myhealth.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.solerunner.myhealth.R

/** Inter, bundled. No runtime download, which keeps the app fully offline. */
val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

private val tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val base = Typography()

/**
 * Rare-style type: tight display tracking for hero numbers, quiet body text, small uppercase labels,
 * and a mono face for values so digits line up in tables and never jitter while animating.
 */
val MyHealthTypography: Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 54.sp, lineHeight = 58.sp, letterSpacing = (-1.6).sp, lineHeightStyle = tight),
    displayMedium = base.displayMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1.2).sp),
    displaySmall = base.displaySmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
    titleSmall = base.titleSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = InterFamily, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontFamily = InterFamily, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = base.labelMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
    labelSmall = base.labelSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

/** Values, scores and doses use tabular digits through the platform mono face. */
val NumericTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.2).sp,
)

/** Small caps used by section labels, in the Rare UI manner. */
val RareLabelStyle: TextStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.9.sp,
)
