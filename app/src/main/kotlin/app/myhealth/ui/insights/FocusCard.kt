package app.myhealth.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.myhealth.core.designsystem.components.Emphasis
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.analytics.Severity
import app.myhealth.ui.common.focusText
import app.myhealth.ui.common.kind
import app.myhealth.ui.common.label

/** Severity is encoded three ways: badge glyph, badge fill weight, and card border weight. */
@Composable
fun FocusCard(item: FocusItem, onClick: (() -> Unit)?, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    val t = focusText(item)
    val emphasis = when (item.severity) { Severity.High -> Emphasis.Strong; Severity.Moderate -> Emphasis.Medium; else -> Emphasis.Subtle }
    MyHealthCard(modifier, onClick = onClick, emphasis = emphasis) {
        Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.xs)) {
            MyHealthStatusBadge(label(item.severity), item.severity.kind())
            Text(t.title, style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
            Text(t.body, style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
            extra?.invoke()
        }
    }
}
