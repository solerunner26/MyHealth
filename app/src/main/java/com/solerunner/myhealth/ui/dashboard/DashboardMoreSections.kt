package com.solerunner.myhealth.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ListRow
import com.solerunner.myhealth.design.components.MetaChip
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.components.ThinDivider
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.navigation.Routes

@Composable
fun RecentReportsSection(reports: List<Report>, onOpenReport: (Long) -> Unit, onImport: () -> Unit) {
    SectionHeader(title = stringResource(R.string.home_recent_reports))
    if (reports.isEmpty()) {
        Column {
            Text(
                stringResource(R.string.home_reports_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MyHealthTokens.spacing.sm))
            Button(onClick = onImport) { Text(stringResource(R.string.home_import_report)) }
        }
    } else {
        reports.take(3).forEach { report ->
            ListRow(
                title = report.title,
                subtitle = "${report.sampleDate} · ${report.kind.label()}",
                trailing = report.labName,
                onClick = { onOpenReport(report.id) },
            )
            ThinDivider()
        }
        TextButton(onClick = onImport) { Text(stringResource(R.string.home_import_report)) }
    }
}

@Composable
fun QuickActions(onNavigate: (String) -> Unit) {
    val spacing = MyHealthTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader(title = stringResource(R.string.home_quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(onClick = { onNavigate(Routes.IMPORT) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.import_pick_file))
            }
            OutlinedButton(onClick = { onNavigate(Routes.VITALS) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_add_vital))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(onClick = { onNavigate(Routes.TRENDS) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_quick_trend))
            }
            OutlinedButton(onClick = { onNavigate(Routes.DOCTOR_SUMMARY) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_quick_summary))
            }
        }
        MetaChip(text = stringResource(R.string.settings_privacy))
    }
}
