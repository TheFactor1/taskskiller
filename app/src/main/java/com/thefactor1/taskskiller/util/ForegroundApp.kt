package com.thefactor1.taskskiller.util

import android.content.Context
import com.thefactor1.taskskiller.kill.KillBackend

/**
 * Which app is on screen, read through the backend's privileged shell. Null
 * when the backend has no shell (an ordinary app cannot see other apps' tasks).
 */
object ForegroundApp {

    /** "mResumedActivity: ActivityRecord{1a2b3c u0 com.example/.Main t12}" (the prefix varies by version). */
    private val RESUMED = Regex("""ResumedActivity:? ActivityRecord\{\S+ u\d+ ([\w.]+)/""")

    fun packageName(context: Context, backend: KillBackend): String? {
        val output = backend.shellOutput(
            context,
            arrayOf("sh", "-c", "dumpsys activity activities | grep -m1 -E 'mResumedActivity|ResumedActivity:'")
        ) ?: return null
        return RESUMED.find(output)?.groupValues?.get(1)
    }
}
