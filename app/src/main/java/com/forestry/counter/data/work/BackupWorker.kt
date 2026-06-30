package com.forestry.counter.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.Room
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.repository.CounterRepositoryImpl
import com.forestry.counter.data.repository.FormulaRepositoryImpl
import com.forestry.counter.data.repository.GroupRepositoryImpl
import com.forestry.counter.domain.calculator.FormulaParser
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext

            val db = Room.databaseBuilder(
                context,
                ForestryDatabase::class.java,
                ForestryDatabase.DATABASE_NAME
            ).build()

            val parser = FormulaParser()

            val groupRepo = GroupRepositoryImpl(db, db.groupDao(), db.counterDao(), db.formulaDao(), db.groupVariableDao())
            val counterRepo = CounterRepositoryImpl(db.counterDao(), db.formulaDao(), db.groupVariableDao(), parser)
            val formulaRepo = FormulaRepositoryImpl(db.formulaDao(), db.counterDao(), db.groupVariableDao(), parser)

            val export = ExportDataUseCase(context, groupRepo, counterRepo, formulaRepo)

            val dir = context.getExternalFilesDir("backups") ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
            val file = File(dir, "ForestryBackup-$ts.zip")

            when (export.exportToZipFile(file).isSuccess) {
                true -> Result.success()
                false -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
