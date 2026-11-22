package app.myhealth.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.myhealth.R
import app.myhealth.StorageState
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthErrorState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.data.DataStack
import app.myhealth.data.security.SecureFailure

/** Shows the app only once encrypted storage is open; otherwise a designed loading or fail-closed error state. */
@Composable
fun StorageGate(state: StorageState, onRetry: () -> Unit, onErase: () -> Unit, content: @Composable (DataStack) -> Unit) {
    when (state) {
        is StorageState.Ready -> content(state.stack)
        StorageState.Opening -> Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("storageOpening")) {
            MyHealthLoadingState(stringResource(R.string.storage_opening))
        }
        is StorageState.Failed -> StorageErrorScreen(state.failure, onRetry, onErase)
    }
}

@Composable
fun StorageErrorScreen(failure: SecureFailure, onRetry: () -> Unit, onErase: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val (title, body) = when (failure) {
        SecureFailure.KeystoreUnavailable -> R.string.storage_keystore_title to R.string.storage_keystore_body
        SecureFailure.KeyInvalidated -> R.string.storage_invalidated_title to R.string.storage_invalidated_body
        SecureFailure.KeyMaterialCorrupt -> R.string.storage_corrupt_title to R.string.storage_corrupt_body
        SecureFailure.DatabaseUnavailable -> R.string.storage_db_title to R.string.storage_db_body
        SecureFailure.NewerSchema -> R.string.storage_newer_title to R.string.storage_newer_body
    }
    // Erasing is offered only where the data can never be read again or the user cannot fix it otherwise.
    val canErase = failure != SecureFailure.NewerSchema
    Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("storageError")) {
        MyHealthErrorState(
            title = stringResource(title), message = stringResource(body),
            actionLabel = stringResource(R.string.action_retry), onAction = onRetry,
            secondaryLabel = if (canErase) stringResource(R.string.storage_action_erase) else null,
            onSecondary = if (canErase) ({ confirm = true }) else null,
        )
    }
    if (confirm) {
        MyHealthDialog(
            title = stringResource(R.string.storage_erase_title), text = stringResource(R.string.storage_erase_body),
            confirmLabel = stringResource(R.string.storage_erase_confirm), onConfirm = { confirm = false; onErase() },
            dismissLabel = stringResource(R.string.action_cancel), onDismiss = { confirm = false }, icon = MyHealthIcons.Erase,
        )
    }
}
