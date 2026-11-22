package com.solerunner.myhealth.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.solerunner.myhealth.MyHealthApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/**
 * Fires one dose reminder and schedules the next occurrence. Registered in the manifest, so the alarm
 * still delivers after the process was killed.
 */
class MedicineReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val medicineId = intent.getLongExtra(EXTRA_MEDICINE_ID, 0L)
        val timeLabel = intent.getStringExtra(EXTRA_TIME_LABEL) ?: return
        if (medicineId == 0L) return
        val application = context.applicationContext as? MyHealthApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // An alarm can cold-start the process; wait for the encrypted database to finish opening.
                withTimeoutOrNull(6_000) { application.container.ready.first { it } }
                if (!application.container.ready.value) return@launch
                val medicine = application.container.medicines.byId(medicineId)
                if (medicine != null && medicine.reminderEnabled && medicine.isActive) {
                    MedicineReminders.notify(context, medicine.id, medicine.name, medicine.strength, timeLabel)
                    application.container.reminders.schedule(medicine)
                }
            } catch (_: Exception) {
                // A reminder that cannot be delivered must never crash the process.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.solerunner.myhealth.action.MEDICINE_REMIND"
        const val EXTRA_MEDICINE_ID = "medicine_id"
        const val EXTRA_TIME_LABEL = "time_label"
    }
}
