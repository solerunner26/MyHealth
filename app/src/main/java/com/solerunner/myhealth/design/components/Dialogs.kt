package com.solerunner.myhealth.design.components

import androidx.compose.runtime.Composable
import com.solerunner.myhealth.design.rare.RareChoiceDialog
import com.solerunner.myhealth.design.rare.RareConfirmDialog

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    RareConfirmDialog(
        title = title,
        body = body,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = destructive,
    )
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    label: @Composable (T) -> String,
) {
    RareChoiceDialog(
        title = title,
        options = options,
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        label = label,
    )
}
