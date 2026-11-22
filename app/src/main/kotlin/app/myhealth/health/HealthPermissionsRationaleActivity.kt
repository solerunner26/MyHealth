package app.myhealth.health

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.myhealth.R
import app.myhealth.core.LocaleSupport
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.theme.MyHealthTheme

/** Shown by Health Connect when the user asks why MyHealth wants access (required privacy-policy screen). */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleSupport.localized(newBase))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyHealthTheme {
                Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(MyHealthTheme.spacing.xl)) {
                    Text(stringResource(R.string.hc_rationale_title), style = MyHealthTheme.typography.headline, color = MyHealthTheme.colors.textPrimary)
                    Text(stringResource(R.string.hc_intro), style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(vertical = MyHealthTheme.spacing.lg))
                    Text(stringResource(R.string.about_body), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                    MyHealthPrimaryButton(stringResource(R.string.action_close), { finish() }, Modifier.padding(top = MyHealthTheme.spacing.xl))
                }
            }
        }
    }
}
