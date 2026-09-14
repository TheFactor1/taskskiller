package com.thefactor1.taskskiller.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.thefactor1.taskskiller.R
import com.thefactor1.taskskiller.data.Rule
import com.thefactor1.taskskiller.data.RuleStore
import com.thefactor1.taskskiller.data.RunLog
import com.thefactor1.taskskiller.kill.KillBackends
import com.thefactor1.taskskiller.launch.AppLauncher
import com.thefactor1.taskskiller.ui.MainActivity
import com.thefactor1.taskskiller.util.PackageUtil
import com.thefactor1.taskskiller.util.ScreenUtil
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Does the actual kill-wait-relaunch work.
 *
 * It runs as a foreground service for the duration of a job so the system does
 * not reap the process mid-restart, and holds a partial wake lock so the CPU
 * stays up while the display is off.
 */
class RestartService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val pendingJobs = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within a few seconds of being started, before any work.
        promoteToForeground()

        when (intent?.action) {
            ACTION_RUN_RULE -> {
                val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
                if (ruleId >= 0) enqueue(ruleId, manual = intent.getBooleanExtra(EXTRA_MANUAL, false))
                else finishIfIdle()
            }
            else -> finishIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun enqueue(ruleId: Long, manual: Boolean) {
        pendingJobs.incrementAndGet()
        executor.execute {
            val wakeLock = acquireWakeLock()
            try {
                execute(ruleId, manual)
            } catch (e: Exception) {
                Log.e(TAG, "Rule $ruleId failed", e)
                RunLog.get(this).add("Rule $ruleId", "Unexpected error: ${e.message}", false)
            } finally {
                releaseWakeLock(wakeLock)
                if (pendingJobs.decrementAndGet() <= 0) stopSelf()
            }
        }
    }

    private fun execute(ruleId: Long, manual: Boolean) {
        val store = RuleStore.get(this)
        val rule = store.find(ruleId) ?: run {
            RestartScheduler.cancel(this, ruleId)
            return
        }

        if (!manual && (!store.masterEnabled || !rule.enabled)) {
            RestartScheduler.cancel(this, ruleId)
            return
        }

        if (!PackageUtil.isInstalled(this, rule.packageName)) {
            record(rule, "Skipped: ${rule.packageName} is not installed", success = false)
            if (!manual) RestartScheduler.schedule(this, rule.copy(lastRunAt = System.currentTimeMillis()))
            return
        }

        // A scheduled run defers while someone is watching; a manual one never does.
        if (!manual && rule.skipWhileScreenOn && ScreenUtil.isScreenOn(this)) {
            record(rule, "Deferred: display is on", success = true, advanceClock = false)
            RestartScheduler.scheduleAt(
                this,
                rule.id,
                System.currentTimeMillis() + minOf(rule.intervalMillis, DEFER_RETRY_MS)
            )
            return
        }

        // Sampled before the kill: the question is whether anyone was watching.
        val displayWasOff = !ScreenUtil.isScreenOn(this)

        val backend = KillBackends.resolve(this)
        val killResult = backend.kill(this, rule.packageName)
        val parts = mutableListOf(
            if (killResult.success) "Killed via ${backend.displayName}"
            else "Kill failed (${backend.displayName}): ${killResult.detail}"
        )
        if (killResult.success && killResult.detail.isNotBlank()) parts += killResult.detail

        if (rule.relaunch) {
            SystemClock.sleep(rule.relaunchDelayMs.coerceIn(0L, MAX_RELAUNCH_DELAY_MS))
            if (rule.wakeScreen) ScreenUtil.wakeScreen(this, backend)
            val launchResult = AppLauncher.launch(this, backend, rule.packageName)
            parts += if (launchResult.success) {
                if (launchResult.detail.isBlank()) "relaunched" else "relaunched (${launchResult.detail})"
            } else {
                "relaunch failed: ${launchResult.detail}"
            }

            // Relaunched into a sleeping TV: once the app has had time to start
            // (a VPN reconnects meanwhile), put the launcher back on top so the
            // TV wakes up to the home screen instead of to that app. Skipped if
            // someone turned the TV on in the meantime.
            if (launchResult.success && displayWasOff && !rule.wakeScreen) {
                SystemClock.sleep(HOME_RETURN_DELAY_MS)
                if (!ScreenUtil.isScreenOn(this)) {
                    val home = AppLauncher.goHome(this, backend)
                    parts += if (home.success) "returned to Home" else "could not return to Home: ${home.detail}"
                }
            }
        }

        record(rule, parts.joinToString(" • "), killResult.success)
        if (!manual) {
            RestartScheduler.schedule(this, store.find(ruleId) ?: rule)
        }
    }

    /** Persist the outcome on the rule and in the rolling log. */
    private fun record(rule: Rule, detail: String, success: Boolean, advanceClock: Boolean = true) {
        Log.i(TAG, "${rule.packageName}: $detail")
        val store = RuleStore.get(this)
        val current = store.find(rule.id)
        val updated = current?.copy(
            lastRunAt = if (advanceClock) System.currentTimeMillis() else current.lastRunAt,
            lastResult = detail
        )

        // A rule that keeps deferring records the identical outcome every few
        // minutes. Writing and logging each repeat would churn storage and
        // flush the real history out of the 50-entry log within a few hours,
        // so an unchanged outcome is recorded once and then stays quiet.
        if (current != null && updated == current) return

        if (updated != null) store.upsert(updated)
        RunLog.get(this).add(rule.label, detail, success)
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return try {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TasksKiller:restart").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock", e)
            null
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock release failed", e)
        }
    }

    private fun finishIfIdle() {
        if (pendingJobs.get() <= 0) stopSelf()
    }

    private fun promoteToForeground() {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_restart)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )
    }

    companion object {
        private const val TAG = "RestartService"
        private const val CHANNEL_ID = "taskskiller_restarts"
        private const val NOTIFICATION_ID = 4711

        private const val ACTION_RUN_RULE = "com.thefactor1.taskskiller.action.RUN_RULE"
        private const val EXTRA_RULE_ID = "rule_id"
        private const val EXTRA_MANUAL = "manual"

        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60_000L
        private const val MAX_RELAUNCH_DELAY_MS = 60_000L

        /** Time the relaunched app gets in front before the launcher is brought back. */
        private const val HOME_RETURN_DELAY_MS = 15_000L

        /** How soon to look again after deferring a run because the TV was in use. */
        private const val DEFER_RETRY_MS = 5 * 60_000L

        fun runRule(context: Context, ruleId: Long, manual: Boolean = false) {
            val intent = Intent(context, RestartService::class.java).apply {
                action = ACTION_RUN_RULE
                putExtra(EXTRA_RULE_ID, ruleId)
                putExtra(EXTRA_MANUAL, manual)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ only lifts the background foreground-service ban
                // for a broadcast delivered by an *exact* alarm. On the inexact
                // fallback path that exemption is absent and every run dies
                // here, so name the cause instead of logging a bare exception.
                Log.e(TAG, "Could not start restart service", e)
                val hint = if (!RestartScheduler.canScheduleExact(context)) {
                    " — exact alarms are not permitted, so the wake-up carried no " +
                        "foreground-service exemption. Grant them in Setup."
                } else {
                    ""
                }
                RunLog.get(context)
                    .add("Rule $ruleId", "Service start blocked: ${e.message}$hint", false)
            }
        }
    }
}
