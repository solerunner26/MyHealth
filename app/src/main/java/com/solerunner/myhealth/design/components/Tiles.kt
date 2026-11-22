package com.solerunner.myhealth.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareStat

/** Compact stat inside a quiet inset card. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    RareCard(modifier = modifier, onClick = onClick, inset = true, contentPadding = 12.dp) {
        Column(Modifier.padding(0.dp)) {
            RareStat(label = label, value = value, unit = unit, caption = caption)
        }
    }
}
