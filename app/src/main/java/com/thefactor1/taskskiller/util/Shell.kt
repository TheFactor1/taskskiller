package com.thefactor1.taskskiller.util

import java.io.BufferedReader
import java.io.File
import java.io.InputStream

object Shell {

    data class Result(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
    }

    private val SU_LOCATIONS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/su/bin/su", "/system/sbin/su", "/vendor/bin/su"
    )

    fun suBinaryExists(): Boolean = SU_LOCATIONS.any { File(it).exists() }

    /**
     * Runs [command] through `su`, returning a non-zero result rather than
     * throwing. The wait happens on a helper thread because the timed
     * `Process.waitFor` overload only exists from API 26.
     */
    fun runAsRoot(command: String, timeoutSeconds: Long = 20): Result = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()

        var output = ""
        var exitCode = -1
        val worker = Thread {
            output = process.inputStream.readTextSafely()
            exitCode = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                -1
            }
        }
        worker.start()
        worker.join(timeoutSeconds * 1_000L)

        if (worker.isAlive) {
            process.destroy()
            worker.interrupt()
            Result(-1, "timed out after ${timeoutSeconds}s")
        } else {
            Result(exitCode, output)
        }
    } catch (e: Exception) {
        Result(-1, e.message ?: e.javaClass.simpleName)
    }
}

/** Reads a stream to the end, yielding "" rather than throwing on a broken pipe. */
internal fun InputStream.readTextSafely(): String = try {
    bufferedReader().use(BufferedReader::readText).trim()
} catch (e: Exception) {
    ""
}
