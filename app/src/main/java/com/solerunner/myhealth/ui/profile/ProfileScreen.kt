package com.solerunner.myhealth.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ChoiceDialog
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel

/**
 * Profile: identity, body basics and emergency contact. Age, sex and height are the fields that make
 * reference ranges and BMI meaningful, so they are labelled with why they are asked for.
 */
@Composable
fun ProfileScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { ProfileViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSex by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.profile_saved)

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(savedText)
            viewModel.clearSaved()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding, vertical = spacing.md),
        ) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.profile_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            val draft = state.draft
            ProfileField(draft.displayName, viewModel::setName, stringResource(R.string.profile_name))
            DateField(
                label = stringResource(R.string.profile_dob),
                date = draft.dateOfBirth,
                onChange = viewModel::setDateOfBirth,
                allowClear = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_sex), modifier = Modifier.weight(1f))
                TextButton(onClick = { showSex = true }) { Text(draft.sex.label()) }
            }
            ProfileField(draft.heightCm?.let { it.toString() }.orEmpty(), { viewModel.setHeight(it.toDoubleOrNull()) }, stringResource(R.string.profile_height), numeric = true)
            ProfileField(draft.bloodGroup.orEmpty(), viewModel::setBloodGroup, stringResource(R.string.profile_blood_group))
            ProfileField(
                draft.allergies.joinToString(", "),
                viewModel::setAllergies,
                stringResource(R.string.profile_allergies) + " · " + stringResource(R.string.common_comma_separated),
            )
            ProfileField(
                draft.conditions.joinToString(", "),
                viewModel::setConditions,
                stringResource(R.string.profile_conditions) + " · " + stringResource(R.string.common_comma_separated),
            )
            ProfileField(draft.emergencyContactName.orEmpty(), viewModel::setEmergencyName, stringResource(R.string.profile_emergency_name))
            ProfileField(
                draft.emergencyContactPhone.orEmpty(),
                viewModel::setEmergencyPhone,
                stringResource(R.string.profile_emergency_phone),
                numeric = true,
            )
            ProfileField(
                draft.targetWeightKg?.let { it.toString() }.orEmpty(),
                { viewModel.setTargetWeight(it.toDoubleOrNull()) },
                stringResource(R.string.profile_target_weight),
                numeric = true,
            )
            if (state.error != null) {
                Text(stringResource(R.string.auth_name_required), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(spacing.md))
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_save))
            }
            Spacer(Modifier.height(spacing.xl))
            Text(
                stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xxxl))
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showSex) {
        ChoiceDialog(
            title = stringResource(R.string.profile_sex),
            options = Sex.entries,
            selected = state.draft.sex,
            onSelect = viewModel::setSex,
            onDismiss = { showSex = false },
            label = { it.label() },
        )
    }
}

@Composable
private fun ProfileField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    numeric: Boolean = false,
) {
    Spacer(Modifier.height(MyHealthTokens.spacing.sm))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
