package com.solerunner.myhealth.ui.medicines

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ConfirmDialog
import com.solerunner.myhealth.design.components.EmptyState
import com.solerunner.myhealth.design.components.ListRow
import com.solerunner.myhealth.design.components.SegmentedControl
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.ui.containerViewModel

@Composable
fun MedicinesScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { MedicinesViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Medicine?>(null) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.meds_saved)

    LaunchedEffect(state.message) {
        if (state.message != null) {
            viewModel.consumeMessage()
            snackbar.showSnackbar(savedText)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding, vertical = spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.meds_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = viewModel::create) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.meds_add))
                }
            }
            Spacer(Modifier.height(spacing.sm))
            SegmentedControl(
                options = MedicineTab.entries,
                selected = state.tab,
                onSelect = viewModel::selectTab,
                label = {
                    when (it) {
                        MedicineTab.ACTIVE -> stringResource(R.string.meds_active)
                        MedicineTab.PAST -> stringResource(R.string.meds_past)
                    }
                },
            )
            Spacer(Modifier.height(spacing.md))

            if (state.visible.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.meds_empty_title),
                    body = stringResource(R.string.meds_empty_body),
                    actionLabel = stringResource(R.string.meds_add),
                    onAction = viewModel::create,
                )
            } else {
                state.visible.forEach { medicine ->
                    ListRow(
                        title = medicine.name,
                        subtitle = listOfNotNull(medicine.strength, medicine.frequencyText, medicine.molecule)
                            .joinToString(" · ")
                            .ifBlank { null },
                        trailing = medicine.endDate?.toString() ?: stringResource(R.string.meds_ongoing),
                        onClick = { viewModel.edit(medicine) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.creating || state.editing != null) {
        MedicineEditorDialog(
            initial = state.editing,
            onDismiss = viewModel::dismissForm,
            onSave = viewModel::save,
            onDelete = { pendingDelete = it },
        )
    }

    pendingDelete?.let { medicine ->
        ConfirmDialog(
            title = stringResource(R.string.meds_delete_title),
            body = medicine.name,
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { viewModel.delete(medicine) },
            onDismiss = { pendingDelete = null },
        )
    }
}
