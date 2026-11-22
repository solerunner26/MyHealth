package com.solerunner.myhealth.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colour roles follow the Rare UI surface model: porcelain/ink neutrals, hairline outlines, one accent.
 * The accent stays deep pine because the product's single-hue rule outranks any imported palette.
 */
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Pine30,
    onPrimary = Palette.White,
    primaryContainer = Palette.Pine95,
    onPrimaryContainer = Palette.Pine10,
    inversePrimary = Palette.Pine80,
    secondary = Palette.Ink32,
    onSecondary = Palette.White,
    secondaryContainer = Palette.Ink96,
    onSecondaryContainer = Palette.Ink14,
    tertiary = Palette.Pine40,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.Pine99,
    onTertiaryContainer = Palette.Pine10,
    background = Palette.Ink99,
    onBackground = Palette.Ink14,
    surface = Palette.White,
    onSurface = Palette.Ink14,
    surfaceVariant = Palette.Ink94,
    onSurfaceVariant = Palette.Ink52,
    surfaceTint = Palette.Pine30,
    surfaceBright = Palette.White,
    surfaceDim = Palette.Ink92,
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Palette.Ink98,
    surfaceContainer = Palette.Ink96,
    surfaceContainerHigh = Palette.Ink94,
    surfaceContainerHighest = Palette.Ink92,
    inverseSurface = Palette.Ink14,
    inverseOnSurface = Palette.Ink98,
    outline = Palette.Ink80,
    outlineVariant = Palette.Ink92,
    // Single hue: destructive styling uses pine ink, never a second colour.
    error = Palette.Pine20,
    onError = Palette.White,
    errorContainer = Palette.Ink94,
    onErrorContainer = Palette.Pine10,
    scrim = Palette.Ink0,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.Pine80,
    onPrimary = Palette.Pine05,
    primaryContainer = Palette.Pine20,
    onPrimaryContainer = Palette.Pine90,
    inversePrimary = Palette.Pine30,
    secondary = Palette.Ink88,
    onSecondary = Palette.Ink10,
    secondaryContainer = Palette.Ink22,
    onSecondaryContainer = Palette.Ink92,
    tertiary = Palette.Pine70,
    onTertiary = Palette.Pine05,
    tertiaryContainer = Palette.Pine20,
    onTertiaryContainer = Palette.Pine90,
    background = Palette.Ink7,
    onBackground = Palette.Ink92,
    surface = Palette.Ink10,
    onSurface = Palette.Ink92,
    surfaceVariant = Palette.Ink18,
    onSurfaceVariant = Palette.Ink72,
    surfaceTint = Palette.Pine80,
    surfaceBright = Palette.Ink26,
    surfaceDim = Palette.Ink7,
    surfaceContainerLowest = Palette.Ink3,
    surfaceContainerLow = Palette.Ink14,
    surfaceContainer = Palette.Ink14,
    surfaceContainerHigh = Palette.Ink18,
    surfaceContainerHighest = Palette.Ink22,
    inverseSurface = Palette.Ink92,
    inverseOnSurface = Palette.Ink10,
    outline = Palette.Ink40,
    outlineVariant = Palette.Ink32,
    error = Palette.Pine85,
    onError = Palette.Pine05,
    errorContainer = Palette.Ink22,
    onErrorContainer = Palette.Pine90,
    scrim = Palette.Ink0,
)

/** Spacing scale. Every gap comes from here so the rhythm stays identical app-wide. */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val screenPadding: Dp = 20.dp,
    val cardPadding: Dp = 16.dp,
    val rowHeight: Dp = 56.dp,
    val hairline: Dp = 1.dp,
)

/** Geometry constants that keep controls identical in size and stroke across the app. */
@Immutable
data class RareDims(
    val border: Dp = 1.dp,
    val focusBorder: Dp = 1.5.dp,
    val controlHeight: Dp = 48.dp,
    val iconButton: Dp = 40.dp,
    val touchTarget: Dp = 48.dp,
    val sheetHandleWidth: Dp = 36.dp,
    val dot: Dp = 6.dp,
)

/** Shared motion language: short, eased moves; springs only for playful indicators. */
@Immutable
data class Motion(
    val fast: Int = 120,
    val normal: Int = 200,
    val slow: Int = 320,
    val out: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    val inOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f),
)

/** Chart palette: tints of the single accent plus neutral, never an independent hue. */
@Immutable
data class ChartColors(
    val line: Color,
    val lineSoft: Color,
    val point: Color,
    val pointSelected: Color,
    val referenceBand: Color,
    val grid: Color,
    val axisLabel: Color,
    val areaTop: Color,
    val areaBottom: Color,
)

fun lightChartColors(): ChartColors = ChartColors(
    line = Palette.Pine30,
    lineSoft = Palette.Pine60,
    point = Palette.Pine30,
    pointSelected = Palette.Pine10,
    referenceBand = Palette.Pine30.copy(alpha = 0.10f),
    grid = Palette.Ink92,
    axisLabel = Palette.Ink52,
    areaTop = Palette.Pine80.copy(alpha = 0.28f),
    areaBottom = Palette.Pine80.copy(alpha = 0.02f),
)

fun darkChartColors(): ChartColors = ChartColors(
    line = Palette.Pine80,
    lineSoft = Palette.Pine70,
    point = Palette.Pine80,
    pointSelected = Palette.Pine95,
    referenceBand = Palette.Pine80.copy(alpha = 0.14f),
    grid = Palette.Ink22,
    axisLabel = Palette.Ink64,
    areaTop = Palette.Pine70.copy(alpha = 0.24f),
    areaBottom = Palette.Pine70.copy(alpha = 0.02f),
)

private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalChartColors = staticCompositionLocalOf { lightChartColors() }
private val LocalDims = staticCompositionLocalOf { RareDims() }
private val LocalMotion = staticCompositionLocalOf { Motion() }

/** Product-wide accessor for the extended design tokens. */
object MyHealthTokens {
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val chart: ChartColors
        @Composable @ReadOnlyComposable get() = LocalChartColors.current

    val dims: RareDims
        @Composable @ReadOnlyComposable get() = LocalDims.current

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}

/**
 * The single theme entry point. `darkTheme` comes from the user's setting, so SYSTEM/LIGHT/DARK is
 * resolved in one place instead of being re-derived in every screen.
 */
@Composable
fun MyHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalChartColors provides if (darkTheme) darkChartColors() else lightChartColors(),
        LocalDims provides RareDims(),
        LocalMotion provides Motion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MyHealthTypography,
            shapes = MyHealthShapes,
            content = content,
        )
    }
}
