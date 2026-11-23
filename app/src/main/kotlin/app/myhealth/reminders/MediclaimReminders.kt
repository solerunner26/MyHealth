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
import app.myhealth.domain.insurance.Mediclaims
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** Item 6 (mediclaim): system notifications 30, 7 and 1 day(s) before a policy ends, and on the day (10:00). */
class MediclaimReminders(private val context: Context, private val clock: Clock) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(stack: DataStack) {
        val today = LocalDate.now(clock)
        val policies = runCatching { stack.mediclaims.all() }.getOrElse { return }
        policies.forEach { p ->
            Mediclaims.REMINDER_DAYS.forEach { d -> cancel(p.id, d) }
            Mediclaims.reminderDates(p, today).forEach { (days, date) ->
                val at = date.atTime(AT).atZone(clock.zone).toInstant()
                if (at.isBefore(clock.instant())) return@forEach
                val pi = pending(p.id, days, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEach
                runCatching { alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi) }
            }
        }
    }

    fun cancelAll(policyId: String) = Mediclaims.REMINDER_DAYS.forEach { cancel(policyId, it) }
    private fun cancel(id: String, days: Int) { runCatching { pending(id, days, PendingIntent.FLAG_NO_CREATE)?.let { alarms?.cancel(it) } } }

    private fun pending(id: String, days: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, ("mc:$id:$days").hashCode(), Intent(context, MediclaimReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION = "app.myhealth.POLICY_RENEWAL"
        const val EXTRA_ID = "policy"
        const val CHANNEL = "insurance"
        val AT: LocalTime = LocalTime.of(10, 0)
        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, LocaleSupport.localized(context).getString(R.string.mc_notif_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }
}

/** Not exported. Generic text on the lock screen; insurer and date once unlocked. */
class MediclaimReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MediclaimReminders.ACTION) return
        val id = intent.getStringExtra(MediclaimReminders.EXTRA_ID) ?: return
        guarded {
            val stack = context.container.openNow() ?: return@guarded
            val p = stack.mediclaims.all().firstOrNull { it.id == id } ?: return@guarded
            if (!Notifications.canPost(context)) return@guarded
            MediclaimReminders.ensureChannel(context)
            val ctx = LocaleSupport.localized(context)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            val public = NotificationCompat.Builder(context, MediclaimReminders.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(ctx.getString(R.string.mc_notif_public)).build()
            val n = NotificationCompat.Builder(context, MediclaimReminders.CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.mc_notif_title, listOf(p.insurer, p.planName).filter { it.isNotBlank() }.joinToString(" · ")))
                .setContentText(ctx.getString(R.string.mc_notif_text, p.endDate.toString()))
                .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.mc_notif_text, p.endDate.toString())))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).setAutoCancel(true).setContentIntent(open).build()
            try { NotificationManagerCompat.from(context).notify(("mc:$id").hashCode(), n) } catch (_: SecurityException) { }
        }
    }
}
