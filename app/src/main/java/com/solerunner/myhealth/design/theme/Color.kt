package com.solerunner.myhealth.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette: one hue, many weights.
 *
 * The geometry and surface treatment follow the Rare UI design language (near-black and porcelain
 * neutrals, hairline borders, small radii, high contrast type). The product rule still wins: there is
 * exactly one hue in the app, a deep pine accent. Severity is carried by icon, label and fill weight,
 * never by a second colour, so the palette stays colour-blind safe.
 */
internal object Palette {
    // Accent: deep pine, 05 (darkest) to 99 (lightest).
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

    // Warm neutral scale. Slightly green-warm so it sits with the accent without a second hue.
    val Ink0 = Color(0xFF000000)
    val Ink3 = Color(0xFF050606)
    val Ink7 = Color(0xFF0A0B0B)
    val Ink10 = Color(0xFF101211)
    val Ink14 = Color(0xFF161817)
    val Ink18 = Color(0xFF1C1E1D)
    val Ink22 = Color(0xFF222524)
    val Ink26 = Color(0xFF2A2D2C)
    val Ink32 = Color(0xFF343836)
    val Ink40 = Color(0xFF454A47)
    val Ink52 = Color(0xFF5E635F)
    val Ink64 = Color(0xFF7C817D)
    val Ink72 = Color(0xFF949994)
    val Ink80 = Color(0xFFC4C8C3)
    val Ink88 = Color(0xFFDDE0DB)
    val Ink92 = Color(0xFFE9EBE7)
    val Ink94 = Color(0xFFEFF0ED)
    val Ink96 = Color(0xFFF4F5F2)
    val Ink98 = Color(0xFFFAFAF8)
    val Ink99 = Color(0xFFFDFDFB)
    val White = Color(0xFFFFFFFF)
}
