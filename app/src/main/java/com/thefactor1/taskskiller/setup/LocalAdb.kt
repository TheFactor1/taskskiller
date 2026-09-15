package com.thefactor1.taskskiller.setup

import android.content.Context
import android.os.Build
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

    /**
     * dadb creates its key with java.util.Base64, which Android only has from
     * 8.0. On 7.x the first session died with NoClassDefFoundError, so the
     * local-ADB features are simply off there.
     */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private const val UNSUPPORTED =
        "Needs Android 8.0 or newer: use the manual setup from a computer below"

    data class Result(val success: Boolean, val output: String)

    class Session internal constructor(private val adb: Dadb) {
        /** Never throws: connection and protocol errors come back as a failed [Result]. */
        fun exec(command: String): Result = try {
            val response = adb.shell(command)
            Result(response.exitCode == 0, response.allOutput.trim())
        } catch (e: Throwable) {
            Result(false, describe(e))
        }
    }

    /**
     * Runs [block] over one connection, then closes it. The connection is made
     * lazily by the first command, so failures to reach adbd surface from
     * [Session.exec]; a non-null return means the session could not even be
     * set up (for example, the key could not be created).
     *
     * Catches Throwable, not Exception: a class missing from an old or unusual
     * ROM arrives as an Error, and on the background threads this runs on an
     * uncaught one takes the whole app down.
     */
    fun session(context: Context, socketTimeoutMs: Int, block: (Session) -> Unit): String? {
        if (!isSupported) return UNSUPPORTED
        return try {
            Dadb.create(HOST, PORT, keyPair(context), CONNECT_TIMEOUT_MS, socketTimeoutMs).use {
                block(Session(it))
            }
            null
        } catch (e: Throwable) {
            describe(e)
        }
    }

    /** Our own key, generated once. dadb's default, ~/.android/adbkey, has no home on Android. */
    private fun keyPair(context: Context): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val privateKey = File(dir, "adbkey")
        val publicKey = File(dir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) AdbKeyPair.generate(privateKey, publicKey)
        return AdbKeyPair.read(privateKey, publicKey)
    }

    /**
     * Turn the usual failures into something the user can act on from the couch.
     * The whole cause chain is searched: the root of a refused connection is an
     * ErrnoException ("ECONNREFUSED"), with the ConnectException one level up.
     */
    private fun describe(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.toList()
        return when {
            chain.any { it is ConnectException } ->
                "Network debugging is off: turn it on in Developer options (nothing answers on port $PORT)"
            chain.any { it is SocketTimeoutException } ->
                "Timed out: accept \"Allow network debugging?\" on the TV, tick Always allow, and try again"
            else -> chain.last().let { it.message ?: it.javaClass.simpleName }
        }
    }
}
