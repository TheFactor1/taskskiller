package com.thefactor1.taskskiller.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.thefactor1.taskskiller.data.Rule
import com.thefactor1.taskskiller.data.RuleStore

object RestartScheduler {

    private const val TAG = "RestartScheduler"

    /** Never fire less than this far out, so re-arming can't produce a tight loop. */
    private const val MIN_LEAD_MS = 30_000L

    fun scheduleAll(context: Context) {
        val store = RuleStore.get(context)
        // Anchor first: without it [nextRunAt] is not a pure function of stored
        // state, and rebuilding the schedule would move every pending first run.
        val allRules = store.backfillAnchors(System.currentTimeMillis())
        val enabled = if (store.masterEnabled) allRules.filter { it.enabled } else emptyList()
        val enabledIds = enabled.map { it.id }.toSet()

        // Drop alarms for rules that were disabled or deleted while we were away.
        allRules.filterNot { enabledIds.contains(it.id) }.forEach { cancel(context, it.id) }
        enabled.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, rule: Rule) = scheduleAt(context, rule.id, nextRunAt(rule))

    /** Schedule an explicit wake-up time, used when a run is deferred rather than completed. */
    fun scheduleAt(context: Context, ruleId: Long, triggerAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(context, ruleId)

        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            } else {
                // Inexact but still Doze-piercing; drifts by a few minutes.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm rejected for rule $ruleId", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, ruleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(context, ruleId)
        alarmManager.cancel(pendingIntent)
        // Drop the PendingIntent too, so a cancelled rule leaves nothing behind
        // that a later FLAG_UPDATE_CURRENT could silently resurrect.
        pendingIntent.cancel()
    }

    /**
     * Intervals are measured from the end of the last run, or from the rule's
     * anchor if it has never run. Both are persisted, which makes this a pure
     * function of stored state: re-arming the same rule any number of times
     * lands on the same instant instead of pushing it further out each time.
     *
     * Runs missed while the box was powered off collapse into a single one
     * shortly after boot rather than being replayed one per interval.
     */
    fun nextRunAt(rule: Rule): Long {
        val now = System.currentTimeMillis()
        val base = if (rule.lastRunAt > 0L) rule.lastRunAt else rule.anchorAt
        // Saved by a build that had no anchor yet: start the clock from now.
        if (base <= 0L) return now + rule.intervalMillis
        return maxOf(base + rule.intervalMillis, now + MIN_LEAD_MS)
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context, ruleId: Long): PendingIntent {
        val intent = Intent(context, RestartAlarmReceiver::class.java).apply {
            action = RestartAlarmReceiver.ACTION_FIRE
            // A distinct data Uri per rule keeps PendingIntents from colliding;
            // extras alone are not part of PendingIntent identity.
            data = Uri.parse("taskskiller://rule/$ruleId")
            putExtra(RestartAlarmReceiver.EXTRA_RULE_ID, ruleId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, ruleId.toInt(), intent, flags)
    }
}
