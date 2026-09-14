package com.thefactor1.taskskiller.setup

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * An ADB client aimed at this box's own "Network debugging" port.
 *
 * Connecting to adbd from inside the app gives us a shell-user session without
 * a computer: enough to start Shizuku and to apply the settings Android TV has
 * no screen for. The first connection makes the TV ask "Allow network
 * debugging?" for our key; once the user ticks "Always allow", later sessions,
 * including the one after a reboot, connect silently.
 */
object LocalAdb {

    private const val HOST = "127.0.0.1"
    const val PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 5_000

    /** Long enough to find the remote and accept the authorization prompt. */
    const val INTERACTIVE_TIMEOUT_MS = 60_000

    /** After a reboot the key is already trusted, so there is nothing to wait for. */
    const val BACKGROUND_TIMEOUT_MS = 15_000

    data class Result(val success: Boolean, val output: String)

    class Session internal constructor(private val adb: Dadb) {
        /** Never throws: connection and protocol errors come back as a failed [Result]. */
        fun exec(command: String): Result = try {
            val response = adb.shell(command)
            Result(response.exitCode == 0, response.allOutput.trim())
        } catch (e: Exception) {
            Result(false, describe(e))
        }
    }

    /**
     * Runs [block] over one connection, then closes it. The connection is made
     * lazily by the first command, so failures to reach adbd surface from
     * [Session.exec]; a non-null return means the session could not even be
     * set up (for example, the key could not be created).
     */
    fun session(context: Context, socketTimeoutMs: Int, block: (Session) -> Unit): String? = try {
        Dadb.create(HOST, PORT, keyPair(context), CONNECT_TIMEOUT_MS, socketTimeoutMs).use {
            block(Session(it))
        }
        null
    } catch (e: Exception) {
        describe(e)
    }

    /** Our own key, generated once. dadb's default, ~/.android/adbkey, has no home on Android. */
    private fun keyPair(context: Context): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val privateKey = File(dir, "adbkey")
        val publicKey = File(dir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) AdbKeyPair.generate(privateKey, publicKey)
        return AdbKeyPair.read(privateKey, publicKey)
    }

    /** Turn the usual failures into something the user can act on from the couch. */
    private fun describe(e: Throwable): String = when (val root = generateSequence(e) { it.cause }.last()) {
        is ConnectException ->
            "Network debugging is off: turn it on in Developer options (nothing answers on port $PORT)"
        is SocketTimeoutException ->
            "Timed out: accept \"Allow network debugging?\" on the TV, tick Always allow, and try again"
        else -> root.message ?: root.javaClass.simpleName
    }
}
