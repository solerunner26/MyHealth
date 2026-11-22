package app.myhealth.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.myhealth.R
import app.myhealth.core.designsystem.components.rememberBreath
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.core.designsystem.tokens.MyHealthThemeId

object Branding {
    @DrawableRes fun background(t: MyHealthThemeId) = when (t) {
        MyHealthThemeId.Aurora -> R.drawable.logo_aurora_background; MyHealthThemeId.Crystal -> R.drawable.logo_crystal_background
        MyHealthThemeId.Botanical -> R.drawable.logo_botanical_background; MyHealthThemeId.Precision -> R.drawable.logo_precision_background
        MyHealthThemeId.Pulse -> R.drawable.logo_pulse_background
    }
    @DrawableRes fun foreground(t: MyHealthThemeId) = when (t) {
        MyHealthThemeId.Aurora -> R.drawable.logo_aurora_foreground; MyHealthThemeId.Crystal -> R.drawable.logo_crystal_foreground
        MyHealthThemeId.Botanical -> R.drawable.logo_botanical_foreground; MyHealthThemeId.Precision -> R.drawable.logo_precision_foreground
        MyHealthThemeId.Pulse -> R.drawable.logo_pulse_foreground
    }
    @StringRes fun name(t: MyHealthThemeId) = when (t) {
        MyHealthThemeId.Aurora -> R.string.theme_aurora; MyHealthThemeId.Crystal -> R.string.theme_crystal; MyHealthThemeId.Botanical -> R.string.theme_botanical
        MyHealthThemeId.Precision -> R.string.theme_precision; MyHealthThemeId.Pulse -> R.string.theme_pulse
    }
    @StringRes fun description(t: MyHealthThemeId) = when (t) {
        MyHealthThemeId.Aurora -> R.string.theme_aurora_desc; MyHealthThemeId.Crystal -> R.string.theme_crystal_desc; MyHealthThemeId.Botanical -> R.string.theme_botanical_desc
        MyHealthThemeId.Precision -> R.string.theme_precision_desc; MyHealthThemeId.Pulse -> R.string.theme_pulse_desc
    }
}

/** The theme's logo (same art as the launcher icon), with a slow breathing glow when [alive]. */
@Composable
fun MyHealthLogo(size: Dp, modifier: Modifier = Modifier, theme: MyHealthThemeId = MyHealthTheme.colors.theme, alive: Boolean = false) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.28f)
    val b = if (alive) rememberBreath() else 0f
    Box(modifier.size(size).shadow(size * (0.12f + 0.06f * b), shape, ambientColor = MyHealthTheme.colors.glow, spotColor = MyHealthTheme.colors.glow).clip(shape)) {
        Image(painterResource(Branding.background(theme)), stringResource(R.string.logo_cd), Modifier.fillMaxSize().scale(1.5f))
        Image(painterResource(Branding.foreground(theme)), null, Modifier.fillMaxSize().scale(1.5f + 0.03f * b))
    }
}

