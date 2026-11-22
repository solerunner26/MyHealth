package app.myhealth.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Writes uncaught exceptions to app-private storage (no network, no SDK).
 * Only the stack trace and device/app metadata are written: never health data.
 */
class CrashLog(private val dir: File, private val appVersion: String) {

    val file: File get() = File(dir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(threadName: String, error: Throwable) {
        dir.mkdirs()
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val text = buildString {
            appendLine("MyHealth error report")
            appendLine("time=${Instant.now()}")
            appendLine("app=$appVersion sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread=$threadName")
            appendLine()
            append(redact(sw.toString()))
        }
        file.writeText(text.take(MAX_CHARS))
    }

    fun hasReport(): Boolean = runCatching { file.exists() && file.length() > 0 }.getOrDefault(false)

    fun clear() { runCatching { file.delete() } }

    companion object {
        const val FILE_NAME = "last_error.txt"
        private const val MAX_CHARS = 64_000

        fun forContext(context: Context, version: String) = CrashLog(File(context.filesDir, "errors"), version)

        /** Exception messages can embed user input (e.g. a parse failure quoting a value); strip digits after ':' markers. */
        internal fun redact(trace: String): String = trace.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith("at ")) line
            else line.replace(Regex("""(?<=: ).*"""), "<redacted>")
        }
    }
}
