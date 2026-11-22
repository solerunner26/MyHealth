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

/** Passphrase entry for creating or restoring an encrypted backup. Never stored anywhere. */
@Composable
fun BackupPassphraseSheet(
    exporting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val spacing = MyHealthTokens.spacing
    val tooShort = passphrase.length < 8

    RareSheet(onDismiss = onDismiss) {
        Text(
            if (exporting) stringResource(R.string.settings_backup_create) else stringResource(R.string.settings_backup_restore),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                error = null
            },
            label = stringResource(R.string.settings_backup_passphrase),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = stringResource(R.string.settings_backup_passphrase_hint),
            isError = error != null,
        )
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            RareBanner(text = error!!)
        }
        Spacer(Modifier.height(spacing.md))
        RareButton(
            text = stringResource(R.string.common_continue),
            onClick = {
                if (tooShort) {
                    error = "Use at least 8 characters"
                } else {
                    onSubmit(passphrase.toCharArray())
                }
            },
            enabled = !tooShort,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
