package com.solerunner.myhealth.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareConfirmDialog
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareEmptyState
import com.solerunner.myhealth.design.rare.RareIconButton
import com.solerunner.myhealth.design.rare.RareListRow
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSegmented
import com.solerunner.myhealth.design.rare.RareSnackbarHost
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.ui.containerViewModel
import kotlinx.coroutines.launch

@Composable
fun MedicinesScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { MedicinesViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Medicine?>(null) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.meds_saved)
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.active.any { it.reminderEnabled }) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            state.active.any { it.reminderEnabled } &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                title = stringResource(R.string.meds_title),
                eyebrow = stringResource(R.string.meds_active_count, state.active.size),
                modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
                actions = {
                    RareIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.meds_add),
                        onClick = viewModel::create,
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
                RareSegmented(
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
                    RareEmptyState(
                        title = stringResource(R.string.meds_empty_title),
                        body = stringResource(R.string.meds_empty_body),
                        actionLabel = stringResource(R.string.meds_add),
                        onAction = viewModel::create,
                    )
                } else {
                    RareCard(contentPadding = 0.dp) {
                        state.visible.forEachIndexed { index, medicine ->
                            Column(Modifier.padding(horizontal = 14.dp)) {
                                RareListRow(
                                    title = medicine.name,
                                    subtitle = listOfNotNull(medicine.strength, medicine.frequencyText, medicine.molecule)
                                        .joinToString(" · ")
                                        .ifBlank { null },
                                    trailing = medicine.endDate?.toString() ?: stringResource(R.string.meds_ongoing),
                                    onClick = { viewModel.edit(medicine) },
                                )
                            }
                            if (index != state.visible.lastIndex) RareDivider(Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(spacing.lg))
                Text(
                    stringResource(R.string.meds_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xxxl))
            }
        }
        RareSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
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
        RareConfirmDialog(
            title = stringResource(R.string.meds_delete_title),
            body = medicine.name,
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { viewModel.delete(medicine) },
            onDismiss = { pendingDelete = null },
        )
    }
}
