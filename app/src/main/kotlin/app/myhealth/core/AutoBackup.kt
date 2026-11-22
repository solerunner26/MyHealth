package app.myhealth.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.myhealth.BuildInfo
import app.myhealth.container
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AutoBackupFrequency { Off, Daily, Weekly }

/**
 * Item 3: scheduled encrypted backups to a folder the user picked (SD card, USB drive, any document provider).
 * The backup password is kept AES-GCM encrypted under a non-exportable Android Keystore key so the job can run
 * unattended; the backup file itself is the same password-encrypted `.myhealth` format as manual backups.
 */
object AutoBackup {
    private const val WORK = "auto-backup"
    private const val ALIAS = "myhealth_autobackup"
    const val KEEP = 5
    const val PREFIX = "MyHealth-auto-"

    fun schedule(context: Context, f: AutoBackupFrequency) = runCatching {
        val wm = WorkManager.getInstance(context)
        if (f == AutoBackupFrequency.Off) { wm.cancelUniqueWork(WORK); return@runCatching }
        val days = if (f == AutoBackupFrequency.Daily) 1L else 7L
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(context: Context) = runCatching { WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build()) }

    // ---- password sealed by the Keystore --------------------------------------------------------------------------
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    fun seal(password: CharArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        try { return Base64.getEncoder().encodeToString(c.iv + c.doFinal(bytes)) } finally { bytes.fill(0) }
    }

    fun unseal(sealed: String): CharArray {
        val raw = Base64.getDecoder().decode(sealed)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
        val plain = c.doFinal(raw, 12, raw.size - 12)
        try { return String(plain, Charsets.UTF_8).toCharArray() } finally { plain.fill(0) }
    }

    fun forget() = runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }

    // ---- folder handling ---------------------------------------------------------------------------------------------
    fun fileName(now: Instant, zone: ZoneId): String = PREFIX + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(zone).format(now) + ".myhealth"

    /** Our own automatic files in the folder, oldest first (manual backups and other files are never touched). */
    fun ours(names: List<String>): List<String> = names.filter { it.startsWith(PREFIX) && it.endsWith(".myhealth") }.sorted()

    /** Names to delete so only the newest [KEEP] automatic backups remain. */
    fun toPrune(names: List<String>): List<String> = ours(names).let { it.dropLast(KEEP) }

    fun folderLabel(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment.orEmpty()
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val prefs = c.preferences
        val tree = prefs.autoBackupFolder()?.let(Uri::parse) ?: return Result.failure()
        val sealed = prefs.autoBackupSecret() ?: return Result.failure()
        val stack = c.openNow() ?: return Result.retry()
        val resolver = applicationContext.contentResolver
        val ok = runCatching {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = AutoBackup.fileName(c.clock.instant(), c.clock.zone)
            val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: error("cannot create file")
            val pw = AutoBackup.unseal(sealed)
            try {
                resolver.openOutputStream(doc, "w")!!.use { out -> stack.backups(applicationContext).export(out, pw, c.clock.millis(), BuildInfo.versionName(applicationContext)) }
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, doc) }; throw e
            } finally { pw.fill(' ') }
            // keep only the newest automatic backups
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val entries = mutableMapOf<String, String>()
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
                while (cur.moveToNext()) entries[cur.getString(1)] = cur.getString(0)
            }
            AutoBackup.toPrune(entries.keys.toList()).forEach { n -> runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, entries.getValue(n))) } }
        }.isSuccess
        prefs.setAutoBackupResult(c.clock.millis(), ok)
        return if (ok) Result.success() else Result.retry()
    }
}
