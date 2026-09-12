package com.thefactor1.taskskiller.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Alarm landing pad. Broadcasts triggered by an exact alarm are exempt from the
 * Android 12+ ban on starting foreground services from the background, which is
 * what makes the hand-off below legal while the TV is asleep.
 */
class RestartAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId < 0) return
        Log.i(TAG, "Alarm fired for rule $ruleId")
        RestartService.runRule(context, ruleId)
    }

    companion object {
        private const val TAG = "RestartAlarm"
        const val ACTION_FIRE = "com.thefactor1.taskskiller.action.FIRE"
        const val EXTRA_RULE_ID = "rule_id"
    }
}
