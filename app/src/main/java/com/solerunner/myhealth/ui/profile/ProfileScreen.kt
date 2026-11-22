package com.solerunner.myhealth.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.rare.RareAvatar
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChoiceDialog
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareSnackbarHost
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { ProfileViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSex by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.profile_saved)
    val scroll = rememberScrollState()

    LaunchedEffect(state.saved) {
        if (state.saved) {
            scope.launch { snackbar.showSnackbar(savedText) }
            viewModel.clearSaved()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            RareScrollProgress(scroll)
            RarePageHeader(
                title = stringResource(R.string.profile_title),
                subtitle = stringResource(R.string.profile_subtitle),
                modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                val draft = state.draft
                RareCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RareAvatar(draft.initials, size = 52)
                        Spacer(Modifier.width(spacing.md))
                        Column {
                            Text(draft.displayName.ifBlank { stringResource(R.string.profile_name) }, style = MaterialTheme.typography.titleMedium)
                            if (draft.ageYears != null) {
                                Text(
                                    stringResource(R.string.profile_age, draft.ageYears ?: 0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.md))
                    RareTextField(value = draft.displayName, onValueChange = viewModel::setName, label = stringResource(R.string.profile_name))
                    Spacer(Modifier.height(spacing.sm))
                    DateField(
                        label = stringResource(R.string.profile_dob),
                        date = draft.dateOfBirth,
                        onChange = viewModel::setDateOfBirth,
                        allowClear = true,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showSex = true }) { Text(draft.sex.label()) }
                    }
                }

                RareCard {
                    RareSectionLabel(text = stringResource(R.string.profile_height))
                    RareTextField(
                        value = draft.heightCm?.toString().orEmpty(),
                        onValueChange = { viewModel.setHeight(it.toDoubleOrNull()) },
                        label = stringResource(R.string.profile_height),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    RareTextField(
                        value = draft.bloodGroup.orEmpty(),
                        onValueChange = viewModel::setBloodGroup,
                        label = stringResource(R.string.profile_blood_group),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    RareTextField(
                        value = draft.targetWeightKg?.toString().orEmpty(),
                        onValueChange = { viewModel.setTargetWeight(it.toDoubleOrNull()) },
                        label = stringResource(R.string.profile_target_weight),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    RareTextField(
                        value = draft.allergies.joinToString(", "),
                        onValueChange = viewModel::setAllergies,
                        label = stringResource(R.string.profile_allergies),
                        supportingText = stringResource(R.string.common_comma_separated),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    RareTextField(
                        value = draft.conditions.joinToString(", "),
                        onValueChange = viewModel::setConditions,
                        label = stringResource(R.string.profile_conditions),
                        supportingText = stringResource(R.string.common_comma_separated),
                    )
                }

                RareCard {
                    RareSectionLabel(text = stringResource(R.string.profile_emergency_name))
                    RareTextField(
                        value = draft.emergencyContactName.orEmpty(),
                        onValueChange = viewModel::setEmergencyName,
                        label = stringResource(R.string.profile_emergency_name),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    RareTextField(
                        value = draft.emergencyContactPhone.orEmpty(),
                        onValueChange = viewModel::setEmergencyPhone,
                        label = stringResource(R.string.profile_emergency_phone),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        supportingText = stringResource(R.string.profile_emergency_hint),
                    )
                }

                if (state.error != null) {
                    Text(stringResource(R.string.auth_name_required), style = MaterialTheme.typography.bodySmall)
                }
                RareButton(
                    text = stringResource(R.string.common_save),
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.xxxl))
            }
        }
        RareSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showSex) {
        RareChoiceDialog(
            title = stringResource(R.string.profile_sex),
            options = Sex.entries,
            selected = state.draft.sex,
            onSelect = viewModel::setSex,
            onDismiss = { showSex = false },
            label = { it.label() },
        )
    }
}
