package com.thefactor1.taskskiller.data

import org.json.JSONObject

/**
 * One "kill this app every N minutes, then bring it back" job.
 *
 * [intervalMinutes] is measured from the end of the previous run, not from a
 * wall-clock anchor, so a run that is delayed by Doze does not cause a burst of
 * catch-up restarts afterwards.
 */
data class Rule(
    val id: Long,
    val packageName: String,
    val label: String,
    val intervalMinutes: Int,
    val enabled: Boolean = true,
    /** Relaunch the app after killing it. Off means kill-only. */
    val relaunch: Boolean = true,
    /** Pause between the kill and the relaunch, to let the process fully die. */
    val relaunchDelayMs: Long = 3_000L,
    /** Turn the display on for the relaunch. Off keeps the TV asleep. */
    val wakeScreen: Boolean = false,
    /** Skip the run when the display is on, so a restart never interrupts viewing. */
    val skipWhileScreenOn: Boolean = false,
    val lastRunAt: Long = 0L,
    val lastResult: String = ""
) {
    val intervalMillis: Long get() = intervalMinutes * 60_000L

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_PACKAGE, packageName)
        put(KEY_LABEL, label)
        put(KEY_INTERVAL, intervalMinutes)
        put(KEY_ENABLED, enabled)
        put(KEY_RELAUNCH, relaunch)
        put(KEY_RELAUNCH_DELAY, relaunchDelayMs)
        put(KEY_WAKE_SCREEN, wakeScreen)
        put(KEY_SKIP_SCREEN_ON, skipWhileScreenOn)
        put(KEY_LAST_RUN, lastRunAt)
        put(KEY_LAST_RESULT, lastResult)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_PACKAGE = "package"
        private const val KEY_LABEL = "label"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RELAUNCH = "relaunch"
        private const val KEY_RELAUNCH_DELAY = "relaunchDelay"
        private const val KEY_WAKE_SCREEN = "wakeScreen"
        private const val KEY_SKIP_SCREEN_ON = "skipScreenOn"
        private const val KEY_LAST_RUN = "lastRun"
        private const val KEY_LAST_RESULT = "lastResult"

        /**
         * Below roughly this the system starts coalescing alarms hard enough
         * that the advertised interval stops being honest. A mains-powered TV
         * box rarely enters Doze at all, so short intervals usually do work —
         * but they are not guaranteed by the platform.
         */
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 24 * 60

        fun fromJson(json: JSONObject): Rule = Rule(
            id = json.getLong(KEY_ID),
            packageName = json.getString(KEY_PACKAGE),
            label = json.optString(KEY_LABEL, json.getString(KEY_PACKAGE)),
            intervalMinutes = json.optInt(KEY_INTERVAL, 60),
            enabled = json.optBoolean(KEY_ENABLED, true),
            relaunch = json.optBoolean(KEY_RELAUNCH, true),
            relaunchDelayMs = json.optLong(KEY_RELAUNCH_DELAY, 3_000L),
            wakeScreen = json.optBoolean(KEY_WAKE_SCREEN, false),
            skipWhileScreenOn = json.optBoolean(KEY_SKIP_SCREEN_ON, false),
            lastRunAt = json.optLong(KEY_LAST_RUN, 0L),
            lastResult = json.optString(KEY_LAST_RESULT, "")
        )
    }
}
