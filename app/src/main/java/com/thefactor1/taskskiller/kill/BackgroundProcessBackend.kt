package com.thefactor1.taskskiller.kill

import android.app.ActivityManager
import android.content.Context

/**
 * The only backend available with no setup at all.
 *
 * [ActivityManager.killBackgroundProcesses] kills cached and background
 * processes but will not touch an app holding a foreground service — which is
 * exactly what most VPN clients do. Treat it as best-effort.
 */
object BackgroundProcessBackend : KillBackend {
    override val id = "background"
    override val displayName = "Background processes only (no setup)"
    override val rank = 0
    override val isForceStop = false

    override fun isAvailable(context: Context): Boolean = true

    override fun unavailableReason(context: Context): String = ""

    override fun kill(context: Context, packageName: String): OpResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return OpResult.fail("ActivityManager unavailable")
        return try {
            am.killBackgroundProcesses(packageName)
            OpResult(true, "Background processes signalled (foreground services survive)")
        } catch (e: SecurityException) {
            OpResult.fail("KILL_BACKGROUND_PROCESSES denied: ${e.message}")
        }
    }
}
