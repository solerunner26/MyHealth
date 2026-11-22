package com.solerunner.myhealth.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Numeric PIN entry with a real keypad (not a text field), so entry behaves the same on every device
 * and never summons a keyboard over the lock screen. The PIN is never echoed or logged.
 */
@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    title: String? = null,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onBiometric: (() -> Unit)? = null,
) {
    val spacing = MyHealthTokens.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            repeat(pin.length.coerceAtMost(PinHasher.MAX_LENGTH)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            repeat((4 - pin.length).coerceAtLeast(0)) {
                Box(Modifier.size(12.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(spacing.xl))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "back")
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    row.forEach { key ->
                        PinKey(
                            label = key,
                            enabled = when (key) {
                                "bio" -> onBiometric != null
                                "back" -> pin.isNotEmpty()
                                else -> pin.length < PinHasher.MAX_LENGTH
                            },
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
            Spacer(Modifier.height(spacing.xl))
            Button(onClick = onAction, enabled = PinHasher.isValidFormat(pin), modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
        if (onBiometric != null) {
            Spacer(Modifier.height(spacing.sm))
            TextButton(onClick = onBiometric) { Text(stringResource(R.string.auth_use_biometric)) }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    val isBack = label == "back"
    val isBio = label == "bio"
    val description = when {
        isBack -> stringResource(R.string.common_back)
        isBio -> stringResource(R.string.auth_use_biometric)
        else -> label
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
                if (isBack || isBio) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isBack -> Icon(Icons.Filled.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            isBio -> Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            else -> Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
