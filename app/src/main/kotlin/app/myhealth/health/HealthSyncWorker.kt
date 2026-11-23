package app.myhealth.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.myhealth.container
import app.myhealth.domain.model.Relationship
import java.util.concurrent.TimeUnit

/** Every 6 hours: new readings from fitness devices (via Health Connect) become vitals, even if the app stays closed. */
class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        if (!c.preferences.state.value.healthConnectEnabled) return Result.success()
        val stack = c.openNow() ?: return Result.retry()
        val self = stack.members.all().firstOrNull { it.relationship == Relationship.Self } ?: return Result.success()
        return runCatching { HealthSync(HealthConnectSource(applicationContext, c.clock.zone), stack.activity, c.clock, stack.vitals).sync(self.id) }
            .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val WORK = "health-sync"
        fun schedule(context: Context) = runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build())
        }
        fun cancel(context: Context) = runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }
}
