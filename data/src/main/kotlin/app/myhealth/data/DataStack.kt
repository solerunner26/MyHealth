package app.myhealth.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.myhealth.data.db.MyHealthDatabase
import app.myhealth.data.repository.ActivityRepositoryImpl
import app.myhealth.data.repository.MedicineRepositoryImpl
import app.myhealth.data.repository.MemberRepositoryImpl
import app.myhealth.data.repository.RecordRepositoryImpl
import app.myhealth.data.repository.TreatmentRepositoryImpl
import app.myhealth.data.repository.VitalRepositoryImpl
import app.myhealth.data.security.AndroidKeystoreWrapper
import app.myhealth.data.security.KeyManager
import app.myhealth.data.security.KeyWrapper
import app.myhealth.data.security.SecureDelete
import app.myhealth.data.security.SecureFailure
import app.myhealth.data.security.SecureStorageException
import app.myhealth.data.vault.EncryptedVault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/** Opened storage: every repository, backed by one encrypted database and one encrypted vault. */
class DataStack internal constructor(val database: MyHealthDatabase, val vault: EncryptedVault, private val vaultDir: java.io.File) {
    fun backups(context: Context) = app.myhealth.data.backup.BackupManager(context, this, vaultDir)
    val members = MemberRepositoryImpl(database, vault)
    val records = RecordRepositoryImpl(database, vault)
    val vitals = VitalRepositoryImpl(database)
    val medicines = MedicineRepositoryImpl(database)
    val treatments = TreatmentRepositoryImpl(database)
    val activity = ActivityRepositoryImpl(database)
    val photos = PhotoStore(vault)
    fun close() = database.close()
}

/** Profile photos: encrypted in the vault under "photo-<memberId>" (no schema change; included in backups). */
class PhotoStore(private val vault: EncryptedVault) {
    private fun name(memberId: String): String { require(memberId.matches(Regex("[A-Za-z0-9-]{1,64}"))); return "photo-$memberId" }
    fun put(memberId: String, jpeg: ByteArray) { require(jpeg.size <= MAX_BYTES); vault.put(name(memberId), jpeg) }
    fun get(memberId: String): ByteArray? = runCatching { if (vault.exists(name(memberId))) vault.get(name(memberId)) else null }.getOrNull()
    fun delete(memberId: String) { runCatching { if (vault.exists(name(memberId))) vault.delete(name(memberId)) } }
    companion object { const val MAX_BYTES = 2 * 1024 * 1024 }
}

/** Where things live. All under no-backup app-private storage. */
class StoragePaths(val root: File) {
    val database = File(root, "myhealth.db")
    val keys = File(root, "keys/keys.bin")
    val vault = File(root, "vault")
    fun exists() = database.exists() || (vault.listFiles()?.isNotEmpty() == true)
    companion object { fun of(context: Context) = StoragePaths(File(context.noBackupFilesDir, "secure")) }
}

/**
 * Opens storage fail-closed: key unwrap → SQLCipher open (with key check and migrations) → vault.
 * Any failure is a [SecureStorageException] with a precise [SecureFailure]; nothing is regenerated or deleted.
 */
class StorageOpener(
    private val context: Context,
    private val paths: StoragePaths = StoragePaths.of(context),
    private val wrapper: KeyWrapper = AndroidKeystoreWrapper(),
    /** Test seam: production uses SQLCipher with the unwrapped passphrase. */
    private val helperFactory: (ByteArray) -> SupportSQLiteOpenHelper.Factory = { pass -> sqlCipherFactory(pass) },
) {
    fun open(): DataStack {
        val keys = KeyManager(wrapper, paths.keys).loadOrCreate(encryptedDataExists = paths.exists())
        paths.root.mkdirs()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(paths.database.absolutePath)
            .callback(MyHealthDatabase.callback())
            // `name` is already an absolute path inside noBackupFilesDir. Do NOT also set noBackupDirectory(true):
            // the framework helper would then nest the path a second time and the real file would escape erase.
            .build()
        val helper = helperFactory(keys.dbPassphrase).create(config)
        val db = MyHealthDatabase(helper)
        try {
            db.open()
        } catch (e: SecureStorageException) {
            db.close(); throw e
        }
        return DataStack(db, EncryptedVault(paths.vault, keys.vaultKey), paths.vault)
    }

    /** Cryptographic erase: destroy keys first (renders data unreadable), then overwrite and delete files. */
    fun eraseEverything() {
        KeyManager(wrapper, paths.keys).destroy()
        runCatching { context.deleteDatabase(paths.database.absolutePath) }
        listOf(paths.database, File(paths.database.path + "-wal"), File(paths.database.path + "-shm"), File(paths.database.path + "-journal"))
            .filter { it.exists() }.forEach { SecureDelete.overwriteAndDelete(it) }
        if (paths.root.exists()) SecureDelete.overwriteAndDelete(paths.root)
    }

    companion object {
        private val loaded by lazy {
            runCatching { System.loadLibrary("sqlcipher") }.isSuccess
        }

        fun sqlCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
            if (!loaded) throw SecureStorageException(SecureFailure.DatabaseUnavailable)
            return SupportOpenHelperFactory(passphrase)
        }

        /** Unencrypted factory for JVM tests only. */
        fun frameworkFactory(): SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
    }
}
