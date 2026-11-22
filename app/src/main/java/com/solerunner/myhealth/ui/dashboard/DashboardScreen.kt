package com.solerunner.myhealth.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ListRow
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.ui.common.AllClearCard
import com.solerunner.myhealth.ui.common.FocusCard
import com.solerunner.myhealth.ui.containerViewModel
import com.solerunner.myhealth.ui.navigation.Routes

/**
 * Home. Everything is derived from the encrypted local record: score, focus items, latest vitals,
 * medicines and recent reports. There is no remote fetch to fail, so each section has its own empty
 * state instead of a global spinner.
 */
@Composable
fun DashboardScreen(
    container: AppContainer,
    profile: Profile,
    onNavigate: (String) -> Unit,
    onOpenReport: (Long) -> Unit,
) {
    val viewModel = containerViewModel(container) { DashboardViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val spacing = MyHealthTokens.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.screenPadding,
            end = spacing.screenPadding,
            top = spacing.md,
            bottom = spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            GreetingHeader(profile = profile, onOpenProfile = { onNavigate(Routes.PROFILE) })
        }
        item {
            ScoreCard(snapshot = snapshot, onOpenInsights = { onNavigate(Routes.INSIGHTS) })
        }
        item {
            SectionHeader(
                title = stringResource(R.string.home_focus_title),
                actionLabel = stringResource(R.string.home_focus_all),
                onAction = { onNavigate(Routes.INSIGHTS) },
            )
        }
        if (state.focus.isEmpty()) {
            item { AllClearCard() }
        } else {
            items(state.focus.take(3), key = { it.code.name + (it.analyteKey ?: "") }) { item ->
                FocusCard(
                    item = item,
                    onClick = item.relatedReportId?.let { id -> { onOpenReport(id) } },
                )
            }
        }
        item { LatestVitalsSection(snapshot = snapshot, onOpenVitals = { onNavigate(Routes.VITALS) }) }
        item {
            SectionHeader(
                title = stringResource(R.string.home_meds_due),
                actionLabel = stringResource(R.string.common_see_all),
                onAction = { onNavigate(Routes.MEDICINES) },
            )
        }
        val activeMedicines = snapshot?.activeMedicines.orEmpty()
        if (activeMedicines.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_meds_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(activeMedicines.take(3), key = { "med-${it.id}" }) { medicine ->
                ListRow(
                    title = medicine.name,
                    subtitle = listOfNotNull(medicine.strength, medicine.frequencyText).joinToString(" · ").ifBlank { null },
                    onClick = { onNavigate(Routes.MEDICINES) },
                )
            }
        }
        item {
            RecentReportsSection(
                reports = snapshot?.reports.orEmpty(),
                onOpenReport = onOpenReport,
                onImport = { onNavigate(Routes.IMPORT) },
            )
        }
        item { QuickActions(onNavigate = onNavigate) }
    }
}
