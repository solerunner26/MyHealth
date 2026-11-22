package com.solerunner.myhealth.ui.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.HealthCard
import com.solerunner.myhealth.design.components.MetricTile
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.ScoreBand
import com.solerunner.myhealth.ui.common.AllClearCard
import com.solerunner.myhealth.ui.common.FocusCard
import com.solerunner.myhealth.ui.containerViewModel
import com.solerunner.myhealth.ui.dashboard.DashboardViewModel
import com.solerunner.myhealth.ui.dashboard.label

/**
 * The insight screen: every focus item with its evidence, the score broken into its five domains, and
 * the standing disclaimer. Nothing here is generated remotely or with a model — it is all computed from
 * the user's own imported values.
 */
@Composable
fun InsightsScreen(container: AppContainer, onOpenReport: (Long) -> Unit) {
    val viewModel = containerViewModel(container) { DashboardViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = MyHealthTokens.spacing
    val score = state.snapshot?.score

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenPadding, vertical = spacing.md),
    ) {
        Text(stringResource(R.string.insight_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.md))

        HealthCard {
            Text(stringResource(R.string.insight_score_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(spacing.sm))
            Text(
                score?.band?.label() ?: stringResource(R.string.home_score_band_insufficient),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(spacing.md))
            Text(stringResource(R.string.insight_score_domains), style = MaterialTheme.typography.labelLarge)
            score?.domains?.forEach { domain ->
                val name = when (domain.key) {
                    "cardio" -> stringResource(R.string.insight_domain_cardio)
                    "blood" -> stringResource(R.string.insight_domain_blood)
                    "organ" -> stringResource(R.string.insight_domain_organ)
                    "lifestyle" -> stringResource(R.string.insight_domain_lifestyle)
                    else -> stringResource(R.string.insight_domain_monitoring)
                }
                MetricTile(
                    label = name,
                    value = domain.score?.toString() ?: stringResource(R.string.insight_score_missing),
                    caption = stringResource(R.string.insight_weight, (domain.weight * 100).toInt()),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                )
            }
            if (score == null || score.band == ScoreBand.INSUFFICIENT_DATA) {
                Text(
                    stringResource(R.string.insight_no_domains),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(spacing.md))
        SectionHeader(title = stringResource(R.string.insight_focus_title))
        if (state.focus.isEmpty()) {
            AllClearCard()
        } else {
            state.focus.forEach { item ->
                FocusCard(
                    item = item,
                    expanded = true,
                    modifier = Modifier.padding(bottom = spacing.sm),
                    onClick = item.relatedReportId?.let { id -> { onOpenReport(id) } },
                )
            }
        }

        Spacer(Modifier.height(spacing.lg))
        Text(
            stringResource(R.string.insight_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxxl))
    }
}
