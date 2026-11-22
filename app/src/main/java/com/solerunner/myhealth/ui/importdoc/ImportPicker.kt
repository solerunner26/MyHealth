package com.solerunner.myhealth.ui.importdoc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareBanner
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareButtonStyle
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareIconCoin
import com.solerunner.myhealth.design.rare.RareListRow
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens

@Composable
fun ImportPicker(onBack: () -> Unit, onPick: () -> Unit, onCamera: () -> Unit) {
    val spacing = MyHealthTokens.spacing
    Column(Modifier.fillMaxSize()) {
        RarePageHeader(
            title = stringResource(R.string.import_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.sm),
        )
        Column(Modifier.padding(horizontal = spacing.screenPadding)) {
            com.solerunner.myhealth.design.rare.RareStepPlayer(
                steps = listOf(
                    stringResource(R.string.import_step_pick),
                    stringResource(R.string.import_step_read),
                    stringResource(R.string.import_step_review),
                    stringResource(R.string.import_step_save),
                ),
                current = 0,
            )
            Spacer(Modifier.height(spacing.lg))
            Text(
                stringResource(R.string.import_pick_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.lg))
            RareCard(contentPadding = 0.dp) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    RareListRow(
                        title = stringResource(R.string.import_pick_file),
                        subtitle = stringResource(R.string.import_source_pdf) + " · " + stringResource(R.string.import_source_image),
                        leading = { RareIconCoin(Icons.Filled.DateRange) },
                        onClick = onPick,
                    )
                    com.solerunner.myhealth.design.rare.RareDivider()
                    RareListRow(
                        title = stringResource(R.string.import_take_photo),
                        leading = { RareIconCoin(Icons.Filled.Add) },
                        onClick = onCamera,
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg))
            RareBanner(text = stringResource(R.string.import_review_body))
            Spacer(Modifier.height(spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RareButton(
                    text = stringResource(R.string.import_pick_file),
                    onClick = onPick,
                    modifier = Modifier.weight(1f),
                )
                RareButton(
                    text = stringResource(R.string.import_take_photo),
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                    style = RareButtonStyle.SECONDARY,
                )
            }
        }
    }
}
