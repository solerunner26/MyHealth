package com.solerunner.myhealth.design.rare

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Two to four options on a quiet track, with a pill that slides to the active one. Equal widths keep
 * the indicator honest at every label length; long labels ellipsize rather than resizing the control.
 */
@Composable
fun <T> RareSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    if (options.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val index = options.indexOf(selected).coerceAtLeast(0)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerHigh,
    ) {
        BoxWithConstraints(Modifier.padding(3.dp)) {
            val itemWidth = maxWidth / options.size
            val pillOffset by animateDpAsState(
                targetValue = itemWidth * index,
                animationSpec = indicatorSpring(),
                label = "segmented-pill",
            )
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(itemWidth)
                    .height(34.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surface)
                    .then(Modifier.hairline(colors.outlineVariant, MaterialTheme.shapes.small)),
            )
            Row(Modifier.height(34.dp)) {
                options.forEach { option ->
                    val active = option == selected
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) colors.onSurface else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Underline tabs for dense pages (report sections, settings groups). */
@Composable
fun <T> RareUnderlineTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    Row(modifier = modifier) {
        val colors = MaterialTheme.colorScheme
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
