package com.solerunner.myhealth.design.rare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Label above, hairline box below. Focus thickens the border instead of adding a glow, which keeps the
 * control readable in sunlight and stays inside the one-hue rule.
 */
@Composable
fun RareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    minHeight: Int = 46,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (enabled) colors.surface else colors.surfaceContainerLow,
            border = BorderStroke(
                if (focused) 1.5.dp else 1.dp,
                when {
                    isError -> colors.onSurface
                    focused -> colors.primary
                    else -> colors.outlineVariant
                },
            ),
        ) {
            Row(
                modifier = Modifier.heightIn(min = minHeight.dp).padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 10.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = colors.onSurface),
                    singleLine = singleLine,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = if (singleLine) 1 else 4,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        }
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun RareSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    RareTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = Icons.Filled.Search,
        trailing = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
    )
}
