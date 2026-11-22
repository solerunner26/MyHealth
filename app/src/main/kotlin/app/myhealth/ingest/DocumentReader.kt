package app.myhealth.ingest

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.myhealth.data.vault.EncryptedVault
import app.myhealth.domain.model.AttachmentKind
import java.io.ByteArrayOutputStream

enum class IngestError { Unreadable, Unsupported, TooLarge, PasswordProtected, NoText, OcrUnavailable, PermissionDenied }

class IngestException(val error: IngestError, cause: Throwable? = null) : Exception(error.name, cause)

/** Raw document bytes plus what we could learn without parsing. */
class PickedDocument(val bytes: ByteArray, val displayName: String, val kind: AttachmentKind)

object DocumentSniffer {
    /** Type by magic bytes, never by the (untrusted) file name or MIME type. */
    fun kind(bytes: ByteArray): AttachmentKind? = when {
        bytes.size >= 5 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() -> AttachmentKind.Pdf
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> AttachmentKind.Jpeg
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> AttachmentKind.Png
        else -> null
    }
}

class DocumentReader(private val resolver: ContentResolver) {
    fun read(uri: Uri, fallbackName: String): PickedDocument {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackName
        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    total += n
                    if (total > EncryptedVault.MAX_BYTES) throw IngestException(IngestError.TooLarge)
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: throw IngestException(IngestError.Unreadable)
        } catch (e: IngestException) {
            throw e
        } catch (e: SecurityException) {
            throw IngestException(IngestError.PermissionDenied, e)
        } catch (e: Exception) {
            throw IngestException(IngestError.Unreadable, e)
        }
        val kind = DocumentSniffer.kind(bytes) ?: throw IngestException(IngestError.Unsupported)
        return PickedDocument(bytes, name.take(120), kind)
    }
}
