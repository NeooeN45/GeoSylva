package com.forestry.counter.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `flora_fts`
            USING fts4(
                `speciesId` TEXT NOT NULL,
                `nomFrancais` TEXT NOT NULL,
                `nomScientifique` TEXT NOT NULL,
                `vernaculaires` TEXT NOT NULL,
                `synonymes` TEXT NOT NULL,
                `typeMilieu` TEXT NOT NULL,
                `strate` TEXT NOT NULL
            )
        """.trimIndent())

        val tableExists = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='gps_context_cache'"
        ).use { it.moveToFirst() }

        if (tableExists) {
            database.execSQL("CREATE TABLE `gps_context_cache_temp` AS SELECT * FROM `gps_context_cache`")
            database.execSQL("DROP TABLE IF EXISTS `gps_context_cache`")
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `gps_context_cache` (
                    `latKey` REAL NOT NULL,
                    `lonKey` REAL NOT NULL,
                    `regionCode` TEXT NOT NULL DEFAULT '',
                    `deptCode` TEXT NOT NULL DEFAULT '',
                    `altitudeApproxM` REAL NOT NULL DEFAULT 0.0,
                    `topoHint` TEXT NOT NULL DEFAULT '',
                    `zoneHumideProb` REAL NOT NULL DEFAULT 0.0,
                    `packIdActive` TEXT NOT NULL DEFAULT '',
                    `computedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`latKey`, `lonKey`)
                )
            """.trimIndent())
            database.execSQL("INSERT INTO `gps_context_cache` SELECT * FROM `gps_context_cache_temp`")
            database.execSQL("DROP TABLE `gps_context_cache_temp`")
        } else {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `gps_context_cache` (
                    `latKey` REAL NOT NULL,
                    `lonKey` REAL NOT NULL,
                    `regionCode` TEXT NOT NULL DEFAULT '',
                    `deptCode` TEXT NOT NULL DEFAULT '',
                    `altitudeApproxM` REAL NOT NULL DEFAULT 0.0,
                    `topoHint` TEXT NOT NULL DEFAULT '',
                    `zoneHumideProb` REAL NOT NULL DEFAULT 0.0,
                    `packIdActive` TEXT NOT NULL DEFAULT '',
                    `computedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`latKey`, `lonKey`)
                )
            """.trimIndent())
        }
    }
}
