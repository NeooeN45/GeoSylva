package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Ajout seul : aucune qualification fabriquée ni observation ou archive historique réécrite. */
object ObservationQualificationMigrations {
    val MIGRATION_41_42: Migration = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `observation_qualifications` (
                    `tigeId` TEXT NOT NULL,
                    `revision` INTEGER NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `sealedPayloadJson` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `createdBy` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `updatedBy` TEXT NOT NULL,
                    PRIMARY KEY(`tigeId`),
                    FOREIGN KEY(`tigeId`) REFERENCES `tiges`(`tigeId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_observation_qualifications_tigeId`
                ON `observation_qualifications` (`tigeId`)
            """.trimIndent())
        }
    }
}
