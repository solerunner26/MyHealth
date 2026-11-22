package app.myhealth.ui.common

import android.app.NotificationManager
import android.content.Context
import app.myhealth.container

/** Cryptographic erase: cancel reminders and notifications, destroy keys, overwrite and delete every file, reset prefs. */
object SecureErase {
    fun run(context: Context, onDone: () -> Unit = {}) {
        val c = context.container
        runCatching {
            c.stackOrNull?.let { stack ->
                kotlinx.coroutines.runBlocking { runCatching { stack.members.all().forEach { m -> stack.medicines.all(m.id).forEach { c.reminders.cancel(it.id) } } } }
            }
        }
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancelAll() }
        Share.wipe(context)
        c.eraseEverything(onDone)
    }
}
