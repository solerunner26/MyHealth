package com.solerunner.myhealth.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareDot
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * Compatibility layer over the Rare primitives.
 *
 * Screens were written against these names before the redesign; keeping them as thin aliases means the
 * whole product picked up the new surfaces, hairlines and badges in one step, and new screens can use
 * the Rare components directly.
 */

enum class PillTone { NEUTRAL, GOOD, WARN, ALERT, INFO }

@Composable
fun HealthCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    RareCard(modifier = modifier, onClick = onClick, content = content)
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        RareSectionLabel(text = title, actionLabel = actionLabel, onAction = onAction)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    RareBadge(
        text = text,
        modifier = modifier,
        tone = when (tone) {
            PillTone.GOOD, PillTone.INFO -> RareTone.ACCENT
            PillTone.ALERT -> RareTone.SOLID
            PillTone.WARN -> RareTone.NEUTRAL
            PillTone.NEUTRAL -> RareTone.OUTLINE
        },
        icon = icon,
    )
}

@Composable
fun StatusDot(tone: PillTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        PillTone.GOOD -> MaterialTheme.colorScheme.primary
        PillTone.ALERT -> MaterialTheme.colorScheme.onSurface
        PillTone.WARN -> MaterialTheme.colorScheme.outline
        PillTone.INFO, PillTone.NEUTRAL -> MaterialTheme.colorScheme.outlineVariant
    }
    RareDot(color = color, modifier = modifier)
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) = RareDivider(modifier = modifier)

/** Bordered table container; the Rare card with no inner padding so rows can own their hairlines. */
@Composable
fun TableContainer(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    RareCard(modifier = modifier, contentPadding = 0.dp, content = content)
}

@Composable
fun BorderBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    RareCard(modifier = modifier, inset = true, content = content)
}

@Composable
fun RowSpacer(width: Int = 8) = Spacer(Modifier.width(width.dp))

@Composable
fun ColumnSpacer(height: Int = 8) = Spacer(Modifier.height(height.dp))

@Composable
fun SpacedRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MyHealthTokens.spacing.sm), verticalAlignment = Alignment.CenterVertically, content = content)
}
