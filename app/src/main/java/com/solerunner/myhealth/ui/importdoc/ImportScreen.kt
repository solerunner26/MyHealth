package com.solerunner.myhealth.ui.importdoc

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.LoadingState
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.ui.containerViewModel
import java.io.File

/**
 * Import flow: pick a PDF/image or take a photo → read with the text layer or OCR → review → save.
 * The camera writes into app cache and is handed over through the app's FileProvider, so no storage
 * permission is required and the temporary image never leaves private storage.
 */
@Composable
fun ImportScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val viewModel = containerViewModel(container) { ImportViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MyHealthTokens.spacing

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.read(it, displayName(context, it), context.contentResolver.getType(it)) }
    }
    val photoFile = remember {
        val directory = File(context.cacheDir, "imports").apply { mkdirs() }
        File(directory, "capture-${System.currentTimeMillis()}.jpg")
    }
    val photoUri: Uri = remember(photoFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) viewModel.read(photoUri, photoFile.name, "image/jpeg")
    }

    when (val current = state) {
        is ImportUiState.Idle -> ImportPicker(
            onBack = onBack,
            onPick = { picker.launch(arrayOf("application/pdf", "image/*")) },
            onCamera = { camera.launch(photoUri) },
        )

        is ImportUiState.Reading -> Column(
            Modifier.fillMaxSize().padding(spacing.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.solerunner.myhealth.design.rare.RareSpinner(size = 26)
            Spacer(Modifier.height(spacing.md))
            Text(
                stringResource(R.string.import_reading),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.import_ocr_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        is ImportUiState.Review -> ImportReview(
            state = current,
            viewModel = viewModel,
            onBack = { viewModel.reset() },
        )

        is ImportUiState.Saved -> LaunchedEffectOnce { onSaved(current.reportId) }

        is ImportUiState.Failed -> Column(
            Modifier.fillMaxSize().padding(spacing.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.solerunner.myhealth.design.rare.RareEmptyState(
                title = stringResource(R.string.import_failed),
                body = stringResource(R.string.import_failed_body),
                actionLabel = stringResource(R.string.common_retry),
                onAction = viewModel::reset,
            )
            Text(current.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

@Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
