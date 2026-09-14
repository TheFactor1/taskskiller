package com.thefactor1.taskskiller.kill

import android.content.Context

/** Outcome of one privileged operation, kept as data so the UI can show why it failed. */
data class OpResult(val success: Boolean, val detail: String = "") {
    companion object {
        val OK = OpResult(true)
        fun fail(detail: String) = OpResult(false, detail)
    }
}

/**
 * A way of stopping another app.
 *
 * Android does not let an ordinary app force-stop its neighbours, so each
 * backend borrows authority from somewhere else: the shell (Shizuku), root, or
 * device-owner privileges. [BackgroundProcessBackend] is the only one that needs
 * no setup, and it is correspondingly the weakest.
 */
interface KillBackend {
    val id: String
    val displayName: String

    /** Ranked strongest first; [KillBackends.resolve] picks the best available. */
    val rank: Int

    /** True force-stop, as opposed to merely trimming background processes. */
    val isForceStop: Boolean

    fun isAvailable(context: Context): Boolean

    /** Human-readable reason [isAvailable] is false, for the status panel. */
    fun unavailableReason(context: Context): String

    fun kill(context: Context, packageName: String): OpResult

    /**
     * Privileged launch. Backends that can shell out start the activity as the
     * shell user, which sidesteps the background-activity-start restrictions
     * that apply to us. Returning null means "caller should use the normal
     * startActivity path".
     */
    fun launch(context: Context, packageName: String): OpResult? = null

    /** Whether [launch] bypasses the background-activity-start restrictions. */
    val hasPrivilegedLaunch: Boolean get() = false

    /**
     * Run an arbitrary shell command, for backends that have a shell. Used for
     * side errands like waking the display. Null means "no shell here".
     */
    fun shell(context: Context, command: Array<String>): OpResult? = null

    /**
     * Like [shell] but returns what the command printed; null if it failed or
     * this backend has no shell. Used to see which app is on screen.
     */
    fun shellOutput(context: Context, command: Array<String>): String? = null
}
