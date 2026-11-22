package com.solerunner.myhealth.ui.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.HealthCard
import com.solerunner.myhealth.design.components.LoadingState
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.SummaryLine
import com.solerunner.myhealth.ui.containerViewModel
import java.time.format.DateTimeFormatter

/** Preview of the doctor-visit summary plus a one-tap PDF export through the share sheet. */
@Composable
fun DoctorSummaryScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel = containerViewModel(container) { DoctorSummaryViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MyHealthTokens.spacing
    val shareLabel = stringResource(R.string.common_share)

    LaunchedEffect(state.shareIntent) {
        state.shareIntent?.let { intent ->
            context.startActivity(android.content.Intent.createChooser(intent, shareLabel))
            viewModel.clearShare()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = spacing.screenPadding)) {
        com.solerunner.myhealth.design.rare.RarePageHeader(
            title = stringResource(R.string.doctor_title),
            onBack = onBack,
            modifier = Modifier.padding(vertical = spacing.md),
        )

        val summary = state.summary
        if (summary == null) {
            if (state.loading) LoadingState(message = stringResource(R.string.common_loading)) else
                Text(state.error ?: stringResource(R.string.doctor_nothing_flagged))
            return@Column
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
            Text(
                stringResource(R.string.doctor_since, summary.windowStart.format(formatter)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            SummarySection(stringResource(R.string.doctor_changed), summary.headline)
            SummarySection(stringResource(R.string.doctor_flagged), summary.flagged)
            SummarySection(stringResource(R.string.doctor_improved), summary.improved)
            SummarySection(
                stringResource(R.string.doctor_medicines),
                summary.medicines.map { SummaryLine(label = it, value = "") },
            )
            SummarySection(
                stringResource(R.string.doctor_questions),
                summary.questions,
            )
            com.solerunner.myhealth.design.rare.RareButton(
                text = stringResource(R.string.doctor_generate),
                onClick = viewModel::generatePdf,
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
            )
            Text(
                stringResource(R.string.insight_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun SummarySection(title: String, lines: List<SummaryLine>) {
    SectionHeader(title = title)
    if (lines.isEmpty()) {
        Text(
            stringResource(R.string.doctor_nothing_flagged),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        HealthCard {
            lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(line.label, style = MaterialTheme.typography.bodyLarge)
                        line.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (line.value.isNotBlank()) {
                        Text(line.value, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
