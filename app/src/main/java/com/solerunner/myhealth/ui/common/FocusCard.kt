package com.solerunner.myhealth.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.theme.RareLabelStyle
import com.solerunner.myhealth.domain.model.FocusItem
import com.solerunner.myhealth.domain.model.Severity

/**
 * One "what should I focus on" card. Severity is a badge with wording, the explanation is a sentence,
 * and evidence is a short list of the actual readings that triggered the rule.
 */
@Composable
fun FocusCard(
    item: FocusItem,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    RareCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RareBadge(
                text = item.severity.labelText(),
                tone = when (item.severity) {
                    Severity.ATTENTION -> RareTone.SOLID
                    Severity.WATCH -> RareTone.NEUTRAL
                    Severity.INFO -> RareTone.ACCENT
                },
            )
            Spacer(Modifier.weight(1f))
            if (item.discussionPoint) {
                Text(
                    stringResource(R.string.doctor_questions).uppercase(),
                    style = RareLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(item.titleText(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            item.bodyText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded && item.evidence.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.insight_evidence).uppercase(),
                style = RareLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Column { item.evidence.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
fun AllClearCard(modifier: Modifier = Modifier) {
    RareCard(modifier = modifier, inset = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RareBadge(text = stringResource(R.string.insight_good_news), tone = RareTone.ACCENT)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.home_focus_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
