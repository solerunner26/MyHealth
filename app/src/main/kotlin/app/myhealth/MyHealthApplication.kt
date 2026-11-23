package app.myhealth

import android.app.Application
import app.myhealth.core.CrashLog
import app.myhealth.data.StorageOpener

open class MyHealthApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Test seam: Robolectric tests override this with a software key and an unencrypted SQLite factory. */
    protected open fun createStorageOpener(): StorageOpener = StorageOpener(this)

    /** Test seam: the welcome splash is shown once per app start; JVM UI tests skip it (one test covers it). */
    open val showSplash: Boolean = true

    /** Test seam: JVM tests never reach the network. */
    open val updatesEnabled: Boolean = true

    override fun onCreate() {
        super.onCreate()
        val crashLog = CrashLog.forContext(this, BuildInfo.versionName(this) + " build " + app.myhealth.core.Updater.installedVersionCode(this))
        crashLog.install()
        container = AppContainer(this, crashLog, ::createStorageOpener)
        // PdfBox needs the application context once (glyph lists/fonts from its assets); cheap, no I/O until used.
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this) }
        container.start()
    }
}

val android.content.Context.container: AppContainer get() = (applicationContext as MyHealthApplication).container
