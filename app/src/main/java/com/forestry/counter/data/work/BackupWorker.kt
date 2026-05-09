package com.forestry.counter.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val app = context as ForestryCounterApplication

            val export = ExportDataUseCase(
                context,
                app.groupRepository,
                app.counterRepository,
                app.formulaRepository
            )

            val dir = context.getExternalFilesDir("backups") ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
            val file = File(dir, "ForestryBackup-$ts.zip")

            when (export.exportToZipFile(file).isSuccess) {
                true -> Result.success()
                false -> Result.retry()
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Backup failed", e)
            Result.retry()
        }
    }
}
