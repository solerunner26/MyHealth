package com.solerunner.myhealth.design.theme

import androidx.compose.ui.graphics.Color

/**
 * My Health uses a deliberately *monochrome* palette: a single deep-pine accent family plus a
 * warm-neutral surface scale. There is exactly one hue in the product.
 *
 * Why: clinical data is dense, and competing colours make trend reading harder. A single accent
 * also keeps status communication colour-blind safe — "out of range" is expressed with icon,
 * label, fill weight and position, never with colour alone.
 */
internal object Palette {
    // ---- Accent (the one hue) -------------------------------------------------------------
    val Pine05 = Color(0xFF02110E)
    val Pine10 = Color(0xFF04231E)
    val Pine20 = Color(0xFF0B3B33)
    val Pine30 = Color(0xFF0F5C4E)
    val Pine40 = Color(0xFF14735F)
    val Pine60 = Color(0xFF3E9B83)
    val Pine70 = Color(0xFF63B79F)
    val Pine80 = Color(0xFF8ED9C9)
    val Pine85 = Color(0xFFA8E4D6)
    val Pine90 = Color(0xFFC6F0E5)
    val Pine95 = Color(0xFFE3F8F2)
    val Pine99 = Color(0xFFF3FCF9)

    // ---- Neutrals (warm porcelain → ink) ---------------------------------------------------
    val Neutral0 = Color(0xFF000000)
    val Neutral6 = Color(0xFF0C100F)
    val Neutral10 = Color(0xFF161A19)
    val Neutral12 = Color(0xFF1B1F1E)
    val Neutral17 = Color(0xFF242928)
    val Neutral20 = Color(0xFF2C3231)
    val Neutral22 = Color(0xFF313635)
    val Neutral24 = Color(0xFF363B3A)
    val Neutral30 = Color(0xFF454B4A)
    val Neutral40 = Color(0xFF5D6462)
    val Neutral50 = Color(0xFF767C7A)
    val Neutral60 = Color(0xFF909695)
    val Neutral70 = Color(0xFFACB2B0)
    val Neutral80 = Color(0xFFC8CECB)
    val Neutral87 = Color(0xFFDDE2DF)
    val Neutral90 = Color(0xFFE3E7E4)
    val Neutral92 = Color(0xFFE9EDEA)
    val Neutral94 = Color(0xFFEFF2F0)
    val Neutral96 = Color(0xFFF4F6F4)
    val Neutral98 = Color(0xFFF9FAF9)
    val Neutral99 = Color(0xFFFCFDFC)
    val Neutral100 = Color(0xFFFFFFFF)
}
