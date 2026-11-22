package app.myhealth.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
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

/**
 * Floating tab bar. The selected tab sits on a capsule that SLIDES between items (spring), carrying the icon and a
 * bold label; Glass = accent gradient capsule with glow on a frosted bar, Solid = hero-ink capsule on a paper bar
 * with a soft shadow. Status is never colour alone: capsule, weight and label all change.
 */
@Composable
fun MyHealthBottomBar(items: List<NavItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val shape = MyHealthTheme.radii.xlShape
    val index = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val reduce = MyHealthTheme.reduceMotion
    val pos by androidx.compose.animation.core.animateFloatAsState(
        index.toFloat(),
        if (reduce) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 380f), label = NAV,
    )
    val barBg = if (c.isGlass) Modifier.shadow(16.dp, shape, ambientColor = c.glow, spotColor = c.scrim)
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.glassTop, c.surfaceRaised.copy(alpha = 0.94f))), shape)
        .border(MyHealthTheme.stroke.hairline, c.glassBorder, shape)
    else Modifier.shadow(18.dp, shape, ambientColor = c.shadow, spotColor = c.shadow).background(c.surfaceRaised, shape)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MyHealthTheme.spacing.md, vertical = MyHealthTheme.spacing.sm)
            .then(barBg).clip(shape).padding(MyHealthTheme.spacing.xs),
    ) {
        val itemW = maxWidth / items.size
        val capsule = if (c.isGlass) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.accent, androidx.compose.ui.graphics.lerp(c.accent, c.accentBright, 0.5f)))
            else androidx.compose.ui.graphics.SolidColor(c.hero)
        Box(
            Modifier.offset(x = itemW * pos).width(itemW).height(60.dp).padding(horizontal = 4.dp)
                .shadow(if (c.isGlass) 12.dp else 6.dp, MyHealthTheme.radii.lgShape, ambientColor = c.glow, spotColor = if (c.isGlass) c.glow else c.hero)
                .background(capsule, MyHealthTheme.radii.lgShape),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            items.forEachIndexed { i, item ->
                val selected = i == index
                val near = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                val onCapsule = if (c.isGlass) c.onAccent else c.onHero
                val tint = androidx.compose.ui.graphics.lerp(c.textSecondary, onCapsule, near)
                Column(
                    Modifier.weight(1f).height(60.dp).clip(MyHealthTheme.radii.lgShape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.key) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp).scale(1f + 0.08f * near))
                    Text(item.label, style = MyHealthTheme.typography.caption.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
                        color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
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
