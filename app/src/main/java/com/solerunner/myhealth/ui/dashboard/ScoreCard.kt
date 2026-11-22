package com.solerunner.myhealth.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.data.HealthSnapshot
import com.solerunner.myhealth.design.components.HealthCard
import com.solerunner.myhealth.design.components.RingGauge
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.ScoreBand

@Composable
fun ScoreBand.label(): String = when (this) {
    ScoreBand.EXCELLENT -> stringResource(R.string.home_score_band_excellent)
    ScoreBand.GOOD -> stringResource(R.string.home_score_band_good)
    ScoreBand.FAIR -> stringResource(R.string.home_score_band_fair)
    ScoreBand.NEEDS_ATTENTION -> stringResource(R.string.home_score_band_attention)
    ScoreBand.INSUFFICIENT_DATA -> stringResource(R.string.home_score_band_insufficient)
}

/**
 * The health-score card. The ring is the hero number; underneath, one line says how many of the five
 * domains had enough data, so the score never pretends to more certainty than it has.
 */
@Composable
fun ScoreCard(snapshot: HealthSnapshot?, onOpenInsights: () -> Unit, modifier: Modifier = Modifier) {
    val score = snapshot?.score
    val domainsWithData = score?.domains?.count { it.score != null } ?: 0
    val spacing = MyHealthTokens.spacing
    HealthCard(modifier = modifier) {
        Text(
            stringResource(R.string.home_score_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(
                score = score?.total,
                bandLabel = if (score == null || score.band == ScoreBand.INSUFFICIENT_DATA) {
                    stringResource(R.string.home_score_band_insufficient)
                } else {
                    score.band.label()
                },
            )
            Spacer(Modifier.width(spacing.lg))
            Column(Modifier.weight(1f)) {
                if (score?.total == null) {
                    Text(
                        stringResource(R.string.home_score_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.home_score_based, domainsWithData),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(spacing.xs))
                TextButton(onClick = onOpenInsights, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(R.string.home_focus_all))
                }
            }
        }
    }
}
