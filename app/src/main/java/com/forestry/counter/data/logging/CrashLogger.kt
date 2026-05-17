package com.forestry.counter.data.logging

import android.content.Context
import android.net.Uri
import android.os.Build
import com.forestry.counter.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CrashLogger {

    @Volatile
    var enabled: Boolean = false

    private const val MAX_LOG_FILES = 20
    private const val LOG_DIR = "crash-logs"
    private val TS_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (enabled) runCatching { writeCrashLog(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        pruneOldLogs(dir)
        val ts = TS_FORMAT.format(Date())
        PrintWriter(File(dir, "crash-$ts.txt"), Charsets.UTF_8).use { pw ->
            pw.println("=== GeoSylva Crash Report ===")
            pw.println("Timestamp : $ts")
            pw.println("Version   : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            pw.println("Android   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            pw.println("Device    : ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("Thread    : ${thread.name}")
            pw.println()
            throwable.printStackTrace(pw)
            var cause = throwable.cause
            while (cause != null) {
                pw.println()
                pw.println("Caused by:")
                cause.printStackTrace(pw)
                cause = cause.cause
            }
        }
    }

    private fun pruneOldLogs(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size >= MAX_LOG_FILES) {
            files.take(files.size - MAX_LOG_FILES + 1).forEach { runCatching { it.delete() } }
        }
    }

    fun latestLog(context: Context): String? {
        val dir = File(context.filesDir, LOG_DIR)
        val last = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { last.readText() }.getOrNull()
    }

    fun logCount(context: Context): Int =
        File(context.filesDir, LOG_DIR).listFiles()?.size ?: 0

    fun clearLogs(context: Context) {
        File(context.filesDir, LOG_DIR).listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun exportLatest(context: Context, uri: Uri): Boolean = runCatching {
        val text = latestLog(context) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { it.write(text) }
        }
        true
    }.getOrDefault(false)

    fun exportAllZip(context: Context, uri: Uri): Boolean = runCatching {
        val files = File(context.filesDir, LOG_DIR)
            .listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) return false
        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zip ->
                files.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        true
    }.getOrDefault(false)
}
