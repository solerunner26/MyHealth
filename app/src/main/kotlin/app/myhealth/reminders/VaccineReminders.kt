package app.myhealth.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.myhealth.MainActivity
import app.myhealth.R
import app.myhealth.container
import app.myhealth.core.LocaleSupport
import app.myhealth.data.DataStack
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * One local alarm per vaccine that is due in the future, at 09:00 on the due date. Rebuilt from the database whenever
 * vaccinations change and after boot/time changes (same receiver as dose reminders). Inexact alarms: no permission.
 */
class VaccineReminders(private val context: Context, private val clock: Clock) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(stack: DataStack) {
        val today = LocalDate.now(clock)
        val pending = runCatching { stack.vaccinations.allMembersPending() }.getOrElse { return }
        pending.forEach { v ->
            val due = v.dueDate ?: return@forEach
            val at = due.atTime(REMIND_AT).atZone(clock.zone).toInstant()
            if (due.isBefore(today) || at.isBefore(clock.instant())) { cancel(v.id); return@forEach }
            val pi = pending(v.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEach
            runCatching { alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi) }
        }
    }

    fun cancel(id: String) { runCatching { pending(id, PendingIntent.FLAG_NO_CREATE)?.let { alarms?.cancel(it) } } }

    private fun pending(id: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, ("vax:$id").hashCode(), Intent(context, VaccineReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION = "app.myhealth.VACCINE_DUE"
        const val EXTRA_ID = "vaccination"
        const val CHANNEL = "vaccines"
        val REMIND_AT: LocalTime = LocalTime.of(9, 0)

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val ctx = LocaleSupport.localized(context)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_vax), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }
}

/** Not exported: only our own alarm reaches it. Shows a private notification (generic text on the lock screen). */
class VaccineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VaccineReminders.ACTION) return
        val id = intent.getStringExtra(VaccineReminders.EXTRA_ID) ?: return
        guarded {
            val stack = context.container.openNow() ?: return@guarded
            val v = stack.vaccinations.allMembersPending().firstOrNull { it.id == id } ?: return@guarded
            val member = stack.members.get(v.memberId) ?: return@guarded
            if (!Notifications.canPost(context)) return@guarded
            VaccineReminders.ensureChannel(context)
            val ctx = LocaleSupport.localized(context)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            val public = NotificationCompat.Builder(context, VaccineReminders.CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_vax_public)).build()
            val label = listOf(v.name, v.dose).filter { it.isNotBlank() }.joinToString(" · ")
            val n = NotificationCompat.Builder(context, VaccineReminders.CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_vax_title, label))
                .setContentText(ctx.getString(R.string.notif_vax_text, member.name))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open).build()
            try { NotificationManagerCompat.from(context).notify(("vax:$id").hashCode(), n) } catch (_: SecurityException) { }
        }
    }
}
