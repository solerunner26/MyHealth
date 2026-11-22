package com.solerunner.myhealth.design.rare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * Bottom sheet with the Rare handle and 20dp top radius. Used for the add/edit forms so the form never
 * covers the whole record it belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RareSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Spacer(Modifier.size(10.dp))
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(width = MyHealthTokens.dims.sheetHandleWidth, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.size(14.dp))
        },
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = MyHealthTokens.spacing.screenPadding).navigationBarsPadding()) {
            content()
            Spacer(Modifier.size(20.dp))
        }
    }
}

/** Confirmation dialog, styled like a Rare surface with action buttons in a row. */
@Composable
fun RareConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            RareButton(
                text = confirmLabel,
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                style = if (destructive) RareButtonStyle.DESTRUCTIVE else RareButtonStyle.PRIMARY,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.solerunner.myhealth.R.string.common_cancel)) }
        },
    )
}

/** Single-choice dialog with RadioButton rows, used for enums (document type, form, sex, delay). */
@Composable
fun <T> RareChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    label: @Composable (T) -> String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onSelect(option)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.solerunner.myhealth.R.string.common_cancel)) }
        },
    )
}

/** Full-width banner with icon, for inline errors and offline notes. */
@Composable
fun RareBanner(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}
