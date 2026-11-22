package com.solerunner.myhealth.ui.importdoc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.theme.MyHealthTokens

@Composable
fun ImportPicker(onBack: () -> Unit, onPick: () -> Unit, onCamera: () -> Unit) {
    val spacing = MyHealthTokens.spacing
    Column(Modifier.fillMaxSize().padding(spacing.screenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
        Spacer(Modifier.height(spacing.lg))
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.import_pick_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xl))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_pick_file))
        }
        Spacer(Modifier.height(spacing.sm))
        OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_take_photo))
        }
        Spacer(Modifier.height(spacing.xl))
        Text(
            stringResource(R.string.import_review_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
