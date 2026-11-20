package com.solerunner.myhealth.design.theme

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

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Pine30,
    onPrimary = Palette.Neutral100,
    primaryContainer = Palette.Pine90,
    onPrimaryContainer = Palette.Pine10,
    inversePrimary = Palette.Pine80,
    secondary = Palette.Neutral30,
    onSecondary = Palette.Neutral100,
    secondaryContainer = Palette.Neutral92,
    onSecondaryContainer = Palette.Neutral10,
    tertiary = Palette.Pine40,
    onTertiary = Palette.Neutral100,
    tertiaryContainer = Palette.Pine95,
    onTertiaryContainer = Palette.Pine10,
    background = Palette.Neutral98,
    onBackground = Palette.Neutral10,
    surface = Palette.Neutral98,
    onSurface = Palette.Neutral10,
    surfaceVariant = Palette.Neutral92,
    onSurfaceVariant = Palette.Neutral30,
    surfaceTint = Palette.Pine30,
    surfaceBright = Palette.Neutral99,
    surfaceDim = Palette.Neutral90,
    surfaceContainerLowest = Palette.Neutral100,
    surfaceContainerLow = Palette.Neutral96,
    surfaceContainer = Palette.Neutral94,
    surfaceContainerHigh = Palette.Neutral92,
    surfaceContainerHighest = Palette.Neutral90,
    inverseSurface = Palette.Neutral20,
    inverseOnSurface = Palette.Neutral96,
    outline = Palette.Neutral70,
    outlineVariant = Palette.Neutral87,
    // Single-hue product: the "error" slot stays inside the palette (deep pine ink) so that
    // destructive/critical styling never introduces a second hue. Severity is conveyed by
    // iconography, weight and wording instead of colour.
    error = Palette.Pine20,
    onError = Palette.Neutral100,
    errorContainer = Palette.Neutral90,
    onErrorContainer = Palette.Pine05,
    scrim = Palette.Neutral0,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.Pine80,
    onPrimary = Palette.Pine10,
    primaryContainer = Palette.Pine20,
    onPrimaryContainer = Palette.Pine90,
    inversePrimary = Palette.Pine30,
    secondary = Palette.Neutral80,
    onSecondary = Palette.Neutral12,
    secondaryContainer = Palette.Neutral24,
    onSecondaryContainer = Palette.Neutral90,
    tertiary = Palette.Pine70,
    onTertiary = Palette.Pine05,
    tertiaryContainer = Palette.Pine20,
    onTertiaryContainer = Palette.Pine90,
    background = Palette.Neutral6,
    onBackground = Palette.Neutral92,
    surface = Palette.Neutral6,
    onSurface = Palette.Neutral92,
    surfaceVariant = Palette.Neutral22,
    onSurfaceVariant = Palette.Neutral80,
    surfaceTint = Palette.Pine80,
    surfaceBright = Palette.Neutral24,
    surfaceDim = Palette.Neutral6,
    surfaceContainerLowest = Palette.Neutral0,
    surfaceContainerLow = Palette.Neutral10,
    surfaceContainer = Palette.Neutral12,
    surfaceContainerHigh = Palette.Neutral17,
    surfaceContainerHighest = Palette.Neutral22,
    inverseSurface = Palette.Neutral90,
/** Spacing scale — every gap in the product comes from here, so rhythm stays identical app-wide. */
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
    val cardPadding: Dp = 18.dp,
    val rowHeight: Dp = 52.dp,
    val hairline: Dp = 1.dp,
)

/** Chart palette: tints of the single accent plus neutral, never independent hues. */
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

@Immutable
data class MyHealthExtras(
    val spacing: Spacing = Spacing(),
    val chart: ChartColors = lightChartColors(),
)

fun lightChartColors(): ChartColors = ChartColors(
    line = Palette.Pine30,
    lineSoft = Palette.Pine60,
    point = Palette.Pine30,
    pointSelected = Palette.Pine10,
    referenceBand = Palette.Pine90,
    grid = Palette.Neutral90,
    axisLabel = Palette.Neutral40,
    areaTop = Palette.Pine80.copy(alpha = 0.40f),
    areaBottom = Palette.Pine80.copy(alpha = 0.02f),
)

fun darkChartColors(): ChartColors = ChartColors(
    line = Palette.Pine80,
    lineSoft = Palette.Pine70,
    point = Palette.Pine80,
    pointSelected = Palette.Pine95,
    referenceBand = Palette.Pine20,
    grid = Palette.Neutral24,
    axisLabel = Palette.Neutral70,
    areaTop = Palette.Pine70.copy(alpha = 0.32f),
    areaBottom = Palette.Pine70.copy(alpha = 0.02f),
)

private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalChartColors = staticCompositionLocalOf { lightChartColors() }

/** Product-wide accessor for the extended design tokens. */
object MyHealthTokens {
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val chart: ChartColors
        @Composable @ReadOnlyComposable get() = LocalChartColors.current
}

@Composable
fun MyHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalChartColors provides if (darkTheme) darkChartColors() else lightChartColors(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MyHealthTypography,
            shapes = MyHealthShapes,
            content = content,
        )
    }
}

    inverseOnSurface = Palette.Neutral12,
    outline = Palette.Neutral60,
    outlineVariant = Palette.Neutral30,
    error = Palette.Pine85,
    onError = Palette.Pine05,
    errorContainer = Palette.Neutral24,
    onErrorContainer = Palette.Pine90,
    scrim = Palette.Neutral0,
)
