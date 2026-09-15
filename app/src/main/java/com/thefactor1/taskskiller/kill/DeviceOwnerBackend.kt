package com.thefactor1.taskskiller.kill

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Uses device-owner privileges to hide and immediately unhide the target.
 *
 * Hiding a package force-stops it as a side effect, which gives a real
 * force-stop without root or a per-boot ADB step — at the cost of a one-time
 * provisioning command on a box with no accounts set up.
 *
 * To the rest of the system a hidden package looks uninstalled, so every
 * refresh reads as a remove and re-add: PACKAGE_REMOVED/ADDED broadcasts, the
 * launcher tile may move, and processes bound to the app die with it. The
 * Setup screen's device-owner instructions say so.
 */
object DeviceOwnerBackend : KillBackend {
    override val id = "deviceowner"
    override val displayName = "Device owner (hide/unhide)"
    override val rank = 15
    override val isForceStop = true

    override fun isAvailable(context: Context): Boolean = dpm(context)?.let {
        runCatching { it.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
    } ?: false

    override fun unavailableReason(context: Context): String =
        "Refresher is not the device owner of this box"

    override fun kill(context: Context, packageName: String): OpResult {
        val dpm = dpm(context) ?: return OpResult.fail("DevicePolicyManager unavailable")
        val admin = ComponentName(context, AdminReceiver::class.java)
        return try {
            if (!dpm.setApplicationHidden(admin, packageName, true)) {
                return OpResult.fail("$packageName cannot be hidden on this device")
            }
            // Unhide straight away: the kill already happened, and leaving it
            // hidden would make the relaunch impossible.
            dpm.setApplicationHidden(admin, packageName, false)
            OpResult.OK
        } catch (e: SecurityException) {
            OpResult.fail("Device owner privileges rejected: ${e.message}")
        } catch (e: IllegalArgumentException) {
            OpResult.fail("Unknown package $packageName")
        }
    }

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
}
