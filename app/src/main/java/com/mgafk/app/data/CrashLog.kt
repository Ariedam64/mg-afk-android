package com.mgafk.app.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists uncaught exceptions to a file in the app's private storage so that
 * intermittent background crashes (which otherwise vanish into logcat and are
 * lost on reboot) can be read back from the Debug screen. Installed in every
 * process from [com.mgafk.app.MgAfkApp.onCreate]; always chains to the previous
 * default handler so the OS still terminates the process normally.
 */
object CrashLog {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024
    private val lock = Any()

    /**
     * Install a default uncaught-exception handler that records the stack trace
     * (tagged with [processLabel]) before delegating to the previous handler.
     */
    fun install(context: Context, processLabel: String) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(appContext, processLabel, thread, throwable) }
            // Always hand back to the original handler so the process dies as
            // usual; we record, we never swallow.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun append(context: Context, processLabel: String, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "==== $stamp | process=$processLabel | thread=${thread.name} ====\n$stack\n"
        synchronized(lock) {
            File(context.filesDir, FILE_NAME).appendText(entry)
        }
    }

    /** Full crash log, oldest first (most recent entries at the bottom). */
    fun read(context: Context): String = synchronized(lock) {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context) {
        synchronized(lock) {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        }
    }

    /** Keep the file bounded. Called once at startup. */
    fun trimIfLarge(context: Context) {
        synchronized(lock) {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (file.exists() && file.length() > MAX_BYTES) {
                file.writeText(file.readText().takeLast(MAX_BYTES / 2))
            }
        }
    }
}
