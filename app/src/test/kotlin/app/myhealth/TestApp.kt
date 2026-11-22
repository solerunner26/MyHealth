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
    override val showSplash: Boolean get() = TestHooks.splash
    override val updatesEnabled: Boolean get() = false
}

object TestHooks {
    var wrapper = SoftwareKeyWrapper()
    var splash = false
    fun reset() { wrapper = SoftwareKeyWrapper() }
}
