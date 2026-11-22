package app.myhealth

import android.app.Application
import app.myhealth.core.CrashLog

class MyHealthApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val crashLog = CrashLog.forContext(this, BuildInfo.versionName(this))
        crashLog.install()
        container = AppContainer(this, crashLog)
    }
}
