package app.myhealth.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** System font family: renders Devanagari and Gujarati glyphs without bundling fonts. */
@Immutable
data class MyHealthTypography(
    val display: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    val headline: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    val title: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    val subtitle: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    val bodySmall: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    val label: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    val metric: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum"),
    val metricSmall: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
)
