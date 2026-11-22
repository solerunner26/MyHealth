package app.myhealth.core.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MyHealthSpacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 12.dp, val lg: Dp = 16.dp,
    val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val xxxl: Dp = 48.dp,
    /** Horizontal gutter for screen content. */
    val gutter: Dp = 16.dp,
    /** Minimum touch target (accessibility). */
    val touchTarget: Dp = 48.dp,
    /** Max readable content width on wide screens. */
    val contentMaxWidth: Dp = 720.dp,
)

@Immutable
data class MyHealthRadii(
    val sm: Dp = 8.dp, val md: Dp = 12.dp, val lg: Dp = 16.dp, val xl: Dp = 24.dp, val pill: Dp = 999.dp,
) {
    val smShape get() = RoundedCornerShape(sm)
    val mdShape get() = RoundedCornerShape(md)
    val lgShape get() = RoundedCornerShape(lg)
    val xlShape get() = RoundedCornerShape(xl)
    val pillShape get() = RoundedCornerShape(pill)
}

@Immutable
data class MyHealthMotion(
    val shortMillis: Int = 120,
    val mediumMillis: Int = 240,
    val longMillis: Int = 400,
    val shimmerMillis: Int = 1200,
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val emphasized: Easing = CubicBezierEasing(0.3f, 0f, 0f, 1f),
)

@Immutable
data class MyHealthElevation(
    val none: Dp = 0.dp, val low: Dp = 1.dp, val mid: Dp = 3.dp, val high: Dp = 6.dp,
)

/** Stroke weights: one of the non-colour channels used to convey status. */
@Immutable
data class MyHealthStroke(
    val hairline: Dp = 1.dp, val regular: Dp = 1.5.dp, val strong: Dp = 2.5.dp,
)
