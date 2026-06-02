package com.mgafk.app.service

import android.app.ActivityManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mgafk.app.data.repository.SessionRepository
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net: every ~15 min (the WorkManager minimum), verify that
 * [AfkService] is alive. If the user has live sessions (wantConnected=true)
 * but the service got killed without our self-restart catching it, post a
 * resume notification. We can't legally restart a foreground service from a
 * background worker on modern Android, but a notification gets the user back
 * in one tap.
 */
class AfkWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = SessionRepository(ctx)
        val pending = repo.loadSessions().count { it.wantConnected && it.cookie.isNotBlank() }
        if (pending == 0) {
            // Nothing to babysit. Clear any stale resume notif and cancel ourselves.
            cancelResumeNotification(ctx)
            WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK_NAME)
            return Result.success()
        }

        if (isAfkServiceRunning(ctx)) {
            // Service is alive: a "stopped" notif here would be a false positive
            // (getRunningServices is unreliable). Clear it instead of posting.
            cancelResumeNotification(ctx)
            return Result.success()
        }

        postResumeNotification(ctx, pending)
        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isAfkServiceRunning(ctx: Context): Boolean {
        val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        // getRunningServices is deprecated for third-party use but still
        // returns OUR services accurately, which is all we need here.
        val running = try {
            activityManager.getRunningServices(Int.MAX_VALUE)
        } catch (_: Exception) {
            return false
        }
        val target = AfkService::class.java.name
        return running.any { it.service.className == target }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "mgafk.afk_watchdog"

        /**
         * Enqueue the watchdog. Idempotent: if already scheduled, keeps the
         * existing schedule (no thrash from connect/disconnect storms).
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AfkWatchdogWorker>(
                15, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
