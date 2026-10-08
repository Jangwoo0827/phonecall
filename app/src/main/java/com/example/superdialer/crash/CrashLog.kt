package com.example.superdialer.crash

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crashes on the phone (nothing is sent anywhere): the app's uncaught errors are written to files that
 * can be read and shared from Settings > 오류 기록. Call [install] once from the Application.
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val KEEP = 10

    private var appContext: Context? = null

    /** Bumped when entries change so screens showing them refresh. */
    var version by mutableIntStateOf(0)
        private set

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(thread, error)
            } catch (e: Throwable) {
                // never let logging hide the real crash
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Newest first. */
    fun entries(): List<Pair<String, String>> {
        val dir = dir() ?: return emptyList()
        return (dir.listFiles() ?: emptyArray()).sortedByDescending { it.name }.map { it.name to it.readText() }
    }

    fun clear() {
        dir()?.listFiles()?.forEach { it.delete() }
        version++
    }

    private fun dir(): File? = appContext?.let { File(it.filesDir, DIR).apply { mkdirs() } }

    private fun write(thread: Thread, error: Throwable) {
        val context = appContext ?: return
        val dir = dir() ?: return
        val now = System.currentTimeMillis()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        File(dir, "crash-$now.txt").writeText(format(now, version, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.SDK_INT, thread.name, error))
        // keep only the newest few
        (dir.listFiles() ?: emptyArray()).sortedByDescending { it.name }.drop(KEEP).forEach { it.delete() }
    }

    /** The text of one report. */
    fun format(atMillis: Long, appVersion: String, device: String, sdk: Int, threadName: String, error: Throwable): String {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(atMillis))
        return buildString {
            appendLine("시각: $time")
            appendLine("앱 버전: $appVersion")
            appendLine("기기: $device (Android API $sdk)")
            appendLine("스레드: $threadName")
            appendLine()
            append(stack)
        }
    }
}
