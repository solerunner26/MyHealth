package com.solerunner.myhealth.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.data.HealthSnapshot
import com.solerunner.myhealth.design.rare.AnimatedCounter
import com.solerunner.myhealth.design.rare.RareAvatar
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareButtonStyle
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareIconButton
import com.solerunner.myhealth.design.rare.RareListRow
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareProgressRing
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareStat
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.ScoreBand
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.ui.common.AllClearCard
import com.solerunner.myhealth.ui.common.FocusCard
import com.solerunner.myhealth.ui.common.displayWithUnit
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel
import com.solerunner.myhealth.ui.navigation.Routes
import java.time.LocalTime
import java.util.Locale

/**
 * Home, rebuilt as a Rare-style bento page: one hero score, a short focus list, vitals, medicines and
 * reports. Every block is a hairline card on porcelain, with generous whitespace instead of dividers.
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
    val scroll = rememberScrollState()
    val spacing = MyHealthTokens.spacing

    Column(Modifier.fillMaxSize()) {
        RareScrollProgress(scroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenPadding, vertical = spacing.md),
        ) {
            RarePageHeader(
                eyebrow = Dates.format(Dates.today(), Locale.getDefault(), "EEEE, dd MMM"),
                title = stringResource(R.string.home_greeting_named, greetingText(), profile.displayName.substringBefore(' ')),
                actions = {
                    RareIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        onClick = { onNavigate(Routes.SETTINGS) },
                    )
                    Column(Modifier.clickable { onNavigate(Routes.PROFILE) }) {
                        RareAvatar(profile.initials, size = 40)
                    }
                },
            )
            Spacer(Modifier.height(spacing.lg))

            ScoreHero(snapshot = snapshot, onOpenInsights = { onNavigate(Routes.INSIGHTS) })

            Spacer(Modifier.height(spacing.lg))
            RareSectionLabel(
                text = stringResource(R.string.home_focus_title),
                actionLabel = stringResource(R.string.home_focus_all),
                onAction = { onNavigate(Routes.INSIGHTS) },
            )
            if (state.focus.isEmpty()) {
                AllClearCard()
            } else {
                state.focus.take(3).forEachIndexed { index, item ->
                    FocusCard(
                        item = item,
                        onClick = item.relatedReportId?.let { id -> { onOpenReport(id) } },
                    )
                    if (index != state.focus.take(3).lastIndex) Spacer(Modifier.height(spacing.sm))
                }
            }

            Spacer(Modifier.height(spacing.lg))
            RareSectionLabel(
                text = stringResource(R.string.home_latest_vitals),
                actionLabel = stringResource(R.string.common_see_all),
                onAction = { onNavigate(Routes.VITALS) },
            )
            VitalsRow(snapshot = snapshot, onOpenVitals = { onNavigate(Routes.VITALS) })

            Spacer(Modifier.height(spacing.lg))
            RareSectionLabel(
                text = stringResource(R.string.home_meds_due),
                actionLabel = stringResource(R.string.common_see_all),
                onAction = { onNavigate(Routes.MEDICINES) },
            )
            MedicinesCard(snapshot = snapshot, onOpen = { onNavigate(Routes.MEDICINES) })

            Spacer(Modifier.height(spacing.lg))
            RareSectionLabel(
                text = stringResource(R.string.home_recent_reports),
                actionLabel = stringResource(R.string.common_see_all),
                onAction = { onNavigate(Routes.RECORDS) },
            )
            ReportsCard(
                snapshot = snapshot,
                onOpenReport = onOpenReport,
                onImport = { onNavigate(Routes.IMPORT) },
            )

            Spacer(Modifier.height(spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RareButton(
                    text = stringResource(R.string.home_import_report),
                    onClick = { onNavigate(Routes.IMPORT) },
                    modifier = Modifier.weight(1f),
                )
                RareButton(
                    text = stringResource(R.string.home_add_vital),
                    onClick = { onNavigate(Routes.VITALS) },
                    modifier = Modifier.weight(1f),
                    style = RareButtonStyle.SECONDARY,
                )
            }
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RareButton(
                    text = stringResource(R.string.home_quick_trend),
                    onClick = { onNavigate(Routes.TRENDS) },
                    modifier = Modifier.weight(1f),
                    style = RareButtonStyle.GHOST,
                )
                RareButton(
                    text = stringResource(R.string.home_quick_summary),
                    onClick = { onNavigate(Routes.DOCTOR_SUMMARY) },
                    modifier = Modifier.weight(1f),
                    style = RareButtonStyle.GHOST,
                )
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun ScoreHero(snapshot: HealthSnapshot?, onOpenInsights: () -> Unit) {
    val score = snapshot?.score
    val domainCount = score?.domains?.count { it.score != null } ?: 0
    RareCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_score_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(
                        value = score?.total,
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.padding(bottom = 6.dp)) {
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
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (score?.total == null) stringResource(R.string.home_score_no_data)
                    else stringResource(R.string.home_score_based, domainCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                RareButton(
                    text = stringResource(R.string.home_focus_all),
                    onClick = onOpenInsights,
                    style = RareButtonStyle.GHOST,
                )
            }
            RareProgressRing(progress = (score?.total ?: 0) / 100f, size = 92, stroke = 4)
        }
    }
}

@Composable
private fun VitalsRow(snapshot: HealthSnapshot?, onOpenVitals: () -> Unit) {
    val latest = snapshot?.measurements.orEmpty()
        .groupBy { it.kind }
        .mapValues { entry -> entry.value.maxBy { it.recordedDay } }
    if (latest.isEmpty()) {
        RareCard(inset = true) {
            Text(
                stringResource(R.string.home_vitals_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            RareButton(
                text = stringResource(R.string.home_add_vital),
                onClick = onOpenVitals,
                style = RareButtonStyle.SECONDARY,
            )
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MyHealthTokens.spacing.sm)) {
        latest.entries.take(3).forEach { (kind, measurement) ->
            RareCard(modifier = Modifier.weight(1f), onClick = onOpenVitals, contentPadding = 12.dp) {
                RareStat(
                    label = kind.label(),
                    value = measurement.displayWithUnit(),
                )
            }
        }
    }
}

@Composable
private fun MedicinesCard(snapshot: HealthSnapshot?, onOpen: () -> Unit) {
    val active = snapshot?.activeMedicines.orEmpty()
    if (active.isEmpty()) {
        RareCard(inset = true) {
            Text(
                stringResource(R.string.home_meds_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    RareCard(contentPadding = 0.dp) {
        active.take(3).forEachIndexed { index, medicine ->
            Column(Modifier.padding(horizontal = 14.dp)) {
                RareListRow(
                    title = medicine.name,
                    subtitle = listOfNotNull(medicine.strength, medicine.frequencyText).joinToString(" · ").ifBlank { null },
                    trailing = medicine.endDate?.toString() ?: stringResource(R.string.meds_ongoing),
                    onClick = onOpen,
                )
            }
            if (index != active.take(3).lastIndex) RareDivider(Modifier.padding(horizontal = 14.dp))
        }
    }
}

@Composable
private fun ReportsCard(snapshot: HealthSnapshot?, onOpenReport: (Long) -> Unit, onImport: () -> Unit) {
    val reports = snapshot?.reports.orEmpty()
    if (reports.isEmpty()) {
        RareCard(inset = true) {
            Text(
                stringResource(R.string.home_reports_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            RareButton(text = stringResource(R.string.home_import_report), onClick = onImport)
        }
        return
    }
    RareCard(contentPadding = 0.dp) {
        reports.take(3).forEachIndexed { index, report ->
            Column(Modifier.padding(horizontal = 14.dp)) {
                RareListRow(
                    title = report.title,
                    subtitle = "${report.sampleDate} · ${report.kind.label()}",
                    trailing = report.labName,
                    onClick = { onOpenReport(report.id) },
                )
            }
            if (index != reports.take(3).lastIndex) RareDivider(Modifier.padding(horizontal = 14.dp))
        }
        RareDivider()
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            RareButton(
                text = stringResource(R.string.home_import_report),
                onClick = onImport,
                style = RareButtonStyle.GHOST,
            )
        }
    }
}

@Composable
fun greetingText(): String {
    val hour = LocalTime.now().hour
    val res = when {
        hour < 5 -> R.string.home_greeting_night
        hour < 12 -> R.string.home_greeting_morning
        hour < 17 -> R.string.home_greeting_afternoon
        hour < 21 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }
    return stringResource(res)
}
