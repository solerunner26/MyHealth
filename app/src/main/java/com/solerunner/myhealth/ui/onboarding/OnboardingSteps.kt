package com.solerunner.myhealth.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareBanner
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareButtonStyle
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens

@Composable
internal fun WelcomeStep(
    googleConfigured: Boolean,
    error: String?,
    onStart: () -> Unit,
    onGoogle: () -> Unit,
) {
    val spacing = MyHealthTokens.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(spacing.lg))
        RareCard {
            Text(stringResource(R.string.auth_welcome_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(spacing.sm))
            Text(
                stringResource(R.string.auth_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        RareButton(text = stringResource(R.string.auth_get_started), onClick = onStart, modifier = Modifier.fillMaxWidth())
        if (googleConfigured) {
            Spacer(Modifier.height(spacing.sm))
            RareButton(
                text = stringResource(R.string.auth_google_sign_in),
                onClick = onGoogle,
                modifier = Modifier.fillMaxWidth(),
                style = RareButtonStyle.SECONDARY,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            RareBanner(text = error)
        }
    }
}

@Composable
internal fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    error: String?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MyHealthTokens.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.auth_name_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.sm))
        Text(
            stringResource(R.string.auth_name_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xl))
        RareTextField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.auth_name_label),
            isError = error != null,
            supportingText = error,
        )
        Spacer(Modifier.height(spacing.xl))
        RareButton(text = stringResource(R.string.common_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(spacing.sm))
        RareButton(
            text = stringResource(R.string.common_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            style = RareButtonStyle.GHOST,
        )
    }
}
