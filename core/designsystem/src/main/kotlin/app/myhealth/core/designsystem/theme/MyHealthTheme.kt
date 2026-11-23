package app.myhealth.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import app.myhealth.core.designsystem.tokens.MyHealthColorRoles
import app.myhealth.core.designsystem.tokens.MyHealthColors
import app.myhealth.core.designsystem.tokens.MyHealthThemeId
import app.myhealth.core.designsystem.tokens.MyHealthElevation
import app.myhealth.core.designsystem.tokens.MyHealthMotion
import app.myhealth.core.designsystem.tokens.MyHealthRadii
import app.myhealth.core.designsystem.tokens.MyHealthSpacing
import app.myhealth.core.designsystem.tokens.MyHealthStroke
import app.myhealth.core.designsystem.tokens.MyHealthTypography
import app.myhealth.core.designsystem.tokens.toColors

/** Auto = light by day, dark from sunset to sunrise (the app passes `night`). */
enum class ThemeMode { Auto, System, Light, Dark }

val LocalMyHealthColors = staticCompositionLocalOf { MyHealthColorRoles.roles(MyHealthThemeId.Default, false).toColors(false, MyHealthThemeId.Default) }

/** True when the user turned animations off in system settings: decorative motion stops. */
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalMyHealthTypography = staticCompositionLocalOf { MyHealthTypography() }
val LocalMyHealthSpacing = staticCompositionLocalOf { MyHealthSpacing() }
val LocalMyHealthRadii = staticCompositionLocalOf { MyHealthRadii() }
val LocalMyHealthMotion = staticCompositionLocalOf { MyHealthMotion() }
val LocalMyHealthElevation = staticCompositionLocalOf { MyHealthElevation() }
val LocalMyHealthStroke = staticCompositionLocalOf { MyHealthStroke() }

/** Token accessor: `MyHealthTheme.colors.accent`, `MyHealthTheme.spacing.lg`, ... */
object MyHealthTheme {
    val colors: MyHealthColors @Composable @ReadOnlyComposable get() = LocalMyHealthColors.current
    val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
    val typography: MyHealthTypography @Composable @ReadOnlyComposable get() = LocalMyHealthTypography.current
    val spacing: MyHealthSpacing @Composable @ReadOnlyComposable get() = LocalMyHealthSpacing.current
    val radii: MyHealthRadii @Composable @ReadOnlyComposable get() = LocalMyHealthRadii.current
    val motion: MyHealthMotion @Composable @ReadOnlyComposable get() = LocalMyHealthMotion.current
    val elevation: MyHealthElevation @Composable @ReadOnlyComposable get() = LocalMyHealthElevation.current
    val stroke: MyHealthStroke @Composable @ReadOnlyComposable get() = LocalMyHealthStroke.current
}

@Composable
fun MyHealthTheme(
    mode: ThemeMode = ThemeMode.System,
    theme: MyHealthThemeId = MyHealthThemeId.Default,
    night: Boolean = false,
    /** User setting "Glass effects": liquid-glass surfaces. */
    glass: Boolean = true,
    /** User setting "Animations": off stops every decorative animation (as does the system's animator scale 0). */
    animations: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Auto -> night
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = androidx.compose.runtime.remember(theme, dark, glass) { MyHealthColorRoles.roles(theme, dark).toColors(dark, theme).copy(isGlass = glass) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val systemOff = androidx.compose.runtime.remember(ctx) {
        runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val reduceMotion = systemOff || !animations
    val typography = MyHealthTypography()
    CompositionLocalProvider(
        LocalMyHealthColors provides colors,
        LocalMyHealthTypography provides typography,
        LocalReduceMotion provides reduceMotion,
    ) {
        // Material components used internally (text fields, dialogs) are fed ONLY our tokens,
        // so no default Material hue (including the red error role) can leak into the UI.
        MaterialTheme(colorScheme = colors.toMaterial(), typography = typography.toMaterial(), content = content)
    }
}

internal fun MyHealthColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent, primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
        inversePrimary = accentContainer,
        secondary = accent, onSecondary = onAccent, secondaryContainer = accentContainer, onSecondaryContainer = onAccentContainer,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = accentContainer, onTertiaryContainer = onAccentContainer,
        background = background, onBackground = textPrimary, surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceSunken, onSurfaceVariant = textSecondary, surfaceTint = accent,
        inverseSurface = textPrimary, inverseOnSurface = surface,
        // Errors are neutral-strong + icon + text, never a second hue.
        error = textPrimary, onError = surface, errorContainer = surfaceSunken, onErrorContainer = textPrimary,
        outline = outlineStrong, outlineVariant = outline, scrim = scrim,
        surfaceBright = surfaceRaised, surfaceDim = surfaceSunken, surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceSunken,
        surfaceContainerLow = surface, surfaceContainerLowest = surface,
    )
}

internal fun MyHealthTypography.toMaterial() = Typography(
    displayLarge = display, displayMedium = display, displaySmall = headline,
    headlineLarge = headline, headlineMedium = headline, headlineSmall = title,
    titleLarge = title, titleMedium = subtitle, titleSmall = label,
    bodyLarge = body, bodyMedium = bodySmall, bodySmall = caption,
    labelLarge = label, labelMedium = caption, labelSmall = caption,
)
