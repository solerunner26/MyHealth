package app.myhealth.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myhealth.core.designsystem.theme.MyHealthTheme

/**
 * Frosted-glass card: translucent gradient over the ambient background, hairline edge in the accent's tint, large
 * radius. Emphasis raises the edge weight and adds an accent glow (fill weight carries meaning, not colour alone).
 */
@Composable
fun MyHealthCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    emphasis: Emphasis = Emphasis.Subtle,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MyHealthTheme.colors
    val shape = MyHealthTheme.radii.lgShape
    val fill = when (emphasis) {
        Emphasis.Subtle -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.glassTop, c.glassBottom))
        Emphasis.Medium -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.glassTop, c.accentSoft.copy(alpha = 0.92f)))
        Emphasis.Strong -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.accentContainer, c.accentSoft))
    }
    val border = when (emphasis) {
        Emphasis.Subtle -> BorderStroke(MyHealthTheme.stroke.hairline, c.glassBorder)
        Emphasis.Medium -> BorderStroke(MyHealthTheme.stroke.regular, c.accent.copy(alpha = 0.75f))
        Emphasis.Strong -> BorderStroke(MyHealthTheme.stroke.strong, c.accent)
    }
    val glowElevation = when (emphasis) { Emphasis.Subtle -> 0.dp; Emphasis.Medium -> 10.dp; Emphasis.Strong -> 18.dp }
    val base = modifier.fillMaxWidth()
        .shadow(glowElevation, shape, ambientColor = c.glow, spotColor = c.glow)
        .clip(shape).background(fill).border(border, shape)
    val clickable = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Column(clickable.padding(MyHealthTheme.spacing.lg), content = content)
}

@Composable
fun MyHealthSectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = MyHealthTheme.spacing.lg, bottom = MyHealthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MyHealthTheme.typography.title,
            color = MyHealthTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke(this)
    }
}

@Composable
fun MyHealthListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = MyHealthTheme.colors
    val base = modifier.fillMaxWidth().heightIn(min = 56.dp)
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(vertical = MyHealthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft).border(MyHealthTheme.stroke.hairline, c.glassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(leadingIcon, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(MyHealthTheme.spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MyHealthTheme.typography.subtitle, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MyHealthTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(MyHealthTheme.spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.xs), content = trailing)
        }
    }
}

@Composable
fun MyHealthDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = MyHealthTheme.stroke.hairline, color = MyHealthTheme.colors.outline)
}

/** Status pill. Status = icon + label + fill weight; colour is the single accent at varying weights. */
@Composable
fun MyHealthStatusBadge(label: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val shape = MyHealthTheme.radii.pillShape
    val (bg, fg, border) = when (kind.emphasis) {
        Emphasis.Subtle -> Triple(c.surface, c.textSecondary, BorderStroke(MyHealthTheme.stroke.hairline, c.outlineStrong))
        Emphasis.Medium -> Triple(c.accentContainer, c.onAccentContainer, BorderStroke(MyHealthTheme.stroke.regular, c.accent))
        Emphasis.Strong -> Triple(c.accent, c.onAccent, BorderStroke(MyHealthTheme.stroke.strong, c.accent))
    }
    Row(
        modifier.clip(shape).background(bg).border(border, shape)
            .padding(horizontal = MyHealthTheme.spacing.sm, vertical = MyHealthTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kind.icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(MyHealthTheme.spacing.xs))
        Text(label, style = MyHealthTheme.typography.caption, color = fg)
    }
}

@Composable
fun MyHealthMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    status: Pair<String, StatusKind>? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = MyHealthTheme.colors
    MyHealthCard(modifier = modifier, onClick = onClick) {
        Text(label, style = MyHealthTheme.typography.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.padding(top = MyHealthTheme.spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.foundation.text.BasicText(value, style = MyHealthTheme.typography.metric.copy(color = c.textPrimary), maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = MyHealthTheme.typography.metric.fontSize),
                modifier = Modifier.weight(1f, fill = false))
            if (unit != null) {
                Spacer(Modifier.width(MyHealthTheme.spacing.xs))
                Text(unit, style = MyHealthTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (status != null) {
            Spacer(Modifier.padding(top = MyHealthTheme.spacing.xs))
            MyHealthStatusBadge(status.first, status.second)
        }
        if (caption != null) {
            Spacer(Modifier.padding(top = MyHealthTheme.spacing.xs))
            Text(caption, style = MyHealthTheme.typography.caption, color = c.textTertiary)
        }
    }
}

@Composable
fun MyHealthInfoBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = MyHealthIcons.Info) {
    val c = MyHealthTheme.colors
    Row(
        modifier.fillMaxWidth().clip(MyHealthTheme.radii.mdShape).background(c.accentSoft.copy(alpha = 0.85f))
            .border(MyHealthTheme.stroke.hairline, c.glassBorder, MyHealthTheme.radii.mdShape)
            .padding(MyHealthTheme.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(MyHealthTheme.spacing.sm))
        Text(text, style = MyHealthTheme.typography.bodySmall, color = c.textPrimary)
    }
}

/** Non-diagnostic disclaimer shown under every insight. */
@Composable
fun MyHealthDisclaimer(text: String, modifier: Modifier = Modifier) =
    MyHealthInfoBanner(text = text, modifier = modifier, icon = MyHealthIcons.Help)

@Composable
fun MyHealthAvatar(initials: String, selected: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 36.dp, photo: androidx.compose.ui.graphics.ImageBitmap? = null) {
    val c = MyHealthTheme.colors
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (selected) c.accent else c.surfaceSunken)
            .border(MyHealthTheme.stroke.regular, if (selected) c.accent else c.outlineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) androidx.compose.foundation.Image(photo, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(initials, style = MyHealthTheme.typography.label, color = if (selected) c.onAccent else c.textPrimary)
    }
}
