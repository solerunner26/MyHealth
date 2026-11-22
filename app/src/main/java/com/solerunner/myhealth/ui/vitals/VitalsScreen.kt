package com.solerunner.myhealth.ui.vitals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareEmptyState
import com.solerunner.myhealth.design.rare.RareIconButton
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareSnackbarHost
import com.solerunner.myhealth.design.rare.RareConfirmDialog
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.ui.common.displayValue
import com.solerunner.myhealth.ui.containerViewModel
import kotlinx.coroutines.launch

@Composable
fun VitalsScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { VitalsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Measurement?>(null) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val savedText = stringResource(R.string.vitals_saved)
    val scroll = rememberScrollState()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            viewModel.consumeMessage()
            scope.launch { snackbar.showSnackbar(savedText) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            RareScrollProgress(scroll)
            RarePageHeader(
                title = stringResource(R.string.vitals_title),
                eyebrow = if (state.all.isEmpty()) null else stringResource(R.string.trends_points, state.all.size),
                modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
                actions = {
                    RareIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.vitals_add),
                        onClick = { showAdd = true },
                        filled = true,
                    )
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.screenPadding),
            ) {
                if (state.all.isEmpty()) {
                    RareEmptyState(
                        title = stringResource(R.string.vitals_empty_title),
                        body = stringResource(R.string.vitals_empty_body),
                        actionLabel = stringResource(R.string.vitals_add),
                        onAction = { showAdd = true },
                    )
                    return@Column
                }
                VitalsOverview(state = state, onSelect = viewModel::select)
                Spacer(Modifier.height(spacing.lg))
                RareSectionLabel(text = stringResource(R.string.vitals_history))
                RareCard(contentPadding = 0.dp) {
                    VitalsHistory(measurements = state.visible, onDelete = { pendingDelete = it })
                }
                Spacer(Modifier.height(spacing.lg))
                com.solerunner.myhealth.design.rare.RareButton(
                    text = stringResource(R.string.vitals_add),
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.xxxl))
            }
        }
        RareSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
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
        RareConfirmDialog(
            title = stringResource(R.string.vitals_delete_title),
            body = measurement.displayValue(),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { viewModel.delete(measurement.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}
