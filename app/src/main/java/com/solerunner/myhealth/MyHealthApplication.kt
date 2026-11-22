package com.solerunner.myhealth

import android.app.Application
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
        container = AppContainer(this)
        container.start()
        container.reminders.ensureChannel()
    }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}
