package com.solerunner.myhealth.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareBanner
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareSheet
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * Re-authentication sheet. Security changes (new PIN, disabling the app lock) are only allowed after the
 * current PIN is verified, so an unlocked-but-unattended phone cannot be taken over.
 */
@Composable
fun VerifyPinSheet(
    title: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    verify: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val wrong = stringResource(R.string.auth_pin_wrong)
    val spacing = MyHealthTokens.spacing

    RareSheet(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.auth_reauth_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = pin,
            onValueChange = {
                pin = it
                error = null
            },
            label = stringResource(R.string.auth_reauth_title),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
        )
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            RareBanner(text = error!!)
        }
        Spacer(Modifier.height(spacing.md))
        RareButton(
            text = stringResource(R.string.common_confirm),
            onClick = {
                if (verify(pin)) onVerified() else error = wrong
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
