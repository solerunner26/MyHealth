package com.solerunner.myhealth.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.solerunner.myhealth.MainActivity
import com.solerunner.myhealth.R
import com.solerunner.myhealth.domain.model.Medicine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Medicine reminders, scheduled on-device with [AlarmManager].
 *
 * Why alarms rather than a polling worker: a dose reminder is a single, precise moment per day, and an
 * alarm survives Doze without keeping the process alive. Exact alarms are used when the platform allows
 * them; otherwise the request degrades to an inexact alarm instead of failing. Nothing leaves the phone.
 */
class MedicineReminders(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.meds_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.meds_reminders_note) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /** Schedules every reminder time of [medicine]; cancels first so edits never duplicate alarms. */
    fun schedule(medicine: Medicine) {
        if (medicine.id == 0L) return
        cancel(medicine.id)
        if (!medicine.reminderEnabled || !medicine.isActive) return
        ensureChannel()
        val scheduled = mutableListOf<String>()
        medicine.reminderTimes.mapNotNull(::parseTime).forEach { time ->
            val label = time.toString()
            val trigger = nextTrigger(medicine, time)
            if (trigger != Long.MAX_VALUE) {
                setAlarm(medicine.id, label, trigger)
                scheduled += label
            }
        }
        if (scheduled.isNotEmpty()) storeScheduled(medicine.id, scheduled)
    }

    /**
     * Cancels the alarms that are actually armed for this medicine. The labels are persisted, because
     * an edit can change the reminder times and the old request codes would otherwise leak.
     */
    fun cancel(medicineId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val labels = scheduledLabels(medicineId)
        labels.forEach { label -> alarmManager.cancel(pendingIntent(medicineId, label)) }
        prefs.edit().remove(KEY_PREFIX + medicineId).apply()
    }

    private fun storeScheduled(medicineId: Long, labels: List<String>) {
        prefs.edit().putString(KEY_PREFIX + medicineId, labels.joinToString(",")).apply()
    }

    private fun scheduledLabels(medicineId: Long): List<String> =
        prefs.getString(KEY_PREFIX + medicineId, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private val prefs by lazy {
        context.getSharedPreferences("medicine_reminders", Context.MODE_PRIVATE)
    }

    fun scheduleAll(medicines: List<Medicine>) = medicines.forEach(::schedule)

    private fun setAlarm(medicineId: Long, timeLabel: String, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(medicineId, timeLabel)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }.onFailure {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun nextTrigger(medicine: Medicine, time: LocalTime): Long {
        val now = LocalDateTime.now()
        var date: LocalDate = now.toLocalDate()
        var candidate = date.atTime(time)
        if (!candidate.isAfter(now)) {
            date = date.plusDays(1)
            candidate = date.atTime(time)
        }
        // Do not schedule beyond the course end date.
        val end = medicine.endDate
        if (end != null && date.isAfter(end)) return Long.MAX_VALUE
        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(medicineId: Long, timeLabel: String): PendingIntent {
        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            action = MedicineReminderReceiver.ACTION_REMIND
            putExtra(MedicineReminderReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineReminderReceiver.EXTRA_TIME_LABEL, timeLabel)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(medicineId, timeLabel),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(medicineId: Long, timeLabel: String): Int =
        (medicineId.toString() + timeLabel).hashCode()

    private fun parseTime(raw: String): LocalTime? = runCatching {
        val parts = raw.trim().split(':')
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

    companion object {
        const val CHANNEL_ID = "medicine_reminders"
        private const val KEY_PREFIX = "scheduled_"

        /** Posts the reminder notification for one medicine. Called by the receiver. */
        fun notify(context: Context, medicineId: Long, medicineName: String?, strength: String?, timeLabel: String) {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (!manager.areNotificationsEnabled()) return
            val open = PendingIntent.getActivity(
                context,
                medicineId.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title = medicineName ?: context.getString(R.string.meds_title)
            val text = listOfNotNull(strength, context.getString(R.string.meds_reminder_times), timeLabel)
                .joinToString(" · ")
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { manager.notify((medicineId.toString() + timeLabel).hashCode(), notification) }
        }
    }
}
