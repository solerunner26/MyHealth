package com.solerunner.myhealth.ui.vitals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ConfirmDialog
import com.solerunner.myhealth.design.components.EmptyState
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.ui.common.displayValue
import com.solerunner.myhealth.ui.containerViewModel

@Composable
fun VitalsScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { VitalsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Measurement?>(null) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.vitals_saved)

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
                    stringResource(R.string.vitals_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vitals_add))
                }
            }

            if (state.all.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.vitals_empty_title),
                    body = stringResource(R.string.vitals_empty_body),
                    actionLabel = stringResource(R.string.vitals_add),
                    onAction = { showAdd = true },
                )
                return@Column
            }

            VitalsOverview(state = state, onSelect = viewModel::select)
            Spacer(Modifier.height(spacing.md))
            SectionHeader(title = stringResource(R.string.vitals_history))
            VitalsHistory(measurements = state.visible, onDelete = { pendingDelete = it })
            TextButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vitals_add))
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAdd) {
        AddVitalDialog(
            onDismiss = { showAdd = false },
            onSave = { kind, primary, secondary, day, note, customLabel ->
                showAdd = false
                viewModel.add(kind, primary, secondary, day, note, customLabel)
            },
        )
    }

    pendingDelete?.let { measurement ->
        ConfirmDialog(
            title = stringResource(R.string.vitals_delete_title),
            body = measurement.displayValue(),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { viewModel.delete(measurement.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}
