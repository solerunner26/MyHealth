package app.myhealth.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.myhealth.data.security.SecureDelete
import java.io.File

/**
 * Hands a file to the Android share sheet. The copy lives in cache/share (FileProvider) and is wiped on the next
 * app start; the UI warns that a shared copy is not encrypted. Nothing is ever sent by the app itself.
 */
object Share {
    private fun dir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    fun safeName(name: String): String = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "file" }

    fun write(context: Context, name: String, bytes: ByteArray): File = File(dir(context), safeName(name)).apply { writeBytes(bytes) }

    fun launch(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Called at startup: previously shared plaintext copies do not linger. */
    fun wipe(context: Context) { runCatching { File(context.cacheDir, "share").listFiles()?.forEach { SecureDelete.overwriteAndDelete(it) } }
        runCatching { File(context.cacheDir, "camera").listFiles()?.forEach { SecureDelete.overwriteAndDelete(it) } } }

    fun mimeFor(kind: app.myhealth.domain.model.AttachmentKind) = when (kind) {
        app.myhealth.domain.model.AttachmentKind.Pdf -> "application/pdf"
        app.myhealth.domain.model.AttachmentKind.Jpeg -> "image/jpeg"
        app.myhealth.domain.model.AttachmentKind.Png -> "image/png"
    }
}
