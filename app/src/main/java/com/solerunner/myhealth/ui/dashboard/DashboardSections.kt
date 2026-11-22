package com.solerunner.myhealth.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.data.HealthSnapshot
import com.solerunner.myhealth.design.components.InitialsAvatar
import com.solerunner.myhealth.design.components.MetricTile
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.ui.common.displayWithUnit
import com.solerunner.myhealth.ui.common.label
import java.time.LocalTime

@Composable
fun GreetingHeader(profile: Profile, onOpenProfile: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.home_greeting_named, greeting(), profile.displayName.substringBefore(' ')),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Box(Modifier.clickable(onClick = onOpenProfile)) {
            InitialsAvatar(profile.initials)
        }
    }
}

@Composable
fun greeting(): String {
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

@Composable
fun LatestVitalsSection(snapshot: HealthSnapshot?, onOpenVitals: () -> Unit) {
    val latest = snapshot?.measurements.orEmpty()
        .groupBy { it.kind }
        .mapValues { entry -> entry.value.maxBy { it.recordedDay } }
    SectionHeader(
        title = stringResource(R.string.home_latest_vitals),
        actionLabel = stringResource(R.string.common_see_all),
        onAction = onOpenVitals,
    )
    if (latest.isEmpty()) {
        Text(
            stringResource(R.string.home_vitals_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(MyHealthTokens.spacing.sm)) {
            latest.entries.take(3).forEach { (kind, measurement) ->
                MetricTile(
                    label = kind.label(),
                    value = measurement.displayWithUnit(),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenVitals,
                )
            }
        }
    }
}
