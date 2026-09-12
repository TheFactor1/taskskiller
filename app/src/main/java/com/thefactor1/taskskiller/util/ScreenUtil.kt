package com.thefactor1.taskskiller.util

import android.content.Context
import android.os.PowerManager
import com.thefactor1.taskskiller.kill.KillBackend

object ScreenUtil {

    private const val KEYCODE_WAKEUP = 224

    fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    /**
     * Wakes the display. A shell backend can send KEYCODE_WAKEUP, which is what
     * a remote-control button press does; without one we fall back to the
     * deprecated wake-lock flags, which still work on TV firmware.
     */
    @Suppress("DEPRECATION")
    fun wakeScreen(context: Context, backend: KillBackend) {
        if (isScreenOn(context)) return

        backend.shell(context, arrayOf("input", "keyevent", KEYCODE_WAKEUP.toString()))
            ?.takeIf { it.success }
            ?.let { return }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "TasksKiller:wakeScreen"
        )
        try {
            wakeLock.acquire(5_000L)
        } catch (e: Exception) {
            return
        }
        // Let it lapse on its own timeout so the TV can go back to sleep.
    }
}
