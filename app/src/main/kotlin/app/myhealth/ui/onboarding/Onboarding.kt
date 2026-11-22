package app.myhealth.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.theme.MyHealthTheme

/** First screen on a clean install: what the app is, what it is not, and one action. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val c = MyHealthTheme.colors; val sp = MyHealthTheme.spacing
    Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("welcome"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(sp.xl),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(MyHealthIcons.Heart, null, tint = c.onAccent, modifier = Modifier.size(36.dp))
            }
            Text(stringResource(R.string.onb_welcome_title), style = MyHealthTheme.typography.display, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.onb_welcome_body), style = MyHealthTheme.typography.body, color = c.textSecondary)
            Point(MyHealthIcons.Offline, stringResource(R.string.onb_point_offline))
            Point(MyHealthIcons.Lock, stringResource(R.string.onb_point_encrypted))
            Point(MyHealthIcons.Check, stringResource(R.string.onb_point_review))
            Point(MyHealthIcons.Help, stringResource(R.string.onb_point_not_diagnosis))
            Spacer(Modifier.height(sp.sm))
            MyHealthPrimaryButton(stringResource(R.string.onb_start), onStart, Modifier.fillMaxWidth().testTag("onbStart"))
        }
    }
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    val c = MyHealthTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(MyHealthTheme.spacing.md))
        Text(text, style = MyHealthTheme.typography.subtitle, color = c.textPrimary)
    }
}
