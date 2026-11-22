package com.solerunner.myhealth.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.AnimatedCounter
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareBanner
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareLinearProgress
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.ScoreBand
import com.solerunner.myhealth.ui.common.AllClearCard
import com.solerunner.myhealth.ui.common.FocusCard
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel
import com.solerunner.myhealth.ui.dashboard.DashboardViewModel

/**
 * Insights: the score broken into its five domains, every focus item with its evidence, and the
 * standing disclaimer. All computed locally from the user's own record.
 */
@Composable
fun InsightsScreen(container: AppContainer, onOpenReport: (Long) -> Unit) {
    val viewModel = containerViewModel(container) { DashboardViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = MyHealthTokens.spacing
    val score = state.snapshot?.score
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        RareScrollProgress(scroll)
        RarePageHeader(
            title = stringResource(R.string.insight_title),
            eyebrow = stringResource(R.string.insight_focus_title),
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenPadding),
        ) {
            RareCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(value = score?.total, style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.padding(bottom = 4.dp)) {
                        RareBadge(
                            text = score?.band?.label() ?: stringResource(R.string.home_score_band_insufficient),
                            tone = when (score?.band) {
                                ScoreBand.EXCELLENT, ScoreBand.GOOD -> RareTone.ACCENT
                                ScoreBand.FAIR -> RareTone.NEUTRAL
                                ScoreBand.NEEDS_ATTENTION -> RareTone.SOLID
                                else -> RareTone.OUTLINE
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.insight_score_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(spacing.md))
                score?.domains?.forEachIndexed { index, domain ->
                    val name = when (domain.key) {
                        "cardio" -> stringResource(R.string.insight_domain_cardio)
                        "blood" -> stringResource(R.string.insight_domain_blood)
                        "organ" -> stringResource(R.string.insight_domain_organ)
                        "lifestyle" -> stringResource(R.string.insight_domain_lifestyle)
                        else -> stringResource(R.string.insight_domain_monitoring)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            domain.score?.toString() ?: stringResource(R.string.insight_score_missing),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.insight_weight, (domain.weight * 100).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    RareLinearProgress(progress = (domain.score ?: 0) / 100f)
                    if (index != score.domains.lastIndex) Spacer(Modifier.height(spacing.md))
                }
                if (score == null || score.band == ScoreBand.INSUFFICIENT_DATA) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.insight_no_domains),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(spacing.lg))
            RareSectionLabel(text = stringResource(R.string.insight_focus_title))
            if (state.focus.isEmpty()) {
                AllClearCard()
            } else {
                state.focus.forEachIndexed { index, item ->
                    FocusCard(
                        item = item,
                        expanded = true,
                        onClick = item.relatedReportId?.let { id -> { onOpenReport(id) } },
                    )
                    if (index != state.focus.lastIndex) Spacer(Modifier.height(spacing.sm))
                }
            }

            Spacer(Modifier.height(spacing.lg))
            RareBanner(text = stringResource(R.string.insight_disclaimer))
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}
