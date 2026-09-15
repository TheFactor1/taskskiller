package com.thefactor1.taskskiller.kill

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * The only backend available with no setup at all.
 *
 * [ActivityManager.killBackgroundProcesses] kills cached and background
 * processes but will not touch an app holding a foreground service — which is
 * exactly what most VPN clients do. Android does not say whether it stopped
 * anything, so a run through here is reported as unconfirmed.
 *
 * From Android 14 the call only reaches the caller's own processes, so it
 * cannot stop another app at all; the backend reports itself unavailable there
 * and a run that falls back to it fails instead of claiming success.
 */
object BackgroundProcessBackend : KillBackend {
    override val id = "background"
    override val displayName = "Background processes only (no setup)"
    override val rank = 0
    override val isForceStop = false

    private const val OWN_PROCESSES_ONLY = "Android 14 and newer only let an app stop its own processes"

    private val worksOnThisVersion: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    override fun isAvailable(context: Context): Boolean = worksOnThisVersion

    override fun unavailableReason(context: Context): String = OWN_PROCESSES_ONLY

    override fun kill(context: Context, packageName: String): OpResult {
        if (!worksOnThisVersion) {
            return OpResult.fail("$OWN_PROCESSES_ONLY; set up Shizuku in Setup")
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return OpResult.fail("ActivityManager unavailable")
        return try {
            am.killBackgroundProcesses(packageName)
            OpResult(
                true,
                "unconfirmed: Android does not report what it stopped, and foreground services such as VPNs survive"
            )
        } catch (e: SecurityException) {
            OpResult.fail("KILL_BACKGROUND_PROCESSES denied: ${e.message}")
        }
    }
}
