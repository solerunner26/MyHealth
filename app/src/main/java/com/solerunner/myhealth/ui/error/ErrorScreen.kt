package com.solerunner.myhealth.ui.error

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

@Composable
fun ErrorScreen(
    message: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(MyHealthTokens.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            com.solerunner.myhealth.design.rare.RareButton(
                text = androidx.compose.ui.res.stringResource(com.solerunner.myhealth.R.string.common_retry),
                onClick = onRetry,
            )
        }
    }
}
