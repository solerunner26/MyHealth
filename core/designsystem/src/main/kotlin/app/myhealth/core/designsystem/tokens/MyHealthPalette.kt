package app.myhealth.core.designsystem.tokens

/**
 * Raw palette. Exactly ONE chromatic hue (teal, HSL hue 174°) plus pure greys.
 * Values are ARGB longs so the single-hue and contrast rules can be verified by plain JVM tests.
 * Never add a second hue: status is expressed by icon, label, fill weight and position.
 */
object MyHealthPalette {
    // Accent scale, HSL(174°, 62%, L)
    const val Accent10 = 0xFF0A2926
    const val Accent15 = 0xFF0F3E39
    const val Accent20 = 0xFF13534C
    const val Accent25 = 0xFF18675F
    const val Accent30 = 0xFF1D7C72
    const val Accent40 = 0xFF27A599
    const val Accent62 = 0xFF62DACE
    const val Accent75 = 0xFF98E7DF
    const val Accent88 = 0xFFCDF3F0
    const val Accent92 = 0xFFDEF7F5
    const val Accent97 = 0xFFF3FCFB

    // Neutral greys (R == G == B)
    const val White = 0xFFFFFFFF
    const val Grey98 = 0xFFFAFAFA
    const val Grey95 = 0xFFF2F2F2
    const val Grey90 = 0xFFE6E6E6
    const val Grey84 = 0xFFD6D6D6
    const val Grey62 = 0xFF9E9E9E
    const val Grey55 = 0xFF8C8C8C
    const val Grey42 = 0xFF6B6B6B
    const val Grey30 = 0xFF4D4D4D
    const val Grey24 = 0xFF3D3D3D
    const val Grey20 = 0xFF333333
    const val Grey15 = 0xFF262626
    const val Grey12 = 0xFF1F1F1F
    const val Grey09 = 0xFF171717
    const val Grey06 = 0xFF0F0F0F
    const val Grey76 = 0xFFC2C2C2
    const val Grey95b = 0xFFF2F2F2
    const val Black08 = 0xFF141414

    val accentScale = listOf(Accent10, Accent15, Accent20, Accent25, Accent30, Accent40, Accent62, Accent75, Accent88, Accent92, Accent97)
    val neutrals = listOf(White, Grey98, Grey95, Grey90, Grey84, Grey76, Grey62, Grey55, Grey42, Grey30, Grey24, Grey20, Grey15, Grey12, Grey09, Grey06, Black08)
}
