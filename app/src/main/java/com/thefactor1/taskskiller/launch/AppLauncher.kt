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
        val privileged = backend.launch(context, packageName)
        if (privileged != null && privileged.success) return privileged

        // Fall through to the ordinary path rather than giving up, but carry
        // the reason forward: a bare "relaunched" would hide the fact that the
        // privileged path is broken, which is the thing worth fixing.
        val privilegedFailure = privileged?.detail?.takeIf { it.isNotBlank() }

        val intent = PackageUtil.launchIntent(context, packageName)
            ?: return OpResult.fail(
                listOfNotNull("$packageName has no launchable activity", privilegedFailure)
                    .joinToString("; ")
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        return try {
            context.startActivity(intent)
            val notes = listOfNotNull(
                privilegedFailure?.let { "privileged launch failed: $it" },
                if (backgroundStartsLikelyBlocked(context)) {
                    "background activity starts may be blocked"
                } else {
                    null
                }
            )
            if (notes.isEmpty()) OpResult.OK else OpResult(true, notes.joinToString("; "))
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
