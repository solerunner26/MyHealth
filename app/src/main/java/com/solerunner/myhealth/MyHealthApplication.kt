package com.solerunner.myhealth

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File
import java.time.Instant
import com.solerunner.myhealth.di.AppContainer

/**
 * Owns the process-wide [AppContainer]: the encrypted database, the Keystore vault, settings and the
 * document pipeline. Opening the database starts immediately on a background dispatcher.
 */
class MyHealthApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        container = AppContainer(this)
        container.start()
        container.reminders.ensureChannel()
        container.exporter.clearCache()
    }

    override fun onTerminate() {
        runCatching { container.close() }
        super.onTerminate()
    }

    /**
     * Writes the next fatal exception to app-private storage before the process dies, so the user can
     * share it from Settings without adb or a crash-reporting SDK. Nothing is uploaded anywhere.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val directory = File(filesDir, "diagnostics").apply { mkdirs() }
                val report = buildString {
                    appendLine("My Health crash")
                    appendLine("time: ${Instant.now()}")
                    appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine()
                    append(Log.getStackTraceString(error))
                }
                File(directory, "last-crash.txt").writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
