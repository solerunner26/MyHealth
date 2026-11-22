package app.myhealth.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.myhealth.R
import app.myhealth.data.DataStack
import app.myhealth.domain.analytics.DoseSchedule
import app.myhealth.domain.model.Medicine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Local dose reminders. One pending alarm per medicine (its next dose); when it fires the notification is shown and
 * the following dose is scheduled. Everything is rebuilt from the database on boot, app update and time-zone change,
 * so alarms can never drift from the stored schedule.
 *
 * Exactness: API 26–30 use exact while-idle alarms. API 31+ uses inexact while-idle alarms (no SCHEDULE_EXACT_ALARM
 * permission requested; see DECISIONS D-020), which Doze may defer by a few minutes.
 */
class ReminderScheduler(private val context: Context, private val clock: Clock) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ctx = app.myhealth.core.LocaleSupport.localized(context)
        val ch = NotificationChannel(CHANNEL_DOSES, ctx.getString(R.string.notif_channel_doses), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.notif_channel_doses_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(ch)
    }

    /** Cancels and re-creates every alarm from the database. Safe to call any number of times. */
    suspend fun rescheduleAll(stack: DataStack): Int {
        val today = LocalDate.now(clock)
        val meds = runCatching { stack.medicines.allMembersActive(today.minusDays(1)) }.getOrElse { return 0 }
        var n = 0
        runCatching { stack.members.all() }.getOrDefault(emptyList()).forEach { member ->
            // Only the regular next-dose alarm is rebuilt; a pending snooze the user asked for must survive an unlock.
            runCatching { stack.medicines.all(member.id) }.getOrDefault(emptyList()).forEach { cancelRegular(it.id) }
        }
        meds.forEach { m -> if (scheduleNext(m, clock.instant())) n++ }
        return n
    }

    /** Schedules [m]'s next dose after [after]; returns false when it has none (finished, reminders off). */
    fun scheduleNext(m: Medicine, after: Instant): Boolean {
        if (!m.remindersEnabled || m.stopped) { cancel(m.id); return false }
        val next = DoseSchedule.next(m, after, clock.zone) ?: run { cancelRegular(m.id); return false }
        set(m.id, next, next, snooze = false)
        return true
    }

    fun snooze(medicineId: String, scheduledAt: Instant, minutes: Long = 15) =
        set(medicineId, clock.instant().plusSeconds(minutes * 60), scheduledAt, snooze = true)

    /** Cancels the next-dose alarm and any snooze (medicine deleted, stopped or reminders turned off). */
    fun cancel(medicineId: String) = listOf(false, true).forEach { cancelOne(medicineId, it) }

    fun cancelRegular(medicineId: String) = cancelOne(medicineId, false)

    private fun cancelOne(medicineId: String, snooze: Boolean) {
        runCatching { pending(medicineId, Instant.EPOCH, snooze, PendingIntent.FLAG_NO_CREATE)?.let { alarms?.cancel(it) } }
    }

    private fun set(medicineId: String, fireAt: Instant, scheduledAt: Instant, snooze: Boolean) {
        val am = alarms ?: return
        val pi = pending(medicineId, scheduledAt, snooze, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pi)
        } catch (e: SecurityException) {
            Log.w("MyHealth", "alarm refused", e)
            runCatching { am.set(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pi) }
        }
    }

    private fun pending(medicineId: String, scheduledAt: Instant, snooze: Boolean, flags: Int): PendingIntent? {
        val i = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_MEDICINE, medicineId)
            putExtra(EXTRA_SCHEDULED, scheduledAt.toEpochMilli())
            putExtra(EXTRA_SNOOZE, snooze)
        }
        return PendingIntent.getBroadcast(context, requestCode(medicineId, snooze), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_DOSES = "doses"
        const val ACTION_FIRE = "app.myhealth.reminder.FIRE"
        const val ACTION_TAKEN = "app.myhealth.reminder.TAKEN"
        const val ACTION_SNOOZE = "app.myhealth.reminder.SNOOZE"
        const val ACTION_SKIP = "app.myhealth.reminder.SKIP"
        const val EXTRA_MEDICINE = "medicine"
        const val EXTRA_SCHEDULED = "scheduled"
        const val EXTRA_SNOOZE = "snooze"
        fun requestCode(medicineId: String, snooze: Boolean) = medicineId.hashCode() * 2 + if (snooze) 1 else 0
        fun notificationId(medicineId: String) = medicineId.hashCode()
    }
}
