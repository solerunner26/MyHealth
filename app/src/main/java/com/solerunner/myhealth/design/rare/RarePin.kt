package com.solerunner.myhealth.design.rare

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.core.security.PinHasher
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * PIN entry in the Rare OTP idiom: square slots that fill as digits arrive, a keypad instead of a soft
 * keyboard, and a caret that only blinks on the active slot. Digits are masked with a dot. The number of
 * slots grows in rows of four because a PIN may be 4 to 12 digits long.
 */
@Composable
fun RarePinEntry(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    error: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onBiometric: (() -> Unit)? = null,
    note: String? = null,
) {
    val spacing = MyHealthTokens.spacing
    val rows = when {
        pin.length < 4 -> 1
        else -> ((pin.length + 1 + 3) / 4).coerceIn(1, 3)
    }
    val slots = rows * 4

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(22.dp))

        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { column ->
                    val index = row * 4 + column
                    val filled = index < pin.length
                    val active = index == pin.length && pin.length < slots
                    PinSlot(filled = filled, active = active)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "back")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        PinKey(
                            key = key,
                            enabled = when (key) {
                                "bio" -> onBiometric != null
                                "back" -> pin.isNotEmpty()
                                else -> pin.length < PinHasher.MAX_LENGTH
                            },
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (key) {
                                    "back" -> onPinChange(pin.dropLast(1))
                                    "bio" -> onBiometric?.invoke()
                                    else -> if (pin.length < PinHasher.MAX_LENGTH) onPinChange(pin + key)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            RareButton(
                text = actionLabel,
                onClick = onAction,
                enabled = PinHasher.isValidFormat(pin),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onBiometric != null) {
            TextButton(onClick = onBiometric) { Text(stringResource(R.string.auth_use_biometric)) }
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PinSlot(filled: Boolean, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "pin-caret")
    val caretAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "caret-alpha",
    )
    Surface(
        modifier = Modifier.size(width = 46.dp, height = 54.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (filled) colors.surfaceContainerLow else colors.surface,
        border = BorderStroke(
            if (active) 1.5.dp else 1.dp,
            if (active) colors.primary else if (filled) colors.outline else colors.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (filled) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(colors.onSurface))
            } else if (active) {
                Box(Modifier.width(1.5.dp).height(18.dp).alpha(caretAlpha).background(colors.primary))
            }
        }
    }
}

@Composable
private fun PinKey(key: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isBack = key == "back"
    val isBio = key == "bio"
    val description = when {
        isBack -> stringResource(R.string.common_back)
        isBio -> stringResource(R.string.auth_use_biometric)
        else -> key
    }
    val colors = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    Surface(
        modifier = modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pressScale(interaction, enabled, pressedScale = 0.95f)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isBack -> Icon(Icons.Filled.Clear, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                isBio -> Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                else -> Text(key, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
