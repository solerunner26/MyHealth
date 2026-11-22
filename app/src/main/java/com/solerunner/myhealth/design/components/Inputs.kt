package com.solerunner.myhealth.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.solerunner.myhealth.design.rare.RareSearchField
import com.solerunner.myhealth.design.rare.RareSegmented

/** Segmented control alias over the Rare implementation (sliding pill, equal widths). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    RareSegmented(options = options, selected = selected, onSelect = onSelect, modifier = modifier, label = label)
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    RareSearchField(query = query, onQueryChange = onQueryChange, placeholder = placeholder, modifier = modifier)
}
