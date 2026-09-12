package com.thefactor1.taskskiller.launch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.thefactor1.taskskiller.kill.KillBackend
import com.thefactor1.taskskiller.kill.OpResult
import com.thefactor1.taskskiller.util.PackageUtil

/**
 * Starting an activity from the background is heavily restricted from Android 10
 * onwards, so the privileged path is strongly preferred: `am start` runs as the
 * shell user and is not subject to those limits at all.
 */
object AppLauncher {

    fun launch(context: Context, backend: KillBackend, packageName: String): OpResult {
        backend.launch(context, packageName)?.let { privileged ->
            if (privileged.success) return privileged
            // Fall through and try the ordinary path rather than giving up.
        }

        val intent = PackageUtil.launchIntent(context, packageName)
            ?: return OpResult.fail("$packageName has no launchable activity")

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        return try {
            context.startActivity(intent)
            if (backgroundStartsLikelyBlocked(context)) {
                OpResult(true, "Launch requested, but background activity starts may be blocked")
            } else {
                OpResult.OK
            }
        } catch (e: SecurityException) {
            OpResult.fail("Background activity start blocked: ${e.message}")
        } catch (e: Exception) {
            OpResult.fail("startActivity failed: ${e.message}")
        }
    }

    /**
     * From Android 10 the reliable exemption for a background app is holding
     * "display over other apps". Without it, `startActivity` returns normally
     * but the system silently drops the launch.
     */
    fun backgroundStartsLikelyBlocked(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context)
}
