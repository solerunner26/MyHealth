package com.solerunner.myhealth.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.*
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel

@Composable
fun ReportDetailScreen(
    container: AppContainer,
    reportId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel = containerViewModel(container, key = "report-$reportId") { ReportDetailViewModel(it, reportId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing

    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Column(Modifier.fillMaxSize().padding(horizontal = spacing.screenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                stringResource(R.string.records_detail_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
            }
        }

        val report = state.report
        if (report == null) {
            if (!state.loading) EmptyState(title = stringResource(R.string.records_no_results), body = "")
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item {
                Text(report.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${report.kind.label()} · ${report.sampleDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                TableContainer(Modifier.padding(vertical = spacing.sm)) {
                    Column(Modifier.padding(spacing.cardPadding)) {
                        val meta = listOf(
                            R.string.records_detail_lab to report.labName,
                            R.string.records_detail_doctor to report.doctorName,
                            R.string.records_detail_sample_date to report.sampleDate.toString(),
                            R.string.records_detail_reported_date to report.reportedDate?.toString(),
                            R.string.records_detail_pages to report.pageCount.toString(),
                            R.string.records_detail_imported to report.importedAt.toString().take(10),
                            R.string.records_detail_notes to report.notes.takeIf { it.isNotBlank() },
                            R.string.records_detail_source to report.ocrConfidence?.let { "OCR ${(it * 100).toInt()}%" },
                        )
                        meta.forEach { (labelRes, value) ->
                            if (!value.isNullOrBlank()) LabeledValue(stringResource(labelRes), value)
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.records_detail_results, state.results.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = spacing.md),
                )
            }
            if (state.results.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.records_detail_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.results, key = { it.id }) { result ->
                    ResultRow(result)
                    ThinDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.records_delete_title),
            body = stringResource(R.string.records_delete_body),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
