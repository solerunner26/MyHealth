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
import com.solerunner.myhealth.R
import com.solerunner.myhealth.core.security.PinHasher
import com.solerunner.myhealth.design.rare.RareBanner
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareSheet
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens

/** Change PIN in a Rare sheet: two masked fields, validated and matched before saving. */
@Composable
fun ChangePinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.auth_pin_mismatch)
    val invalidFormat = stringResource(R.string.auth_pin_invalid)
    val spacing = MyHealthTokens.spacing

    RareSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.settings_change_pin), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = first,
            onValueChange = { first = it },
            label = stringResource(R.string.auth_setup_title),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(spacing.sm))
        RareTextField(
            value = second,
            onValueChange = { second = it },
            label = stringResource(R.string.auth_confirm_title),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            RareBanner(text = error!!)
        }
        Spacer(Modifier.height(spacing.md))
        RareButton(
            text = stringResource(R.string.common_save),
            onClick = {
                when {
                    !PinHasher.isValidFormat(first) -> error = invalidFormat
                    first != second -> error = mismatch
                    else -> onSubmit(first)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
