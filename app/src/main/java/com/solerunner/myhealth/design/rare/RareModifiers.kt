package com.solerunner.myhealth.design.rare

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion and surface helpers shared by the Rare components.
 *
 * Press feedback is a 0.97 scale on a short spring, the physical feel of the Rare UI controls. The
 * caller passes the same [MutableInteractionSource] to `clickable` so the scale follows real presses.
 */
fun Modifier.pressScale(
    source: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    if (!enabled) return@composed this
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press-scale",
    )
    scale(scale)
}

/** 1dp hairline that reads as a drawn line, not a shadow. */
fun Modifier.hairline(
    color: Color,
    shape: Shape = RoundedCornerShape(0.dp),
    width: Dp = 1.dp,
): Modifier = border(width, color, shape)

/** Spring for indicators that travel between positions (nav dot, segmented pill). */
fun <T> indicatorSpring(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 420f)

@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
