package com.forestry.counter.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 30 → 31 : active les 4 entités d'analyse précédemment désactivées
 * (DataCorrelation, DataInterpretation, EntityRelation, AdvancedCalculation).
 *
 * Les tables sont créées vides — aucune donnée existante à migrer.
 * Toutes référencent `parcelles(parcelleId)` via FOREIGN KEY ON DELETE CASCADE.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("PRAGMA foreign_keys = OFF")

        createDataCorrelations(database)
        createDataInterpretations(database)
        createEntityRelations(database)
        createAdvancedCalculations(database)

        database.execSQL("PRAGMA foreign_keys = ON")
    }

    private fun createDataCorrelations(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `data_correlations` (
                `correlationId` TEXT NOT NULL,
                `sourceParcelleId` TEXT NOT NULL,
                `targetParcelleId` TEXT,
                `correlationType` TEXT NOT NULL,
                `correlationStrength` REAL NOT NULL,
                `sourceDataType` TEXT NOT NULL,
                `targetDataType` TEXT,
                `sourceField` TEXT NOT NULL,
                `targetField` TEXT,
                `correlationFormula` TEXT,
                `confidenceLevel` REAL NOT NULL,
                `sampleSize` INTEGER NOT NULL,
                `statisticalSignificance` REAL NOT NULL,
                `metadata` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`correlationId`),
                FOREIGN KEY(`sourceParcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE,
                FOREIGN KEY(`targetParcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_source` ON `data_correlations`(`sourceParcelleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_target` ON `data_correlations`(`targetParcelleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_type` ON `data_correlations`(`correlationType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_strength` ON `data_correlations`(`correlationStrength`)")
    }

    private fun createDataInterpretations(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `data_interpretations` (
                `interpretationId` TEXT NOT NULL,
                `parcelleId` TEXT NOT NULL,
                `interpretationType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `confidenceScore` REAL NOT NULL,
                `priority` TEXT NOT NULL,
                `dataSource` TEXT NOT NULL,
                `analysisMethod` TEXT NOT NULL,
                `parameters` TEXT,
                `results` TEXT,
                `recommendations` TEXT,
                `actionable` INTEGER NOT NULL,
                `validUntil` INTEGER,
                `tags` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`interpretationId`),
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_parcelle` ON `data_interpretations`(`parcelleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_type` ON `data_interpretations`(`interpretationType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_confidence` ON `data_interpretations`(`confidenceScore`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_priority` ON `data_interpretations`(`priority`)")
    }

    private fun createEntityRelations(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entity_relations` (
                `relationId` TEXT NOT NULL,
                `sourceEntityId` TEXT NOT NULL,
                `sourceEntityType` TEXT NOT NULL,
                `targetEntityId` TEXT NOT NULL,
                `targetEntityType` TEXT NOT NULL,
                `relationType` TEXT NOT NULL,
                `relationStrength` REAL NOT NULL,
                `direction` TEXT NOT NULL,
                `attributes` TEXT,
                `conditions` TEXT,
                `isActive` INTEGER NOT NULL,
                `validFrom` INTEGER NOT NULL,
                `validUntil` INTEGER,
                `metadata` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`relationId`),
                FOREIGN KEY(`sourceEntityId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_source` ON `entity_relations`(`sourceEntityId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_target` ON `entity_relations`(`targetEntityId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_type` ON `entity_relations`(`relationType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_strength` ON `entity_relations`(`relationStrength`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_active` ON `entity_relations`(`isActive`)")
    }

    private fun createAdvancedCalculations(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `advanced_calculations` (
                `calculationId` TEXT NOT NULL,
                `parcelleId` TEXT,
                `calculationType` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `formula` TEXT NOT NULL,
                `variables` TEXT,
                `parameters` TEXT,
                `dependencies` TEXT,
                `result` REAL,
                `resultMetadata` TEXT,
                `status` TEXT NOT NULL,
                `priority` TEXT NOT NULL,
                `executionTime` INTEGER,
                `accuracy` REAL,
                `confidence` REAL,
                `error` TEXT,
                `optimizationHints` TEXT,
                `validUntil` INTEGER,
                `tags` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`calculationId`),
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_parcelle` ON `advanced_calculations`(`parcelleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_type` ON `advanced_calculations`(`calculationType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_status` ON `advanced_calculations`(`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_priority` ON `advanced_calculations`(`priority`)")
    }
}
