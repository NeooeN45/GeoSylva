package com.forestry.counter.data.logging

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CrashLogger {
    @Volatile
    var enabled: Boolean = true

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (enabled) {
                try {
                    // Écriture dans le stockage externe (accessible via gestionnaire de fichiers)
                    // Chemin : /sdcard/Android/data/com.forestry.counter/files/crash-logs/
                    val extDir = context.getExternalFilesDir(null)
                    val dir = if (extDir != null) File(extDir, "crash-logs") else File(context.filesDir, "crash-logs")
                    if (!dir.exists()) dir.mkdirs()
                    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(Date())
                    val file = File(dir, "crash-$ts.txt")
                    PrintWriter(file).use { pw ->
                        pw.println("Thread: ${thread.name}")
                        pw.println("Time: $ts")
                        pw.println("---")
                        throwable.printStackTrace(pw)
                    }
                } catch (_: Exception) { }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun crashDir(context: Context): File {
        val extDir = context.getExternalFilesDir(null)
        return if (extDir != null) File(extDir, "crash-logs") else File(context.filesDir, "crash-logs")
    }

    fun latestLog(context: Context): String? {
        val dir = crashDir(context)
        val files = dir.listFiles() ?: return null
        val last = files.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { last.readText() }.getOrNull()
    }

    fun clearLogs(context: Context) {
        crashDir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun exportLatest(context: Context, uri: Uri): Boolean {
        return try {
            val text = latestLog(context) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { it.write(text) }
            }
            true
        } catch (_: Exception) { false }
    }

    fun exportAllZip(context: Context, uri: Uri): Boolean {
        return try {
            val dir = File(context.filesDir, "crash-logs")
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
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
        } catch (_: Exception) { false }
    }
}
