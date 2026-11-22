package app.myhealth

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.myhealth.data.StorageOpener
import app.myhealth.data.StoragePaths
import app.myhealth.data.security.SoftwareKeyWrapper
import java.io.File

/** Robolectric application: software key + unencrypted SQLite (SQLCipher's native lib cannot load on the JVM). */
class TestApp : MyHealthApplication() {
    override fun createStorageOpener(): StorageOpener =
        StorageOpener(this, StoragePaths(File(noBackupFilesDir, "secure")), TestHooks.wrapper) { FrameworkSQLiteOpenHelperFactory() }
}

object TestHooks {
    var wrapper = SoftwareKeyWrapper()
    fun reset() { wrapper = SoftwareKeyWrapper() }
}
