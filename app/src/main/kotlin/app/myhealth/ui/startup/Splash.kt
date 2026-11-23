package app.myhealth.ui.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.ProvideHeroColors
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.ui.common.HeartMark
import app.myhealth.ui.common.MyHealthLogo

/**
 * Cold-start welcome in the selected theme (android-design-studio: splash). Solid themes fill the screen with the
 * hero shade and a two-tone headline; Glass themes float a frosted mark over the living background. The heart beats;
 * the user enters with one button. Shown once per app start, before the PIN lock.
 */
@Composable
fun SplashScreen(onEnter: () -> Unit) {
    val c = MyHealthTheme.colors
    val reduce = MyHealthTheme.reduceMotion
    val rise = remember { Animatable(if (reduce) 1f else 0f) }
    val easing = MyHealthTheme.motion.emphasized
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); rise.animateTo(1f, tween(800, easing = easing)) }
    val bg = if (c.isGlass) Modifier else Modifier.background(Brush.radialGradient(listOf(androidx.compose.ui.graphics.lerp(c.hero, c.heroAccent, 0.22f), c.hero, androidx.compose.ui.graphics.lerp(c.hero, androidx.compose.ui.graphics.Color.Black, 0.25f)), radius = 1800f))
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().then(bg).testTag("splash"), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).widthIn(max = 520.dp)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 32.dp)) {
                    val title = stringResource(R.string.splash_title); val accent = stringResource(R.string.splash_title_accent)
                    Text(buildAnnotatedString {
                        append(title); append("\n")
                        withStyle(SpanStyle(color = MyHealthTheme.colors.accent)) { append(accent) }
                    }, style = MyHealthTheme.typography.display.copy(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                        color = MyHealthTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.splash_body), style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary)
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
                    if (c.isGlass) MyHealthLogo(132.dp, alive = true)
                    else Box(
                        Modifier.size(132.dp).shadow(24.dp, RoundedCornerShape(40.dp), spotColor = androidx.compose.ui.graphics.Color.Black)
                            .background(Brush.linearGradient(listOf(c.background, c.accentSoft)), RoundedCornerShape(40.dp)),
                        contentAlignment = Alignment.Center,
                    ) { HeartMark(80.dp, c.hero, c.heroAccent.takeIf { c.isDark } ?: c.accentBright) }
                }
                Column(
                    Modifier.graphicsLayer { alpha = rise.value; translationY = (1f - rise.value) * 48f },
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (c.isGlass) MyHealthPrimaryButton(stringResource(R.string.splash_enter), onEnter, Modifier.fillMaxWidth().height(58.dp).testTag("enterApp"), icon = MyHealthIcons.Forward)
                    else androidx.compose.material3.Button(
                        onClick = onEnter, shape = MyHealthTheme.radii.pillShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.background, contentColor = c.textPrimary),
                        modifier = Modifier.fillMaxWidth().height(58.dp).testTag("enterApp"),
                    ) {
                        Text(stringResource(R.string.splash_enter), style = MyHealthTheme.typography.subtitle, color = c.textPrimary)
                        Spacer(Modifier.size(8.dp))
                        androidx.compose.material3.Icon(MyHealthIcons.Forward, null, tint = c.textPrimary)
                    }
                    Text(stringResource(R.string.splash_footer), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (c.isGlass) content() else ProvideHeroColors(content)
}

