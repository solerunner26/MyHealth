package com.solerunner.myhealth.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.solerunner.myhealth.MyHealthApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Re-arms every medicine reminder after a reboot or an app update. Alarms do not survive either, so this
 * receiver is what keeps the schedule honest without any server involvement.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val application = context.applicationContext as? MyHealthApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(6_000) { application.container.ready.first { it } }
                if (application.container.ready.value) {
                    application.container.reminders.scheduleAll(application.container.medicines.all())
                }
            } catch (_: Exception) {
                // Nothing to reschedule, or the vault is not readable yet; the next launch will retry.
            } finally {
                pending.finish()
            }
        }
    }
}
