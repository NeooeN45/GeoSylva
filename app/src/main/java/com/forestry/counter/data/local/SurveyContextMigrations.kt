package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Ajoute seulement le contexte de session ; aucune session/tige/snapshot/file historique réécrite. */
object SurveyContextMigrations {
    val MIGRATION_40_41: Migration = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `survey_contexts` (
                    `sessionId` TEXT NOT NULL,
                    `revision` INTEGER NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `sealedPayloadJson` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `createdBy` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `updatedBy` TEXT NOT NULL,
                    `copiedFromSessionId` TEXT,
                    PRIMARY KEY(`sessionId`),
                    FOREIGN KEY(`sessionId`) REFERENCES `inventaire_sessions`(`sessionId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_survey_contexts_sessionId`
                ON `survey_contexts` (`sessionId`)
            """.trimIndent())
        }
    }
}
