package com.forestry.counter.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration de la base de données de la version 15 à 27.
 * Ajoute les tables pour le système avancé de corrélation, interprétation et calcul.
 */
object Migration15to26 : Migration(15, 26) {
    
    override fun migrate(database: SupportSQLiteDatabase) {
        // Création de la table des corrélations de données
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `data_correlations` (
                `correlationId` TEXT NOT NULL PRIMARY KEY,
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
                FOREIGN KEY(`sourceParcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE,
                FOREIGN KEY(`targetParcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Index pour les corrélations
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_source` ON `data_correlations` (`sourceParcelleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_target` ON `data_correlations` (`targetParcelleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_type` ON `data_correlations` (`correlationType`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_strength` ON `data_correlations` (`correlationStrength`)")
        
        // Création de la table des interprétations de données
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `data_interpretations` (
                `interpretationId` TEXT NOT NULL PRIMARY KEY,
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
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Index pour les interprétations
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_parcelle` ON `data_interpretations` (`parcelleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_type` ON `data_interpretations` (`interpretationType`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_confidence` ON `data_interpretations` (`confidenceScore`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_interpretations_priority` ON `data_interpretations` (`priority`)")
        
        // Création de la table des relations entre entités
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `entity_relations` (
                `relationId` TEXT NOT NULL PRIMARY KEY,
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
                FOREIGN KEY(`sourceEntityId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Index pour les relations
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_source` ON `entity_relations` (`sourceEntityId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_target` ON `entity_relations` (`targetEntityId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_type` ON `entity_relations` (`relationType`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_strength` ON `entity_relations` (`relationStrength`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_relations_active` ON `entity_relations` (`isActive`)")
        
        // Création de la table des calculs avancés
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `advanced_calculations` (
                `calculationId` TEXT NOT NULL PRIMARY KEY,
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
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Index pour les calculs avancés
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_parcelle` ON `advanced_calculations` (`parcelleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_type` ON `advanced_calculations` (`calculationType`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_status` ON `advanced_calculations` (`status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_calculations_priority` ON `advanced_calculations` (`priority`)")
        
        // Ajout de triggers pour maintenir la cohérence des données
        createTriggers(database)
    }
    
    private fun createTriggers(database: SupportSQLiteDatabase) {
        // Trigger pour mettre à jour updatedAt lors des modifications
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS update_correlations_timestamp 
            AFTER UPDATE ON data_correlations
            BEGIN
                UPDATE data_correlations SET updatedAt = strftime('%s', 'now') WHERE correlationId = NEW.correlationId;
            END
        """.trimIndent())
        
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS update_interpretations_timestamp 
            AFTER UPDATE ON data_interpretations
            BEGIN
                UPDATE data_interpretations SET updatedAt = strftime('%s', 'now') WHERE interpretationId = NEW.interpretationId;
            END
        """.trimIndent())
        
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS update_relations_timestamp 
            AFTER UPDATE ON entity_relations
            BEGIN
                UPDATE entity_relations SET updatedAt = strftime('%s', 'now') WHERE relationId = NEW.relationId;
            END
        """.trimIndent())
        
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS update_calculations_timestamp 
            AFTER UPDATE ON advanced_calculations
            BEGIN
                UPDATE advanced_calculations SET updatedAt = strftime('%s', 'now') WHERE calculationId = NEW.calculationId;
            END
        """.trimIndent())
        
        // Trigger pour nettoyer les données expirées
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cleanup_expired_interpretations
            AFTER INSERT ON data_interpretations
            BEGIN
                DELETE FROM data_interpretations WHERE validUntil < strftime('%s', 'now') AND validUntil IS NOT NULL;
            END
        """.trimIndent())
        
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cleanup_expired_calculations
            AFTER INSERT ON advanced_calculations
            BEGIN
                DELETE FROM advanced_calculations WHERE validUntil < strftime('%s', 'now') AND validUntil IS NOT NULL;
            END
        """.trimIndent())
        
        database.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cleanup_expired_relations
            AFTER INSERT ON entity_relations
            BEGIN
                UPDATE entity_relations SET isActive = 0 WHERE validUntil < strftime('%s', 'now') AND validUntil IS NOT NULL;
            END
        """.trimIndent())
    }
}
