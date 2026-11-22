package app.myhealth

import android.content.Context
import app.myhealth.core.CrashLog

/** Manual dependency graph. Constructed once in [MyHealthApplication]; no reflection, no codegen. */
class AppContainer(val context: Context, val crashLog: CrashLog)
