package com.solerunner.myhealth.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.HealthCard
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
        HealthCard {
            Text(
                stringResource(R.string.auth_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(spacing.md))
            Text(
                stringResource(R.string.auth_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_get_started))
        }
        if (googleConfigured) {
            Spacer(Modifier.height(spacing.sm))
            OutlinedButton(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_google_sign_in))
            }
        }
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            Text(error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
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
        Text(stringResource(R.string.auth_name_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(spacing.sm))
        Text(
            stringResource(R.string.auth_name_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xl))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_name_label)) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = MaterialTheme.shapes.medium,
        )
        if (error != null) {
            Spacer(Modifier.height(spacing.xs))
            Text(error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(spacing.xl))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_continue))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}
