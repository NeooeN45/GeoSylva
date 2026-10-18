package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: no historic measurement, snapshot or queue is adopted or rewritten. */
object WoodDepotMigrations {
    val MIGRATION_42_43: Migration = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `wood_depots` (
                    `depotId` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL,
                    `notes` TEXT, `latitude` REAL, `longitude` REAL, `accuracyM` REAL,
                    `locationSource` TEXT, `ownerScope` TEXT NOT NULL, `ownerAccountId` TEXT,
                    `creationFingerprint` TEXT NOT NULL, `revision` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL, `updatedBy` TEXT NOT NULL, `deletedAt` INTEGER,
                    PRIMARY KEY(`depotId`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_wood_depots_owner_live`
                ON `wood_depots` (`ownerScope`, `ownerAccountId`, `deletedAt`)
            """.trimIndent())
        }
    }
}
