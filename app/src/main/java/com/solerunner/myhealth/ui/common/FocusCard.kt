package com.solerunner.myhealth.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.HealthCard
import com.solerunner.myhealth.design.components.StatusPill
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.FocusItem

/**
 * One "what should I focus on" card: severity pill, localized headline and explanation, plus the
 * evidence rows that produced it. Evidence is text, not colour, so it survives translation and
 * colour-blindness alike.
 */
@Composable
fun FocusCard(
    item: FocusItem,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val spacing = MyHealthTokens.spacing
    HealthCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusPill(text = item.severity.labelText(), tone = item.severity.tone())
            Spacer(Modifier.weight(1f))
            if (item.discussionPoint) {
                Text(
                    stringResource(R.string.doctor_questions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(spacing.sm))
        Text(item.titleText(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(spacing.xs))
        Text(
            item.bodyText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded && item.evidence.isNotEmpty()) {
            Spacer(Modifier.height(spacing.md))
            Text(
                stringResource(R.string.insight_evidence),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.evidence.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Compact "nothing needs attention" panel used by both home and insights. */
@Composable
fun AllClearCard(modifier: Modifier = Modifier) {
    HealthCard(modifier = modifier) {
        Text(stringResource(R.string.home_focus_empty), style = MaterialTheme.typography.bodyMedium)
    }
}
