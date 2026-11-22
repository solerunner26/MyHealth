package app.myhealth.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.myhealth.MainActivity
import app.myhealth.R
import app.myhealth.container
import app.myhealth.core.LocaleSupport
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.util.UuidIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant

/** Runs [block] off the main thread within the broadcast time limit; never throws. */
internal fun BroadcastReceiver.guarded(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeout(8_000) { block() }
        } catch (e: Throwable) {
            Log.w("MyHealth", "receiver failed", e)
        } finally {
            runCatching { pending.finish() }
        }
    }
}

/** Fires at a dose time: shows the notification and schedules the next dose. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val medicineId = intent.getStringExtra(ReminderScheduler.EXTRA_MEDICINE) ?: return
        val scheduled = Instant.ofEpochMilli(intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED, 0))
        val snooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_SNOOZE, false)
        guarded {
            val c = context.container
            val stack = c.openNow() ?: return@guarded // storage locked/unavailable: fail closed, no data shown
            val m = stack.medicines.get(medicineId) ?: return@guarded
            val member = stack.members.get(m.memberId) ?: return@guarded
            val scheduler = ReminderScheduler(context, c.clock)
            if (!snooze) scheduler.scheduleNext(m, scheduled)
            if (!m.remindersEnabled || m.stopped) return@guarded
            Notifications.showDose(context, m.id, m.name, listOf(m.strength, m.dose).filter { it.isNotBlank() }.joinToString(" "), member.name, scheduled)
        }
    }
}

/** Notification actions: Taken / Snooze 15 min / Skip. Each is logged against the scheduled slot. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getStringExtra(ReminderScheduler.EXTRA_MEDICINE) ?: return
        val scheduled = Instant.ofEpochMilli(intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED, 0))
        val status = when (intent.action) {
            ReminderScheduler.ACTION_TAKEN -> DoseStatus.Taken
            ReminderScheduler.ACTION_SNOOZE -> DoseStatus.Snoozed
            ReminderScheduler.ACTION_SKIP -> DoseStatus.Skipped
            else -> return
        }
        guarded {
            NotificationManagerCompat.from(context).cancel(ReminderScheduler.notificationId(medicineId))
            val c = context.container
            val stack = c.openNow() ?: return@guarded
            stack.medicines.logDose(DoseLog(UuidIds.next(), medicineId, scheduled, status, c.clock.instant()))
            if (status == DoseStatus.Snoozed) ReminderScheduler(context, c.clock).snooze(medicineId, scheduled)
        }
    }
}

/** Rebuilds all alarms after reboot, app update, clock or time-zone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        guarded {
            val c = context.container
            val stack = c.openNow() ?: return@guarded
            ReminderScheduler(context, c.clock).rescheduleAll(stack)
        }
    }

    companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCKED_BOOT_COMPLETED)
    }
}

object Notifications {
    fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showDose(context: Context, medicineId: String, name: String, detail: String, memberName: String, scheduled: Instant) {
        if (!canPost(context)) return
        val ctx = LocaleSupport.localized(context)
        fun action(a: String, label: Int, code: Int): NotificationCompat.Action {
            val i = Intent(context, ReminderActionReceiver::class.java).setAction(a)
                .putExtra(ReminderScheduler.EXTRA_MEDICINE, medicineId).putExtra(ReminderScheduler.EXTRA_SCHEDULED, scheduled.toEpochMilli())
            val pi = PendingIntent.getBroadcast(context, ReminderScheduler.requestCode(medicineId, false) xor code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action(0, ctx.getString(label), pi)
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        // Lock screen shows only a generic text; medicine and member names appear once unlocked.
        val public = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_DOSES).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_dose_public)).build()
        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_dose_title, name))
            .setContentText(if (detail.isBlank()) memberName else ctx.getString(R.string.notif_dose_text, detail, memberName))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setWhen(scheduled.toEpochMilli()).setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(action(ReminderScheduler.ACTION_TAKEN, R.string.notif_taken, 0x10))
            .addAction(action(ReminderScheduler.ACTION_SNOOZE, R.string.notif_snooze, 0x20))
            .addAction(action(ReminderScheduler.ACTION_SKIP, R.string.notif_skip, 0x30))
            .build()
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            NotificationManagerCompat.from(context).notify(ReminderScheduler.notificationId(medicineId), n)
        } catch (e: SecurityException) {
            Log.w("MyHealth", "notification permission revoked", e)
        }
    }

}
