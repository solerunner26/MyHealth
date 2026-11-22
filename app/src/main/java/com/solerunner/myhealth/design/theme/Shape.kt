package com.solerunner.myhealth.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Small, precise radii in the Rare UI spirit: controls sit at 10dp, cards at 14dp, sheets at 20dp.
 * Pills are always `CircleShape` at the call site.
 */
val MyHealthShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
