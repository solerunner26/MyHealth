package com.solerunner.myhealth.design.rare

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * Top bar: title left, actions right, one hairline underneath. The Rare pattern keeps chrome quiet so
 * the record itself is the loudest thing on screen.
 */
@Composable
fun RareTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = MyHealthTokens.spacing.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                val interaction = rememberInteraction()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .pressScale(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        RareDivider()
    }
}

data class RareNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Bottom bar with a sliding dot above the active icon, the Rare navigation cue. Labels stay visible
 * (unlike icon-only bars) so the IA is legible without a first-use guess.
 */
@Composable
fun RareBottomBar(
    items: List<RareNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        RareDivider()
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
            val itemWidth = maxWidth / items.size
            val index = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
            val dotOffset by animateDpAsState(itemWidth * index + itemWidth / 2 - 3.dp, indicatorSpring(), label = "nav-dot")
            Box(
                Modifier
                    .offset(x = dotOffset, y = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { item ->
                    val active = item.route == currentRoute
                    val tint by animateFloatAsState(if (active) 1f else 0.55f, label = "nav-tint")
                    Column(
                        modifier = Modifier
                            .width(itemWidth)
                            .clickable { onNavigate(item.route) }
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = tint),
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = tint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Wide-screen navigation rail with the same dot indicator, plus a brand seal on top. */
@Composable
fun RareNavRail(
    items: List<RareNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(88.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "MH",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        items.forEach { item ->
            val active = item.route == currentRoute
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(item.route) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                )
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}


/** Page header used inside screens: a small eyebrow line, a large title and an actions row. */
@Composable
fun RarePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                val backInteraction = rememberInteraction()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .pressScale(backInteraction)
                        .clickable(interactionSource = backInteraction, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Text(
                        eyebrow.uppercase(),
                        style = com.solerunner.myhealth.design.theme.RareLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
