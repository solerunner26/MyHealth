package app.myhealth.ui.member

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.domain.model.FamilyMember
import app.myhealth.ui.LocalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decrypted profile photo of [member], or null. Reloads when any photo changes. */
@Composable
fun rememberMemberPhoto(member: FamilyMember?): ImageBitmap? {
    val session = LocalSession.current
    val rev by session.photoRevision.collectAsStateWithLifecycle()
    var bmp by remember(member?.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(member?.id, rev) {
        bmp = member?.let { m ->
            withContext(Dispatchers.IO) { session.stack.photos.get(m.id)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() } }
        }
    }
    return bmp
}
