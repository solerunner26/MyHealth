package app.myhealth

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.data.security.SecureFailure
import app.myhealth.domain.analytics.DoseSchedule
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Relationship
import app.myhealth.reminders.ReminderActionReceiver
import app.myhealth.reminders.ReminderReceiver
import app.myhealth.reminders.ReminderScheduler
import app.myhealth.reminders.RescheduleReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ReminderTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val alarms get() = shadowOf(ctx.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(ctx.getSystemService(NotificationManager::class.java))
    private lateinit var med: Medicine

    @Before fun setUp() {
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val stack = runBlocking { ctx.container.openNow() }!!
        runBlocking {
            stack.members.upsert(FamilyMember("m1", "Asha", Relationship.Self))
            med = Medicine("med1", "m1", "Metformin", "500 mg", frequency = Frequency.TwiceDaily, startDate = LocalDate.now().minusDays(3),
                reminderTimes = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), remindersEnabled = true)
            stack.medicines.upsert(med)
        }
        ctx.container.reminders.ensureChannel()
    }

    private fun awaitCondition(c: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); if (c()) return; Thread.sleep(20) }
        error("timeout")
    }

    @Test fun schedulesNextDoseAndRebuildsAfterBoot() {
        val stack = ctx.container.stackOrNull!!
        runBlocking { ctx.container.reminders.rescheduleAll(stack) }
        val expected = DoseSchedule.next(med, ctx.container.clock.instant(), ctx.container.clock.zone)!!
        val next = alarms.peekNextScheduledAlarm()
        assertNotNull(next); assertEquals(expected.toEpochMilli(), next!!.triggerAtTime)

        // Reboot wipes alarms; BOOT_COMPLETED rebuilds them from the database.
        alarms.scheduledAlarms.toList().forEach { a -> a.operation?.let { ctx.getSystemService(AlarmManager::class.java).cancel(it) } }
        assertTrue(alarms.scheduledAlarms.isEmpty())
        RescheduleReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitCondition { alarms.scheduledAlarms.isNotEmpty() }
        assertEquals(expected.toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)

        // Turning reminders off and rescheduling leaves no alarm for that medicine.
        runBlocking { stack.medicines.upsert(med.copy(remindersEnabled = false)); ctx.container.reminders.rescheduleAll(stack) }
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test fun firingShowsPrivateNotificationAndChainsNextDose() {
        // A real alarm fires at its slot: use the most recent past slot, so "next after slot" == "next after now".
        val now = ctx.container.clock.instant()
        val scheduled = DoseSchedule.occurrences(med, now.minusSeconds(86_400), now, ctx.container.clock.zone).last()
        ReminderReceiver().onReceive(ctx, Intent(ReminderScheduler.ACTION_FIRE).putExtra(ReminderScheduler.EXTRA_MEDICINE, "med1").putExtra(ReminderScheduler.EXTRA_SCHEDULED, scheduled.toEpochMilli()))
        awaitCondition { notifications.allNotifications.isNotEmpty() }
        val n = notifications.allNotifications.single()
        assertEquals(3, n.actions.size)
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, n.visibility)
        assertNotNull(n.publicVersion)
        assertEquals(ctx.getString(R.string.notif_dose_title, "Metformin"), shadowOf(n).contentTitle)
        // the following dose is scheduled
        val following = DoseSchedule.next(med, scheduled, ctx.container.clock.zone)!!
        awaitCondition { alarms.peekNextScheduledAlarm()?.triggerAtTime == following.toEpochMilli() }
    }

    @Test fun takenAndSnoozeActionsAreLogged() {
        val stack = ctx.container.stackOrNull!!
        val slot = Instant.now().minusSeconds(60)
        fun act(a: String) = ReminderActionReceiver().onReceive(ctx, Intent(a).putExtra(ReminderScheduler.EXTRA_MEDICINE, "med1").putExtra(ReminderScheduler.EXTRA_SCHEDULED, slot.toEpochMilli()))
        act(ReminderScheduler.ACTION_SNOOZE)
        awaitCondition { runBlocking { stack.medicines.doseLogs("m1", Instant.EPOCH) }.isNotEmpty() }
        fun snoozeAlarm() = alarms.scheduledAlarms.firstOrNull { it.triggerAtTime - System.currentTimeMillis() in (14 * 60_000L)..(16 * 60_000L) }
        awaitCondition { snoozeAlarm() != null }
        // an unlock re-runs the full reschedule: the user's snooze must survive it
        runBlocking { ctx.container.reminders.rescheduleAll(stack) }
        assertNotNull(snoozeAlarm())
        act(ReminderScheduler.ACTION_TAKEN)
        awaitCondition { runBlocking { stack.medicines.doseLogs("m1", Instant.EPOCH) }.single().status == DoseStatus.Taken }
    }

    @Test fun receiverWithLockedStorageDoesNothingAndDoesNotCrash() {
        TestHooks.wrapper.failWith = SecureFailure.KeystoreUnavailable
        try {
            ctx.container.eraseStateForTest()
            ReminderReceiver().onReceive(ctx, Intent(ReminderScheduler.ACTION_FIRE).putExtra(ReminderScheduler.EXTRA_MEDICINE, "med1").putExtra(ReminderScheduler.EXTRA_SCHEDULED, 1L))
            Thread.sleep(300)
            assertTrue(notifications.allNotifications.isEmpty())
        } finally { TestHooks.wrapper.failWith = null }
    }
}
