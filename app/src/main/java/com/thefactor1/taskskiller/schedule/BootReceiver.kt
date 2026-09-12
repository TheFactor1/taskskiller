package com.thefactor1.taskskiller.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Alarms do not survive a reboot, so every schedule is rebuilt here. Rules live
 * in device-protected storage so this also works at LOCKED_BOOT_COMPLETED,
 * before the user has unlocked the box.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Re-arming schedules after ${intent.action}")
                RestartScheduler.scheduleAll(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
