package com.solerunner.myhealth.design.rare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.theme.MyHealthTokens
import java.time.LocalDate
import kotlin.math.roundToInt

/** Three large choice tiles with icon and label, used for the theme (and reusable for other enums). */
@Composable
fun <T> RareChoiceTiles(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
    icon: ((T) -> ImageVector)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            val interaction = rememberInteraction()
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction, pressedScale = 0.97f)
                    .clip(MaterialTheme.shapes.medium)
                    .then(
                        Modifier.clickableCompat(interaction) { onSelect(option) },
                    ),
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (icon != null) {
                        Icon(
                            icon(option),
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableCompat(
    source: androidx.compose.foundation.interaction.MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.clickable(
        interactionSource = source,
        indication = null,
        onClick = onClick,
    ),
)

/** Quick duration chips (7 / 14 / 30 / 90 days) for a medicine course. */
@Composable
fun RareDurationChips(selectedDays: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(7, 14, 30, 90)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { days ->
            RareChip(
                text = stringResource(R.string.meds_days, days),
                selected = selectedDays == days,
                onClick = { onSelect(days) },
            )
        }
    }
}

/**
 * Destructive action that requires a deliberate press and hold, the Rare delete interaction. The ring
 * fills while the finger stays down; releasing early cancels, so a stray tap can never erase data.
 */
@Composable
fun RareHoldToDelete(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 1_400,
) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        var elapsed = 0
        while (elapsed < holdMillis) {
            delay(40)
            elapsed += 40
            progress = (elapsed.toFloat() / holdMillis).coerceIn(0f, 1f)
        }
        progress = 0f
        holding = false
        onConfirm()
    }

    Surface(
        modifier = modifier
            .semantics { contentDescription = label }
            .pointerInput(holdMillis) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                )
            },
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(22.dp)) {
                    drawArc(
                        color = colors.outlineVariant,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    drawArc(
                        color = colors.primary,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Expandable folder-style group used by the records timeline. */
@Composable
fun RareFolderGroup(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) { content() }
    }
}

/** Guided step indicator (pick, read, review, save). */
@Composable
fun RareStepPlayer(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, step ->
            val done = index < current
            val active = index == current
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (done || active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (done || active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 13-week consistency grid: one square per day, darker when the day has data. */
@Composable
fun RareHeatmap(
    days: List<LocalDate>,
    counts: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    weeks: Int = 13,
) {
    val max = counts.values.maxOrNull() ?: 1
    val end = LocalDate.now()
    val start = end.minusDays((weeks * 7 - 1).toLong()).with(java.time.DayOfWeek.MONDAY)
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier = modifier.fillMaxWidth().height(88.dp)) {
        val cell = size.width / (weeks * 7f)
        val gap = cell * 0.22f
        for (index in 0 until weeks * 7) {
            val date = start.plusDays(index.toLong())
            if (date.isAfter(end)) break
            val value = counts[date] ?: 0
            val intensity = if (value == 0) 0f else (0.25f + 0.75f * (value.toFloat() / max))
            val week = index / 7
            val day = index % 7
            val left = week * cell + gap / 2
            val top = day * (size.height / 7f) + gap / 2
            drawRoundRect(
                color = if (intensity == 0f) empty else color.copy(alpha = intensity),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cell - gap, size.height / 7f - gap),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f, 2.5f),
            )
        }
    }
}

/** Bell with an unread count, used for focus alerts. */
@Composable
fun RareNotificationBell(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.insight_focus_title),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (count > 9) "9+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Expandable question/answer row for the About screen. */
@Composable
fun RareFaqItem(question: String, answer: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
