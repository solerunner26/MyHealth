package com.solerunner.myhealth.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareAvatar
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareKeyValue
import com.solerunner.myhealth.design.rare.RareListRow
import com.solerunner.myhealth.design.rare.RareTone

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    leading: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    RareListRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        trailing = trailing,
        leading = leading,
        onClick = onClick,
    )
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    RareKeyValue(label = label, value = value, modifier = modifier)
}

@Composable
fun MetaChip(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    RareBadge(text = text, modifier = modifier, tone = RareTone.OUTLINE, icon = icon)
}

@Composable
fun GoodNewsBadge(modifier: Modifier = Modifier) {
    RareBadge(
        text = stringResource(R.string.insight_good_news),
        modifier = modifier,
        tone = RareTone.ACCENT,
        icon = Icons.Filled.Check,
    )
}

@Composable
fun AlertBadge(text: String, modifier: Modifier = Modifier) {
    RareBadge(text = text, modifier = modifier, tone = RareTone.SOLID, icon = Icons.Filled.Warning)
}

@Composable
fun WatchBadge(text: String, modifier: Modifier = Modifier) {
    RareBadge(text = text, modifier = modifier, tone = RareTone.NEUTRAL, icon = Icons.Filled.Info)
}

@Composable
fun InitialsAvatar(initials: String, modifier: Modifier = Modifier, size: Int = 40) {
    RareAvatar(initials = initials, modifier = modifier, size = size)
}

/** Small icon and label pair used inside dense rows. */
@Composable
fun IconLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InlineNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp),
    )
}
