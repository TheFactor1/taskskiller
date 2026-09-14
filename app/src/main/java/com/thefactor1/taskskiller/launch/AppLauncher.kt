package com.thefactor1.taskskiller.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

        // The privileged path fails for this same reason, so its message would
        // only repeat this one.
        val intent = PackageUtil.launchIntent(context, packageName)
            ?: return OpResult.fail("$packageName has no launchable activity")

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
     * Brings the default launcher back to the front. Starting an activity does
     * not wake the display, whereas a HOME key press is simply dropped by a
     * sleeping TV. The launcher is resolved explicitly: with no default set, a
     * bare HOME intent would leave an app chooser waiting for when the TV wakes.
     */
    fun goHome(context: Context, backend: KillBackend): OpResult {
        val component = homeComponent(context) ?: return OpResult.fail("no default launcher is set")

        backend.shell(context, arrayOf("am", "start", "-n", component.flattenToShortString()))
            ?.takeIf { it.success }
            ?.let { return it }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            OpResult.OK
        } catch (e: Exception) {
            OpResult.fail("startActivity failed: ${e.message}")
        }
    }

    /**
     * Puts back the app that was on screen before a relaunch took over. Its
     * launcher intent resumes the existing task where it was, so whatever was
     * playing comes back rather than a fresh start screen.
     */
    fun returnTo(context: Context, backend: KillBackend, packageName: String): OpResult =
        if (packageName == homeComponent(context)?.packageName) goHome(context, backend)
        else launch(context, backend, packageName)

    private fun homeComponent(context: Context): ComponentName? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?: return null
        // The system's chooser answers for HOME when no default launcher is set.
        if (launcher.packageName == "android") return null
        return ComponentName(launcher.packageName, launcher.name)
    }

    /**
     * From Android 10 the reliable exemption for a background app is holding
     * "display over other apps". Without it, `startActivity` returns normally
     * but the system silently drops the launch.
     */
    fun backgroundStartsLikelyBlocked(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context)
}
