package app.myhealth.ui.member

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.myhealth.domain.model.FamilyMember

/** Decrypted profile photo of [member] (null until item 8 adds photos, or when none is set). */
@Composable
fun rememberMemberPhoto(member: FamilyMember?): ImageBitmap? {
    var bmp by remember(member?.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(member?.id) { bmp = null }
    return bmp
}
