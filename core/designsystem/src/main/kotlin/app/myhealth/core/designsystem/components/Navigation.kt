package app.myhealth.core.designsystem.components

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
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).heightIn(min = 64.dp)
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

@Composable
fun MyHealthBottomBar(items: List<NavItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    NavigationBar(modifier = modifier, containerColor = c.surface, contentColor = c.textPrimary, tonalElevation = MyHealthTheme.elevation.none) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.key) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MyHealthTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.onAccentContainer, selectedTextColor = c.textPrimary, indicatorColor = c.accentContainer,
                    unselectedIconColor = c.textSecondary, unselectedTextColor = c.textSecondary,
                ),
            )
        }
    }
}

@Composable
fun MyHealthNavRail(items: List<NavItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, header: @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null) {
    val c = MyHealthTheme.colors
    NavigationRail(modifier = modifier, containerColor = c.surface, contentColor = c.textPrimary, header = header) {
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
