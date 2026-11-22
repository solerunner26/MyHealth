package app.myhealth.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme

@Immutable
data class NavItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun MyHealthTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = MyHealthTheme.colors
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).heightIn(min = 68.dp)
            .padding(start = if (onBack != null) MyHealthTheme.spacing.xs else MyHealthTheme.spacing.gutter, end = MyHealthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MyHealthIconButton(MyHealthIcons.Back, backLabel, onBack)
            Spacer(Modifier.width(MyHealthTheme.spacing.xs))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MyHealthTheme.typography.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MyHealthTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Floating frosted-glass tab bar. Selected tab = accent pill + glow + bold label (never colour alone). */
@Composable
fun MyHealthBottomBar(items: List<NavItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val shape = MyHealthTheme.radii.xlShape
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MyHealthTheme.spacing.md, vertical = MyHealthTheme.spacing.sm)
            .shadow(16.dp, shape, ambientColor = c.glow, spotColor = c.scrim)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.glassTop, c.surfaceRaised.copy(alpha = 0.94f))))
            .border(MyHealthTheme.stroke.hairline, c.glassBorder, shape)
            .padding(horizontal = MyHealthTheme.spacing.xs, vertical = MyHealthTheme.spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            val w by animateFloatAsState(if (selected) 1f else 0f, tween(MyHealthTheme.motion.mediumMillis), label = NAV)
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clip(MyHealthTheme.radii.mdShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.key) })
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 52.dp, height = 30.dp).clip(MyHealthTheme.radii.pillShape)
                        .background(c.accentContainer.copy(alpha = w))
                        .border(MyHealthTheme.stroke.hairline, c.accent.copy(alpha = 0.6f * w), MyHealthTheme.radii.pillShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(item.icon, contentDescription = null, tint = if (selected) c.onAccentContainer else c.textSecondary, modifier = Modifier.size(22.dp)) }
                Text(item.label, style = MyHealthTheme.typography.caption.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
                    color = if (selected) c.textPrimary else c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private const val NAV = "nav" // animation inspector id, not UI copy

@Composable
fun MyHealthNavRail(items: List<NavItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, header: @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null) {
    val c = MyHealthTheme.colors
    NavigationRail(modifier = modifier, containerColor = c.glassTop, contentColor = c.textPrimary, header = header) {
        items.forEach { item ->
            NavigationRailItem(
                selected = item.key == selectedKey,
                onClick = { onSelect(item.key) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MyHealthTheme.typography.caption) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = c.onAccentContainer, selectedTextColor = c.textPrimary, indicatorColor = c.accentContainer,
                    unselectedIconColor = c.textSecondary, unselectedTextColor = c.textSecondary,
                ),
            )
        }
    }
}
