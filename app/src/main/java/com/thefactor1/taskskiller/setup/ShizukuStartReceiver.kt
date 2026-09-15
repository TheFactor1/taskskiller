package com.thefactor1.taskskiller.setup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.thefactor1.taskskiller.data.RuleStore
import com.thefactor1.taskskiller.data.RunLog

/**
 * Starts Shizuku a little after boot. Its server does not survive a reboot, and
 * adbd's network port takes a moment to come up, so the start is attempted from
 * an alarm shortly after BOOT_COMPLETED and retried a few times before the
 * failure is written to the run log.
 */
class ShizukuStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val pending = goAsync()
        // Network I/O: off the main thread, inside the broadcast's time budget.
        Thread {
            try {
                if (!LocalAdb.isSupported || !RuleStore.get(context).autoStartShizuku) return@Thread
                val step = ShizukuStarter.startInBackground(context)
                when {
                    // Android also sends BOOT_COMPLETED after an app update on
                    // some boxes (seen on the Shield), so this often finds the
                    // server already up; that is not worth a log entry.
                    ShizukuStarter.wasAlreadyRunning(step) ->
                        Log.i(TAG, "Shizuku already running (attempt $attempt)")
                    step.ok -> {
                        Log.i(TAG, "Shizuku running after boot (attempt $attempt)")
                        RunLog.get(context).add("Shizuku", "Started automatically after boot", true)
                    }
                    attempt < MAX_ATTEMPTS -> {
                        Log.w(TAG, "Shizuku start attempt $attempt failed: ${step.detail}")
                        schedule(context, attempt + 1)
                    }
                    else -> RunLog.get(context)
                        .add("Shizuku", "Could not start after boot: ${step.detail}", false)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "ShizukuStart"
        private const val EXTRA_ATTEMPT = "attempt"
        private const val REQUEST_CODE = 9001
        private const val MAX_ATTEMPTS = 5
        private const val FIRST_DELAY_MS = 30_000L
        private const val RETRY_DELAY_MS = 60_000L

        fun schedule(context: Context, attempt: Int = 1) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, ShizukuStartReceiver::class.java)
                .putExtra(EXTRA_ATTEMPT, attempt)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val delay = if (attempt == 1) FIRST_DELAY_MS else RETRY_DELAY_MS
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay,
                pendingIntent
            )
        }
    }
}
